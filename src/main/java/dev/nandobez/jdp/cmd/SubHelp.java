package dev.nandobez.jdp.cmd;

import java.util.LinkedHashMap;
import java.util.Map;

import static dev.nandobez.jdp.cmd.Tui.*;

/** Per-subcommand custom help renderer. Loaded by Main when --help/-h hits a subcmd. */
public class SubHelp {

    /** Returns true if printed (recognised subcommand), false otherwise. */
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
        section("USO", h.uso);
        if (h.args != null) section("ARGUMENTOS", h.args);
        section("OPÇÕES", h.opts);
        if (h.examples != null) section("EXEMPLOS", h.examples);
    }

    private static void section(String title, String[][] rows) {
        System.out.println("  " + DIM + title + R);
        System.out.println();
        if (rows.length == 1 && rows[0].length == 1) {
            // Single free-form block (USO or EXEMPLOS)
            for (String line : rows[0][0].split("\n")) System.out.println("    " + line);
            System.out.println();
            return;
        }
        // 2-col aligned: left=flag, right=description (with optional 3rd col default)
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
            "Mostra dependências declaradas como tabela",
            "jdp list [opções]",
            null,
            new String[][]{
                {"-p, --pom <path>", "caminho do pom.xml", "./pom.xml"},
                {"    --full",       "mostra GA completo (sem encurtar nomes)"},
                {"-h, --help",       "esta ajuda"},
            },
            "jdp list\njdp ls --full\njdp list -p ../backend/pom.xml"));

        HELPS.put("search", h("search",
            "Busca no Maven Central com fuzzy match",
            "jdp search <query> [opções]",
            new String[][]{ {"<query>", "termo, ex 'jjwt' ou 'starter-data'"} },
            new String[][]{
                {"-n, --limit <N>", "número máximo de resultados", "15"},
                {"-f, --full",      "mostra artifactId + groupId completos"},
                {"-h, --help",      "esta ajuda"},
            },
            "jdp search jjwt\njdp search starter-data -n 20\njdp search hibernate --full"));

        HELPS.put("add", h("add",
            "Adiciona dependência + roda verify chain",
            "jdp add <spec> [opções]",
            new String[][]{ {"<spec>", "GAV (g:a:v), artifactId, ou short name (ex starter-web)"} },
            new String[][]{
                {"-p, --pom <path>",  "caminho do pom.xml", "./pom.xml"},
                {"    --version <v>", "força versão específica"},
                {"    --bom",         "omite <version> (deixa parent BOM ditar)"},
                {"-y, --yes",         "pula picker e conflito, pega top canonical"},
                {"    --no-build",    "pula a verify chain (resolve+package+install)"},
                {"-h, --help",        "esta ajuda"},
            },
            "jdp add starter-data-jpa\njdp add org.postgresql:postgresql:42.7.4\njdp add jwt -y\njdp add lombok --bom"));

        HELPS.put("rm", h("rm",
            "Remove dependência por short name ou artifactId",
            "jdp rm <name> [opções]",
            new String[][]{ {"<name>", "short name ('starter-web') ou artifactId completo"} },
            new String[][]{
                {"-p, --pom <path>", "caminho do pom.xml", "./pom.xml"},
                {"    --no-build",   "pula verify chain"},
                {"-h, --help",       "esta ajuda"},
            },
            "jdp rm starter-actuator\njdp rm log4j-core --no-build"));

        HELPS.put("doctor", h("doctor",
            "Health check: CVEs (OSV.dev) + outdated + regras de incompat + score",
            "jdp doctor [opções]",
            null,
            new String[][]{
                {"-p, --pom <path>", "caminho do pom.xml", "./pom.xml"},
                {"    --fix",        "sobe versões com CVE para a versão patched"},
                {"-y, --yes",        "em --fix: aplica sem perguntar"},
                {"-h, --help",       "esta ajuda"},
            },
            "jdp doctor\njdp doctor --fix\njdp doctor --fix -y"));

        HELPS.put("why", h("why",
            "Por que esta dependência está no grafo (cadeia transitiva)",
            "jdp why <name> [opções]",
            new String[][]{ {"<name>", "artifactId ou parte do nome"} },
            new String[][]{
                {"-d, --dir <path>", "diretório do projeto", "."},
                {"-h, --help",       "esta ajuda"},
            },
            "jdp why tomcat\njdp why jackson\njdp why hibernate"));

        HELPS.put("weight", h("weight",
            "Top jars resolvidos por tamanho (impacto no fat-jar / cold start)",
            "jdp weight [opções]",
            null,
            new String[][]{
                {"-d, --dir <path>", "diretório do projeto", "."},
                {"-n, --top <N>",    "quantos jars mostrar", "15"},
                {"-h, --help",       "esta ajuda"},
            },
            "jdp weight\njdp weight -n 30"));

        HELPS.put("unused", h("unused",
            "Deps declaradas sem import correspondente (heurística)",
            "jdp unused [opções]",
            null,
            new String[][]{
                {"-p, --pom <path>",  "caminho do pom.xml", "./pom.xml"},
                {"-s, --src <path>",  "diretório com .java", "src/main/java"},
                {"    --clean",       "prompta pra remover cada zumbi"},
                {"-y, --yes",         "em --clean: remove todos sem perguntar"},
                {"-h, --help",        "esta ajuda"},
            },
            "jdp unused\njdp unused --clean\njdp unused --clean -y"));

        HELPS.put("diff", h("diff",
            "Links de release notes entre duas versões de um artefato",
            "jdp diff <artifact> <A..B>",
            new String[][]{
                {"<artifact>", "artifactId (ou g:a)"},
                {"<A..B>",     "intervalo de versões, ex 3.3.4..3.4.0"},
            },
            new String[][]{ {"-h, --help", "esta ajuda"} },
            "jdp diff starter-data-jpa 3.3.4..3.4.0\njdp diff jjwt-api 0.11.5..0.12.6"));

        HELPS.put("migrate", h("migrate",
            "Converte manifesto entre formatos",
            "jdp migrate <direction> [opções]",
            new String[][]{ {"<direction>", "atual: maven->gradle"} },
            new String[][]{
                {"-p, --pom <path>",  "caminho do pom.xml", "./pom.xml"},
                {"-o, --out <path>",  "arquivo de saída", "build.gradle.kts"},
                {"-h, --help",        "esta ajuda"},
            },
            "jdp migrate maven->gradle\njdp migrate maven-gradle -o my.gradle.kts"));

        HELPS.put("init", h("init",
            "Scaffold de projeto Java novo",
            "jdp init <dir> [opções]",
            new String[][]{ {"<dir>", "diretório do projeto (criado se não existir)"} },
            new String[][]{
                {"-t, --template <t>",  "rest-api | batch | lib", "rest-api"},
                {"    --group <g>",     "groupId", "com.example"},
                {"    --artifact <a>",  "artifactId", "<dir>"},
                {"    --java <v>",      "versão do JDK", "17"},
                {"-h, --help",          "esta ajuda"},
            },
            "jdp init meu-api\njdp init worker -t batch --group io.foo\njdp init lib1 -t lib --java 21"));

        HELPS.put("repl", h("repl",
            "Shell interativo com tab-complete dos artefatos do Central",
            "jdp repl",
            null,
            new String[][]{ {"-h, --help", "esta ajuda"} },
            "jdp repl                          # entra no shell\n# dentro: list / add start<TAB> / search jwt<TAB> / doctor / exit"));
    }
}
