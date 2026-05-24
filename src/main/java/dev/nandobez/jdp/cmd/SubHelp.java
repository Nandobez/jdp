package dev.nandobez.jdp.cmd;

import java.util.LinkedHashMap;
import java.util.Map;

import static dev.nandobez.jdp.cmd.Tui.*;

/** Per-subcommand custom help renderer. Loaded by Main when --help/-h hits a subcmd. */
public class SubHelp {

    public static boolean print(String cmd) {
        var entry = HELPS.get(cmd);
        if (entry == null) return false;
        render(entry);
        return true;
    }

    public static boolean isHelpFlag(String arg) {
        return arg.equals("-h") || arg.equals("--help") || arg.equals("help");
    }

    private static void render(Help h) {
        System.out.println();
        System.out.println("  " + BLD + "jdp " + h.name + R + DIM + "  — " + h.desc + R);
        System.out.println();
        section("USAGE", h.uso);
        if (h.args != null) section("ARGUMENTS", h.args);
        section("OPTIONS", h.opts);
        if (h.examples != null) section("EXAMPLES", h.examples);
    }

    private static void section(String title, String[][] rows) {
        System.out.println("  " + DIM + title + R);
        System.out.println();
        if (rows.length == 1 && rows[0].length == 1) {
            for (String line : rows[0][0].split("\n")) System.out.println("    " + line);
            System.out.println();
            return;
        }
        int maxLeft = 0;
        for (String[] r : rows) maxLeft = Math.max(maxLeft, visibleLen(r[0]));
        for (String[] r : rows) {
            String left = pad(r[0], maxLeft);
            String right = r.length > 1 ? r[1] : "";
            String def = r.length > 2 ? "  " + DIM + "(default: " + r[2] + ")" + R : "";
            System.out.println("    " + BLD + left + R + "  " + right + def);
        }
        System.out.println();
    }

    private record Help(String name, String desc, String[][] uso, String[][] args, String[][] opts, String[][] examples) {}

    private static Help h(String name, String desc, String uso, String[][] args, String[][] opts, String examples) {
        String[][] examplesArr = examples == null ? null : new String[][]{ { examples } };
        return new Help(name, desc, new String[][]{ { uso } }, args, opts, examplesArr);
    }

    private static final Map<String, Help> HELPS = new LinkedHashMap<>();
    static {
        HELPS.put("list", h("list",
            "Show declared dependencies as a table",
            "jdp list [options]",
            null,
            new String[][]{
                {"-p, --pom <path>", "path to pom.xml", "./pom.xml"},
                {"    --full",       "show full GA (skip the short-name heuristic)"},
                {"-h, --help",       "this help"},
            },
            "jdp list\njdp ls --full\njdp list -p ../backend/pom.xml"));

        HELPS.put("search", h("search",
            "Search Maven Central with fuzzy matching",
            "jdp search <query> [options]",
            new String[][]{ {"<query>", "search term, e.g. 'jjwt' or 'starter-data'"} },
            new String[][]{
                {"-n, --limit <N>", "maximum number of results", "15"},
                {"-f, --full",      "show full artifactId + groupId"},
                {"-h, --help",      "this help"},
            },
            "jdp search jjwt\njdp search starter-data -n 20\njdp search hibernate --full"));

        HELPS.put("add", h("add",
            "Add a dependency and run the verify chain",
            "jdp add <spec> [options]",
            new String[][]{ {"<spec>", "GAV (g:a:v), artifactId, or short name (e.g. starter-web)"} },
            new String[][]{
                {"-p, --pom <path>",  "path to pom.xml", "./pom.xml"},
                {"    --version <v>", "force a specific version"},
                {"    --bom",         "omit <version> (let parent BOM decide)"},
                {"-y, --yes",         "skip the picker and conflict prompt; pick top canonical"},
                {"    --no-build",    "skip the verify chain (resolve+package+install)"},
                {"-h, --help",        "this help"},
            },
            "jdp add starter-data-jpa\njdp add org.postgresql:postgresql:42.7.4\njdp add jwt -y\njdp add lombok --bom"));

        HELPS.put("rm", h("rm",
            "Remove a dependency by short name or artifactId",
            "jdp rm <name> [options]",
            new String[][]{ {"<name>", "short name ('starter-web') or full artifactId"} },
            new String[][]{
                {"-p, --pom <path>", "path to pom.xml", "./pom.xml"},
                {"    --no-build",   "skip the verify chain"},
                {"-h, --help",       "this help"},
            },
            "jdp rm starter-actuator\njdp rm log4j-core --no-build"));

        HELPS.put("doctor", h("doctor",
            "Health check: CVEs (OSV.dev) + outdated + incompat rules + score",
            "jdp doctor [options]",
            null,
            new String[][]{
                {"-p, --pom <path>", "path to pom.xml", "./pom.xml"},
                {"    --fix",        "bump CVE-affected versions to the patched one"},
                {"-y, --yes",        "on --fix: apply without prompting"},
                {"-h, --help",       "this help"},
            },
            "jdp doctor\njdp doctor --fix\njdp doctor --fix -y"));

        HELPS.put("why", h("why",
            "Why is this dependency on the graph? (transitive chain)",
            "jdp why <name> [options]",
            new String[][]{ {"<name>", "artifactId or part of the name"} },
            new String[][]{
                {"-d, --dir <path>", "project directory", "."},
                {"-h, --help",       "this help"},
            },
            "jdp why tomcat\njdp why jackson\njdp why hibernate"));

        HELPS.put("weight", h("weight",
            "Top resolved jars by size (fat-jar / cold-start impact)",
            "jdp weight [options]",
            null,
            new String[][]{
                {"-d, --dir <path>", "project directory", "."},
                {"-n, --top <N>",    "how many jars to show", "15"},
                {"-h, --help",       "this help"},
            },
            "jdp weight\njdp weight -n 30"));

        HELPS.put("unused", h("unused",
            "Declared deps without matching imports (heuristic)",
            "jdp unused [options]",
            null,
            new String[][]{
                {"-p, --pom <path>",  "path to pom.xml", "./pom.xml"},
                {"-s, --src <path>",  ".java source dir", "src/main/java"},
                {"    --clean",       "prompt to remove each zombie"},
                {"-y, --yes",         "on --clean: remove all without prompting"},
                {"-h, --help",        "this help"},
            },
            "jdp unused\njdp unused --clean\njdp unused --clean -y"));

        HELPS.put("diff", h("diff",
            "Release-notes links between two versions of an artifact",
            "jdp diff <artifact> <A..B>",
            new String[][]{
                {"<artifact>", "artifactId (or g:a)"},
                {"<A..B>",     "version range, e.g. 3.3.4..3.4.0"},
            },
            new String[][]{ {"-h, --help", "this help"} },
            "jdp diff starter-data-jpa 3.3.4..3.4.0\njdp diff jjwt-api 0.11.5..0.12.6"));

        HELPS.put("migrate", h("migrate",
            "Convert manifest between formats",
            "jdp migrate <direction> [options]",
            new String[][]{ {"<direction>", "currently: maven->gradle"} },
            new String[][]{
                {"-p, --pom <path>",  "path to pom.xml", "./pom.xml"},
                {"-o, --out <path>",  "output file", "build.gradle.kts"},
                {"-h, --help",        "this help"},
            },
            "jdp migrate maven->gradle\njdp migrate maven-gradle -o my.gradle.kts"));

        HELPS.put("init", h("init",
            "Scaffold a new Java project",
            "jdp init <dir> [options]",
            new String[][]{ {"<dir>", "project directory (created if missing)"} },
            new String[][]{
                {"-t, --template <t>",  "rest-api | batch | lib", "rest-api"},
                {"    --group <g>",     "groupId", "com.example"},
                {"    --artifact <a>",  "artifactId", "<dir>"},
                {"    --java <v>",      "JDK release", "17"},
                {"-h, --help",          "this help"},
            },
            "jdp init my-api\njdp init worker -t batch --group io.foo\njdp init lib1 -t lib --java 21"));

        HELPS.put("repl", h("repl",
            "Interactive shell with tab-complete on Central artifacts",
            "jdp repl",
            null,
            new String[][]{ {"-h, --help", "this help"} },
            "jdp repl                          # enter the shell\n# inside: list / add start<TAB> / search jwt<TAB> / doctor / exit"));
    }
}
