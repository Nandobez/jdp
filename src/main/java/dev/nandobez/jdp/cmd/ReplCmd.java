package dev.nandobez.jdp.cmd;

import dev.nandobez.jdp.Main;
import dev.nandobez.jdp.core.PomReader;
import dev.nandobez.jdp.sources.Central;
import org.jline.reader.*;
import org.jline.reader.impl.completer.*;
import org.jline.terminal.*;
import org.jline.utils.InfoCmp;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.shell.jline3.PicocliJLineCompleter;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.Callable;

@Command(name = "repl", mixinStandardHelpOptions = true, description = "Interactive shell with tab-completion for commands AND artifact names.")
public class ReplCmd implements Callable<Integer> {

    public Integer call() throws Exception {
        CommandLine cli = new CommandLine(new Main());
        // Make the shell own a fresh PicocliCommands wrapping the existing tree.
        try (Terminal terminal = TerminalBuilder.builder().system(true).build()) {
            // Custom completer: picocli commands + dynamic artifact suggestions on `add`/`search`/`diff`.
            Completer artifactCompleter = new ArtifactCompleter();
            Completer base = new PicocliJLineCompleter(cli.getCommandSpec());

            Completer combined = (reader, line, candidates) -> {
                base.complete(reader, line, candidates);
                List<String> words = line.words();
                if (words.size() >= 2) {
                    String cmd = words.get(0);
                    if (("add".equals(cmd) || "search".equals(cmd) || "diff".equals(cmd))
                        && !line.word().isBlank()) {
                        artifactCompleter.complete(reader, line, candidates);
                    }
                }
            };

            LineReader reader = LineReaderBuilder.builder()
                .terminal(terminal)
                .completer(combined)
                .parser(new org.jline.reader.impl.DefaultParser())
                .variable(LineReader.HISTORY_FILE, Paths.get(System.getProperty("user.home"), ".jdp_history"))
                .build();

            String prompt = "\u001B[33mjdp›[0m ";
            terminal.writer().println("\u001B[2mjdp REPL — tab to complete, 'exit' to quit.\u001B[0m");
            showStatus(terminal);

            while (true) {
                String line;
                try { line = reader.readLine(prompt); }
                catch (UserInterruptException e) { continue; }
                catch (EndOfFileException e)    { break; }
                if (line == null) break;
                line = line.trim();
                if (line.isEmpty()) continue;
                if (line.equals("exit") || line.equals("quit")) break;
                if (line.equals("clear")) { terminal.puts(InfoCmp.Capability.clear_screen); terminal.flush(); continue; }

                String[] args = line.split("\\s+");
                try {
                    new CommandLine(new Main()).execute(args);
                } catch (Throwable t) {
                    terminal.writer().println("\u001B[31m" + t.getMessage() + "\u001B[0m");
                }
            }
        }
        return 0;
    }

    private void showStatus(Terminal t) {
        try {
            var pom = Paths.get("pom.xml");
            if (!Files.exists(pom)) {
                t.writer().println("\u001B[2m(no pom.xml in current dir — use `init` or cd to a project)\u001B[0m");
                return;
            }
            var p = PomReader.read(pom);
            t.writer().println("\u001B[2m" + pom.toAbsolutePath() + " · " + p.deps().size() + " deps\u001B[0m");
        } catch (Exception ignored) {}
    }

    /** Hits Maven Central for completions on the current word, debounced via length>=3. */
    static class ArtifactCompleter implements Completer {
        @Override
        public void complete(LineReader reader, ParsedLine line, List<Candidate> candidates) {
            String word = line.word();
            if (word == null || word.length() < 3) return;
            try {
                var hits = Central.search(word, 12);
                for (var h : hits) {
                    candidates.add(new Candidate(h.artifactId(), h.artifactId(),
                        null, h.groupId() + " · " + h.latestVersion(), null, null, true));
                }
            } catch (Exception ignored) {}
        }
    }
}
