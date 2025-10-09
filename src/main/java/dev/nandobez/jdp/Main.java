package dev.nandobez.jdp;

import dev.nandobez.jdp.cmd.*;
import picocli.CommandLine;
import picocli.CommandLine.Command;

import static dev.nandobez.jdp.cmd.Tui.*;

@Command(
    name = "jdp",
    mixinStandardHelpOptions = true,
    version = "jdp 0.1.0",
    description = "Java Dependency Pilot — CRUD manager for Maven/Gradle deps with CVE + compat checks.",
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
        // Intercept "jdp <cmd> --help" / "jdp <cmd> -h" before picocli validates required params.
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
            {"list",    "ls",     "Tabela de dependências declaradas."},
            {"search",  "",       "Busca no Maven Central (★ marca grupo canônico)."},
            {"add",     "",       "Adiciona dependência. Resolve GAV via Central + roda verify chain."},
            {"rm",      "remove", "Remove por nome curto ou artifactId."},
        };
        String[][] ANALYSIS = {
            {"doctor",  "",       "CVEs (OSV.dev) + outdated + regras de incompat + score."},
            {"why",     "",       "Cadeia transitiva: por que essa dep está no grafo."},
            {"weight",  "",       "Top jars por tamanho (impacto no fat-jar / cold start)."},
            {"unused",  "",       "Deps sem import nos .java (heurística)."},
        };
        String[][] PROJECT = {
            {"init",    "",       "Scaffold projeto Java. Templates: rest-api | batch | lib."},
            {"diff",    "",       "Release notes / migration links entre duas versões."},
            {"migrate", "",       "Converte manifesto. Atual: maven → gradle (Kotlin DSL)."},
            {"repl",    "",       "Shell interativo com tab-complete dos artifacts do Central."},
        };

        System.out.println();
        System.out.println();
        System.out.println(BLD + "jdp " + R + DIM + "0.1.0" + R
            + " — Java Dependency Pilot");
        System.out.println(DIM + "  CRUD manager pra deps Maven/Gradle com CVE + compat checks." + R);
        System.out.println();
        System.out.println("  " + DIM + "USAGE" + R);
        System.out.println();
        System.out.println("    jdp <comando> [opções]    " + DIM + "// comando individual" + R);
        System.out.println("    jdp repl                  " + DIM + "// modo interativo com autocomplete" + R);
        System.out.println();
        System.out.println();

        printGroup("CRUD",            CRUD);
        printGroup("ANÁLISE",         ANALYSIS);
        printGroup("PROJETO",         PROJECT);

        System.out.println("  " + DIM + "FLAGS GLOBAIS" + R);
        System.out.println();
        System.out.println("    -h, --help            ajuda completa do comando (jdp add --help, etc)");
        System.out.println("    -V, --version         versão");
        System.out.println("        --no-build        em add/rm: pula verify chain");
        System.out.println("        " + DIM + "JDP_SKIP_BUILD=1" + R + "  env var equivalente");
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
