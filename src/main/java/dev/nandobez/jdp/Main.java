package dev.nandobez.jdp;

import dev.nandobez.jdp.cmd.*;
import picocli.CommandLine;
import picocli.CommandLine.Command;

import static dev.nandobez.jdp.cmd.Tui.*;

@Command(
    name = "jdp",
    mixinStandardHelpOptions = true,
    version = "jdp 0.1.0",
    description = "Java Dependency Pilot — Maven/Gradle dependency manager with CVE + role-conflict + verify chain.",
    subcommands = {
        ListCmd.class, SearchCmd.class, AddCmd.class, RmCmd.class,
        WhyCmd.class, DoctorCmd.class, WeightCmd.class, DiffCmd.class,
        MigrateCmd.class, InitCmd.class, UnusedCmd.class, ReplCmd.class
    }
)
public class Main implements Runnable {

    public void run() { printHelp(); }

    public static void main(String[] args) {
        if (args.length == 0
            || (args.length == 1 && SubHelp.isHelpFlag(args[0]))) {
            printHelp();
            System.exit(0);
        }
        for (int i = 1; i < args.length; i++) {
            if (SubHelp.isHelpFlag(args[i])) {
                if (SubHelp.print(args[0])) System.exit(0);
                break;
            }
        }
        System.out.println();
        int rc = new CommandLine(new Main()).execute(args);
        System.out.println();
        System.exit(rc);
    }

    private static void printHelp() {
        String[][] CRUD = {
            {"list",    "ls",     "Declared dependencies as a table."},
            {"search",  "",       "Search Maven Central (★ marks canonical groups)."},
            {"add",     "",       "Add a dependency. Resolves GAV via Central + verify chain."},
            {"rm",      "remove", "Remove by short name or artifactId."},
        };
        String[][] ANALYSIS = {
            {"doctor",  "",       "CVEs (OSV.dev) + outdated + incompat rules + score."},
            {"why",     "",       "Transitive chain: why is this dep on the graph?"},
            {"weight",  "",       "Top jars by size (fat-jar / cold-start impact)."},
            {"unused",  "",       "Deps without matching imports (heuristic)."},
        };
        String[][] PROJECT = {
            {"init",    "",       "Scaffold a Java project. Templates: rest-api | batch | lib."},
            {"diff",    "",       "Release-notes / migration links between two versions."},
            {"migrate", "",       "Convert manifest. Currently: maven → gradle (Kotlin DSL)."},
            {"repl",    "",       "Interactive shell with Central artifact tab-complete."},
        };

        System.out.println();
        System.out.println();
        System.out.println(BLD + "jdp " + R + DIM + "0.1.0" + R
            + " — Java Dependency Pilot");
        System.out.println(DIM + "  CRUD manager for Maven/Gradle deps with CVE + compat checks." + R);
        System.out.println();
        System.out.println("  " + DIM + "USAGE" + R);
        System.out.println();
        System.out.println("    jdp <command> [options]    " + DIM + "// single command" + R);
        System.out.println("    jdp repl                   " + DIM + "// interactive shell with autocomplete" + R);
        System.out.println();
        System.out.println();

        printGroup("CRUD",     CRUD);
        printGroup("ANALYSIS", ANALYSIS);
        printGroup("PROJECT",  PROJECT);

        System.out.println("  " + DIM + "GLOBAL FLAGS" + R);
        System.out.println();
        System.out.println("    -h, --help            full help for a command (jdp add --help, etc)");
        System.out.println("    -V, --version         show version");
        System.out.println("        --no-build        on add/rm: skip verify chain");
        System.out.println("        " + DIM + "JDP_SKIP_BUILD=1" + R + "  env var equivalent");
        System.out.println();
    }

    private static void printGroup(String title, String[][] rows) {
        System.out.println("  " + DIM + title + R);
        System.out.println();
        for (String[] r : rows) {
            String name = r[0];
            String alias = r[1].isEmpty() ? "" : DIM + ", " + r[1] + R;
            String left = "    " + BLD + name + R + alias;
            int visible = 4 + name.length() + (r[1].isEmpty() ? 0 : 2 + r[1].length());
            int padding = Math.max(2, 30 - visible);
            System.out.println(left + " ".repeat(padding) + r[2]);
        }
        System.out.println();
        System.out.println();
    }
}
