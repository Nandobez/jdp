package dev.nandobez.jdp.cmd;

import dev.nandobez.jdp.core.Coord;
import dev.nandobez.jdp.core.PomReader;
import picocli.CommandLine.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.stream.Stream;

import static dev.nandobez.jdp.cmd.Tui.*;

@Command(name = "unused", mixinStandardHelpOptions = true, description = "Find declared deps with no matching import in src/. Heuristic.")
public class UnusedCmd implements Callable<Integer> {

    @Option(names = {"-p", "--pom"}, defaultValue = "pom.xml")
    Path pom;

    @Option(names = {"-s", "--src"}, defaultValue = "src/main/java")
    Path src;

    @Option(names = "--clean", description = "Prompta para remover cada zumbi detectado.")
    boolean clean;

    @Option(names = {"-y", "--yes"}, description = "Em --clean: remove todos sem perguntar.")
    boolean autoYes;

    public Integer call() throws Exception {
        var p = PomReader.read(pom);
        if (!Files.exists(src)) {
            System.out.println(RED + "src not found: " + src + R);
            return 2;
        }
        // collect all imports from .java
        Set<String> imports = new HashSet<>();
        try (Stream<Path> files = Files.walk(src)) {
            for (Path j : (Iterable<Path>) files.filter(x -> x.toString().endsWith(".java"))::iterator) {
                for (String line : Files.readAllLines(j)) {
                    String t = line.trim();
                    if (t.startsWith("import ")) {
                        String pkg = t.substring(7).replace(";", "").trim();
                        imports.add(pkg);
                    }
                }
            }
        }

        List<Coord> unused = new ArrayList<>();
        for (Coord c : p.deps()) {
            if (isAutowiredOrAgent(c)) continue;
            String groupHint = c.groupId();
            String artifactHint = c.artifactId().replace("-", "");
            boolean matched = imports.stream().anyMatch(imp ->
                imp.startsWith(groupHint) || imp.toLowerCase().contains(artifactHint.toLowerCase()));
            if (!matched) unused.add(c);
        }

        if (unused.isEmpty()) {
            System.out.println(GRN + "no obvious zumbis." + R);
            return 0;
        }
        System.out.println(BLD + "Possivelmente sem uso (heurística — confirme antes):" + R);
        var rows = new ArrayList<String[]>();
        int wN = 2, wP = 6, wV = 6, wG = 5;
        for (int i = 0; i < unused.size(); i++) {
            Coord c = unused.get(i);
            String idx = String.valueOf(i + 1);
            String pkg = c.shortName(), ver = c.version() == null ? "(BOM)" : c.version(), grp = c.shortGroup();
            rows.add(new String[]{idx, pkg, ver, grp});
            wN = Math.max(wN, idx.length()); wP = Math.max(wP, pkg.length());
            wV = Math.max(wV, ver.length()); wG = Math.max(wG, grp.length());
        }
        table(new String[]{"#","pacote","versão","grupo"}, new int[]{wN, wP, wV, wG}, rows);

        if (clean) runClean(unused, pom);
        return 0;
    }

    /** Deps que funcionam sem import direto (autoconfig, SPI, annotation processors, runtime drivers, javaagents). */
    private static boolean isAutowiredOrAgent(Coord c) {
        String a = c.artifactId();
        String g = c.groupId();
        // Spring Boot / Micronaut / Quarkus starters → autoconfig
        if (a.contains("starter") || a.contains("starter-")) return true;
        if (a.startsWith("spring-boot-")) return true;
        if (g.startsWith("org.springframework.boot")) return true;
        // BOMs, parents
        if (a.endsWith("-bom") || a.endsWith("-parent") || a.endsWith("-dependencies")) return true;
        // JDBC drivers (Class.forName via DriverManager)
        if (g.equals("mysql") || g.startsWith("org.postgresql") || g.startsWith("com.h2database")
            || g.startsWith("org.mariadb") || g.startsWith("com.oracle") || g.startsWith("com.microsoft.sqlserver"))
            return true;
        // Annotation processors / agents / build-time
        if (g.equals("org.projectlombok")) return true;
        if (a.contains("mapstruct")) return true;
        // Logging implementations (used via SLF4J facade)
        if (g.equals("ch.qos.logback") || g.startsWith("org.apache.logging.log4j")) return true;
        if (a.startsWith("slf4j-")) return true;
        // Test scope — assume it's used by some test
        // (we don't currently parse <scope>, so skip the common test deps)
        if (a.startsWith("junit") || a.contains("junit-jupiter")
            || a.contains("mockito") || a.contains("assertj") || a.contains("testcontainers")) return true;
        return false;
    }

    private void runClean(List<Coord> unused, Path pom) throws Exception {
        System.out.println();
        boolean canPrompt = System.console() != null && !autoYes;
        int removed = 0;
        for (Coord c : unused) {
            if (autoYes) {
                dev.nandobez.jdp.core.PomWriter.remove(pom, c.groupId(), c.artifactId());
                System.out.println("  " + YLW + "- " + R + Tui.coloredGav(c.groupId(), c.artifactId(), c.version()));
                removed++;
                continue;
            }
            if (!canPrompt) {
                System.out.println(DIM + "  (stdin não é TTY — use -y pra confirmar tudo)" + R);
                break;
            }
            System.out.print("  remover " + Tui.coloredGa(c.groupId(), c.artifactId())
                + DIM + " ? [s/N/q]: " + R);
            String ans = System.console().readLine();
            ans = ans == null ? "" : ans.trim().toLowerCase();
            if (ans.equals("q")) break;
            if (ans.equals("s") || ans.equals("y")) {
                dev.nandobez.jdp.core.PomWriter.remove(pom, c.groupId(), c.artifactId());
                System.out.println("    " + YLW + "- " + R + Tui.coloredGav(c.groupId(), c.artifactId(), c.version()));
                removed++;
            }
        }
        System.out.println();
        System.out.println("  " + GRN + removed + R + DIM + " removidos. Rode " + R + BLD + "jdp doctor" + R + DIM + " pra validar." + R);
    }
}
