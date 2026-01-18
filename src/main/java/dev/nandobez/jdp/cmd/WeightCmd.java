package dev.nandobez.jdp.cmd;

import picocli.CommandLine.*;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.regex.*;

import static dev.nandobez.jdp.cmd.Tui.*;

@Command(name = "weight", mixinStandardHelpOptions = true, description = "Show jar sizes of resolved dependencies (top N).")
public class WeightCmd implements Callable<Integer> {

    @Option(names = {"-d", "--dir"}, defaultValue = ".")
    Path dir;

    @Option(names = {"-n", "--top"}, defaultValue = "15")
    int top;

    public Integer call() throws Exception {
        // 1. resolve
        var pb = new ProcessBuilder("mvn", "-q", "dependency:resolve").directory(dir.toFile());
        pb.redirectErrorStream(true);
        var p1 = pb.start();
        p1.getInputStream().readAllBytes();
        p1.waitFor();

        // 2. list jars via dependency:build-classpath
        var pb2 = new ProcessBuilder("mvn", "-q", "-DincludeScope=runtime",
            "dependency:build-classpath", "-Dmdep.outputFile=/dev/stdout").directory(dir.toFile());
        pb2.redirectErrorStream(true);
        var p2 = pb2.start();
        String cp;
        try (var br = new BufferedReader(new InputStreamReader(p2.getInputStream()))) {
            cp = br.lines().reduce("", String::concat);
        }
        p2.waitFor();

        record Entry(String name, long bytes) {}
        var entries = new ArrayList<Entry>();
        long total = 0;
        Pattern artifactRe = Pattern.compile(".*/([^/]+\\.jar)$");
        for (String jar : cp.split(":")) {
            if (jar.isBlank()) continue;
            Path j = Paths.get(jar);
            if (!Files.exists(j)) continue;
            long sz = Files.size(j);
            total += sz;
            var m = artifactRe.matcher(jar);
            entries.add(new Entry(m.matches() ? m.group(1) : j.getFileName().toString(), sz));
        }
        entries.sort((a, b) -> Long.compare(b.bytes, a.bytes));

        var rows = new ArrayList<String[]>();
        int wN = 2, wJ = 6, wS = 7;
        for (int i = 0; i < Math.min(top, entries.size()); i++) {
            var e = entries.get(i);
            String sz = human(e.bytes);
            String idx = String.valueOf(i + 1);
            rows.add(new String[]{idx, e.name, sz});
            wN = Math.max(wN, idx.length());
            wJ = Math.max(wJ, e.name.length());
            wS = Math.max(wS, sz.length());
        }
        table(new String[]{"#","jar","tamanho"}, new int[]{wN, wJ, wS}, rows);
        System.out.println(DIM + entries.size() + " jars · total " + human(total) + R);
        return 0;
    }

    private static String human(long b) {
        if (b < 1024) return b + " B";
        if (b < 1024 * 1024) return String.format("%.1f KB", b / 1024.0);
        return String.format("%.1f MB", b / (1024.0 * 1024));
    }
}
