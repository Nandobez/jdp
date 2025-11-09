package dev.nandobez.jdp.cmd;

import dev.nandobez.jdp.core.*;
import dev.nandobez.jdp.sources.Central;
import java.util.List;
import picocli.CommandLine.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.Callable;

import static dev.nandobez.jdp.cmd.Tui.*;

@Command(name = "add", mixinStandardHelpOptions = true, description = "Add a dependency. Resolves GAV via Maven Central if you pass a short name.")
public class AddCmd implements Callable<Integer> {

    @Parameters(arity = "1", description = "Either a GAV (g:a:v), or just an artifactId, or a short name like 'starter-web'.")
    String spec;

    @Option(names = {"-p", "--pom"}, defaultValue = "pom.xml")
    Path pom;

    @Option(names = "--version", description = "Override version.")
    String versionOverride;

    @Option(names = "--bom", description = "Omit <version> (let parent BOM dictate).")
    boolean bom;

    @Option(names = "--no-build", description = "Skip post-mutation build verification.")
    boolean noBuild;

    @Option(names = {"-y", "--yes"}, description = "Don't prompt; auto-pick best canonical match.")
    boolean autoYes;

    static void printErrorBlock(String reason, String pomPath) {
        if (reason == null) reason = "build falhou";
        System.out.println();
        System.out.println(RED + BLD + "  ✗ ERRO" + R + DIM + "  build não passou" + R);
        System.out.println();
        int width = Math.max(40, Tui.termWidth() - 6);
        for (String paragraph : reason.split("\\n")) {
            for (String line : wrap(paragraph, width)) {
                System.out.println("    " + line);
            }
            System.out.println();
        }
        System.out.println(YLW + "  ↶ revertido " + R + DIM + pomPath + R);
        System.out.println(DIM + "    use " + R + BLD + "--no-build" + R + DIM + " para forçar" + R);
        System.out.println();
    }

    private static java.util.List<String> wrap(String text, int width) {
        var out = new java.util.ArrayList<String>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            if (line.length() + word.length() + 1 > width) {
                out.add(line.toString());
                line.setLength(0);
            }
            if (line.length() > 0) line.append(' ');
            line.append(word);
        }
        if (line.length() > 0) out.add(line.toString());
        return out;
    }

    private static boolean canPrompt() { return System.console() != null; }

    private static boolean isCanon(String g) {
        return CANON.stream().anyMatch(g::startsWith);
    }

    private static Central.Hit promptPick(List<Central.Hit> hits) {
        int max = Math.min(hits.size(), 10);
        System.out.println();
        System.out.println(BLD + "múltiplos matches — escolha um:" + R);
        for (int i = 0; i < max; i++) {
            var h = hits.get(i);
            String mark = isCanon(h.groupId()) ? YLW + "★" + R : " ";
            System.out.printf("  %s [%s%d%s] %s%n",
                mark,
                BLD, i + 1, R,
                Tui.coloredGav(h.groupId(), h.artifactId(), h.latestVersion()));
        }
        System.out.println();
        System.out.print(DIM + "número (Enter = 1, q = cancelar): " + R);
        try {
            String line = System.console().readLine();
            if (line == null) return null;
            line = line.trim();
            if (line.equalsIgnoreCase("q")) return null;
            int idx = line.isEmpty() ? 1 : Integer.parseInt(line);
            if (idx < 1 || idx > max) return null;
            return hits.get(idx - 1);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static final List<String> CANON = List.of(
        "org.springframework", "org.apache", "com.fasterxml", "io.jsonwebtoken",
        "org.projectlombok", "com.google", "io.micronaut", "io.quarkus", "io.quarkiverse",
        "org.junit", "org.mockito", "io.netty", "io.grpc", "org.postgresql",
        "mysql", "redis.clients", "org.hibernate", "jakarta", "javax",
        "org.slf4j", "ch.qos.logback", "com.zaxxer", "io.projectreactor"
    );

    public Integer call() throws Exception {
        Coord c;
        if (spec.contains(":")) {
            c = Coord.parse(spec);
        } else {
            var hits = Central.search(spec, 20);
            if (hits.isEmpty()) {
                System.out.println(RED + "nothing on Maven Central matched '" + spec + "'." + R);
                return 2;
            }
            // sort canonical first
            hits = new java.util.ArrayList<>(hits);
            hits.sort((a, b) -> Boolean.compare(isCanon(b.groupId()), isCanon(a.groupId())));
            final String want = spec;
            // exact-match shortcut → auto-pick top canonical exact, never prompt
            var exactCanon = hits.stream()
                .filter(h -> h.artifactId().equals(want) && isCanon(h.groupId()))
                .findFirst();
            Central.Hit pick;
            if (exactCanon.isPresent()) {
                pick = exactCanon.get();
            } else if (autoYes || hits.size() == 1 || !canPrompt()) {
                pick = hits.get(0);
            } else {
                pick = promptPick(hits);
                if (pick == null) { System.out.println(DIM + "cancelado." + R); return 1; }
            }
            c = pick.toCoord();
        }
        if (versionOverride != null) c = new Coord(c.groupId(), c.artifactId(), versionOverride);
        if (bom) c = new Coord(c.groupId(), c.artifactId(), null);

        // Equivalence check — flag duplicate role.
        var existing = PomReader.read(pom).deps();
        var conflict = Equivalence.find(c, existing);
        if (conflict != null) {
            String[] eg = conflict.existingGa().split(":");
            String coloredExisting = Tui.coloredGa(eg[0], eg[1]);
            String coloredNew      = Tui.coloredGa(c.groupId(), c.artifactId());

            System.out.println();
            System.out.println("  " + YLW + BLD + "⚠ conflito de função" + R);
            System.out.println();
            System.out.println("    " + coloredExisting + DIM + " já está no pom e cumpre o mesmo papel:" + R);
            System.out.println("    " + DIM + conflict.category().description() + R);
            System.out.println();
            if (autoYes) {
                System.out.println("    " + DIM + "--yes: mantendo os dois." + R);
            } else if (!canPrompt()) {
                System.out.println("    " + DIM + "(stdin não é TTY — mantendo os dois)" + R);
            } else {
                System.out.print("    substituir " + coloredExisting + DIM + " por " + R + coloredNew
                    + "  " + BLD + "[S/n/m]" + R + DIM + " (m = manter os dois): " + R);
                String ans = System.console().readLine();
                ans = ans == null ? "" : ans.trim().toLowerCase();
                if (ans.equals("n")) {
                    System.out.println("    " + DIM + "cancelado." + R);
                    return 1;
                }
                if (ans.isEmpty() || ans.equals("s") || ans.equals("y")) {
                    PomWriter.remove(pom, eg[0], eg[1]);
                    System.out.println();
                    System.out.println("    " + YLW + "- " + R + coloredExisting);
                }
            }
        }

        // Snapshot pom for rollback on verify failure.
        byte[] backup = java.nio.file.Files.readAllBytes(pom);
        PomWriter.add(pom, c);
        System.out.println();
        System.out.println("  " + GRN + "+ " + R + Tui.coloredGav(c.groupId(), c.artifactId(), c.version()));
        if (noBuild) return 0;
        int rc = Verify.run(pom.toAbsolutePath().getParent());
        if (rc != 0) {
            java.nio.file.Files.write(pom, backup);
            printErrorBlock(Verify.lastFailureReason, pom.toString());
        }
        return rc;
    }
}
