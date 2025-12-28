package dev.nandobez.jdp.cmd;

import picocli.CommandLine.*;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.regex.*;

import static dev.nandobez.jdp.cmd.Tui.*;

@Command(name = "why", mixinStandardHelpOptions = true, description = "Why is this dependency on the graph? Wraps `mvn dependency:tree`.")
public class WhyCmd implements Callable<Integer> {

    @Parameters(arity = "1", description = "artifactId or short name to trace.")
    String name;

    @Option(names = {"-d", "--dir"}, description = "Project dir.", defaultValue = ".")
    Path dir;

    // matches: " +- g:a:packaging:version:scope" or " \- ..." possibly nested
    private static final Pattern NODE = Pattern.compile(
        "^([\\s|+\\\\-]*)([^\\s].*?)$");

    public Integer call() throws Exception {
        var pb = new ProcessBuilder("mvn", "-B", "dependency:tree",
            "-Dincludes=*:*" + name + "*");
        pb.directory(dir.toFile());
        pb.redirectErrorStream(true);
        var p = pb.start();
        var collected = new ArrayList<String>();
        try (var br = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
            String line;
            while ((line = br.readLine()) != null) collected.add(line);
        }
        p.waitFor();

        List<String[]> nodes = new ArrayList<>();   // [indentChars, "g:a:type:v:scope"]
        for (String raw : collected) {
            String l = raw.replaceAll("\\u001B\\[[0-9;]*m", "");
            if (!l.startsWith("[INFO] ")) continue;
            String body = l.substring(7);
            if (body.isBlank()) continue;
            if (body.startsWith("---") || body.startsWith("Scanning") || body.startsWith("Building") || body.contains("BUILD") || body.startsWith("Finished")) continue;
            // root project line like "com.acme:orders-api:jar:0.1.0"
            if (body.matches("^[a-zA-Z][\\w.-]+:[\\w.-]+:[\\w.-]+:.*")) {
                nodes.add(new String[]{"", body});
                continue;
            }
            var m = NODE.matcher(body);
            if (!m.matches()) continue;
            String indent = m.group(1);
            String coord = m.group(2);
            if (!coord.contains(":")) continue;
            nodes.add(new String[]{indent, coord});
        }

        if (nodes.isEmpty()) {
            System.out.println(RED + "no path on the resolved tree contains '" + name + "'." + R);
            return 0;
        }

        // Compute depth from indent length (mvn uses ~3 chars per level).
        int[] depths = new int[nodes.size()];
        for (int i = 0; i < nodes.size(); i++) depths[i] = nodes.get(i)[0].length() / 3;

        // Determine which entries are LAST at their depth (for └── vs ├──)
        boolean[] isLast = new boolean[nodes.size()];
        for (int i = 0; i < nodes.size(); i++) {
            int d = depths[i];
            boolean last = true;
            for (int j = i + 1; j < nodes.size(); j++) {
                if (depths[j] < d) break;
                if (depths[j] == d) { last = false; break; }
            }
            isLast[i] = last;
        }

        // Pre-render coords so we can align versions.
        record Pretty(int depth, String prefix, String pkg, String ver, String group, boolean highlight) {}
        List<Pretty> pretties = new ArrayList<>();
        int maxPkg = 0, maxVer = 0;
        for (int i = 0; i < nodes.size(); i++) {
            String coord = nodes.get(i)[1];
            String[] p2 = coord.split(":");
            String g = p2[0];
            String a = p2[1];
            String v = p2.length >= 4 ? p2[3] : (p2.length >= 3 ? p2[2] : "");
            String group = shortGroup(g);
            boolean hi = a.contains(name);
            // build the ├──/└── prefix using ancestor lastness
            StringBuilder pre = new StringBuilder();
            for (int d = 0; d < depths[i]; d++) {
                boolean ancestorLast = false;
                for (int k = i - 1; k >= 0; k--) {
                    if (depths[k] == d) { ancestorLast = isLast[k]; break; }
                }
                pre.append(d < depths[i] - 1 ? (ancestorLast ? "    " : "│   ") : (isLast[i] ? "└── " : "├── "));
            }
            pretties.add(new Pretty(depths[i], pre.toString(), a, v, group, hi));
            maxPkg = Math.max(maxPkg, a.length() + pre.length());
            maxVer = Math.max(maxVer, v.length());
        }

        for (Pretty pr : pretties) {
            String left = pr.prefix + pr.pkg;
            String leftCol = pr.highlight ? GRN + left + R : left;
            String verCol = pr.depth == 0 ? "" : YLW + pad(pr.ver, maxVer) + R;
            String groupCol = pr.depth == 0 ? "" : DIM + " [" + pr.group + "]" + R;
            System.out.println(pad(leftCol, maxPkg + 2) + verCol + groupCol);
        }
        return 0;
    }

    private static String shortGroup(String g) {
        String[] parts = g.split("\\.");
        if (parts.length <= 2) return g;
        // skip org./com./io./net.
        int start = (parts[0].length() <= 3) ? 1 : 0;
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < parts.length; i++) {
            if (sb.length() > 0) sb.append(".");
            sb.append(parts[i]);
        }
        return sb.toString();
    }
}
