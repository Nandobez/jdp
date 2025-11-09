package dev.nandobez.jdp.cmd;

import dev.nandobez.jdp.core.PomReader;
import dev.nandobez.jdp.core.PomWriter;
import picocli.CommandLine.*;
import java.nio.file.Path;
import java.util.concurrent.Callable;

import static dev.nandobez.jdp.cmd.Tui.*;

@Command(name = "rm", mixinStandardHelpOptions = true, aliases = {"remove"}, description = "Remove a dependency by short name or artifactId.")
public class RmCmd implements Callable<Integer> {

    @Parameters(arity = "1", description = "Short name (e.g. 'starter-web') or full artifactId.")
    String name;

    @Option(names = {"-p", "--pom"}, defaultValue = "pom.xml")
    Path pom;

    @Option(names = "--no-build", description = "Skip post-mutation build verification.")
    boolean noBuild;

    public Integer call() throws Exception {
        var p = PomReader.read(pom);
        var match = p.deps().stream()
            .filter(c -> c.artifactId().equals(name) || c.shortName().equals(name))
            .findFirst().orElse(null);
        if (match == null) {
            System.out.println();
            System.out.println("  " + RED + BLD + "✗ não encontrado" + R + DIM + "  '" + name + "' não está no pom" + R);
            System.out.println();
            // fuzzy suggestions
            var suggestions = p.deps().stream()
                .filter(c -> c.artifactId().toLowerCase().contains(name.toLowerCase())
                          || c.shortName().toLowerCase().contains(name.toLowerCase()))
                .limit(5)
                .toList();
            if (!suggestions.isEmpty()) {
                System.out.println("  " + DIM + "talvez você quis dizer:" + R);
                for (var c : suggestions)
                    System.out.println("    " + Tui.coloredGa(c.groupId(), c.artifactId()));
            } else if (!p.deps().isEmpty()) {
                System.out.println("  " + DIM + "deps atuais:" + R);
                for (var c : p.deps().stream().limit(5).toList())
                    System.out.println("    " + Tui.coloredGa(c.groupId(), c.artifactId()));
                System.out.println("    " + DIM + "(jdp list pra ver todas)" + R);
            }
            return 2;
        }
        byte[] backup = java.nio.file.Files.readAllBytes(pom);
        if (PomWriter.remove(pom, match.groupId(), match.artifactId())) {
            System.out.println();
            System.out.println("  " + YLW + "- " + R + Tui.coloredGav(match.groupId(), match.artifactId(), match.version()));
            if (noBuild) return 0;
            int rc = dev.nandobez.jdp.core.Verify.run(pom.toAbsolutePath().getParent());
            if (rc != 0) {
                java.nio.file.Files.write(pom, backup);
                AddCmd.printErrorBlock(dev.nandobez.jdp.core.Verify.lastFailureReason, pom.toString());
            }
            return rc;
        }
        return 2;
    }
}
