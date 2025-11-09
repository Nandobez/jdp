package dev.nandobez.jdp.cmd;

import dev.nandobez.jdp.sources.Central;
import picocli.CommandLine.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import static dev.nandobez.jdp.cmd.Tui.*;

@Command(name = "search", mixinStandardHelpOptions = true, description = "Search Maven Central. Autocomplete-style; partial matches OK.")
public class SearchCmd implements Callable<Integer> {

    @Parameters(arity = "1", description = "Query, e.g. 'starter-data' or 'jjwt'.")
    String query;

    @Option(names = {"-n", "--limit"}, defaultValue = "15")
    int limit;

    @Option(names = {"--full", "-f"}, description = "Show full artifactId + groupId (no breakdown).")
    boolean full;

    private static final List<String> CANON = List.of(
        "org.springframework", "org.apache", "com.fasterxml", "io.jsonwebtoken",
        "org.projectlombok", "com.google", "io.micronaut", "io.quarkus", "io.quarkiverse",
        "org.junit", "org.mockito", "io.netty", "io.grpc", "org.postgresql",
        "mysql", "redis.clients", "org.hibernate", "jakarta", "javax",
        "org.slf4j", "ch.qos.logback", "com.zaxxer", "io.projectreactor"
    );

    public Integer call() throws Exception {
        var hits = Central.search(query, limit);
        if (hits.isEmpty()) { System.out.println(DIM + "no hits." + R); return 1; }

        hits = new ArrayList<>(hits);
        hits.sort((a, b) -> Boolean.compare(isCanon(b.groupId()), isCanon(a.groupId())));

        if (full) renderFull(hits);
        else      renderCompact(hits);

        long canonical = hits.stream().filter(h -> isCanon(h.groupId())).count();
        System.out.println(DIM + hits.size() + " hits · " + YLW + "★" + DIM
            + " = grupo canônico (" + canonical + ") · "
            + (full ? "" : "use --full p/ ver groupId completo · ")
            + "jdp add <artifactId>" + R);
        return 0;
    }

    private void renderFull(List<Central.Hit> hits) {
        var rows = new ArrayList<String[]>();
        int wMark = 1, wA = 10, wV = 6, wG = 7;
        for (var h : hits) {
            String mark  = isCanon(h.groupId()) ? YLW + "★" + R : " ";
            rows.add(new String[]{mark, h.artifactId(), h.latestVersion(), h.groupId()});
            wA = Math.max(wA, h.artifactId().length());
            wV = Math.max(wV, h.latestVersion().length());
            wG = Math.max(wG, h.groupId().length());
        }
        table(new String[]{"", "artifactId", "versão", "groupId"},
              new int[]{wMark, wA, wV, wG}, rows);
    }

    private void renderCompact(List<Central.Hit> hits) {
        record Breakdown(String mark, String type, String version, String framework, String domain) {}
        var brs = new ArrayList<Breakdown>();
        for (var h : hits) {
            String[] parts = h.artifactId().split("-");
            String type, framework = "", domain;
            int idx = indexOf(parts, "starter");
            if (idx >= 0) {
                type      = idx + 1 < parts.length ? parts[idx + 1] : "starter";
                framework = idx == 0 ? "" : join(parts, 0, idx, "-");
                String rest = idx + 2 < parts.length ? parts[idx + 2] : "";
                domain    = rest.isEmpty() ? lastSeg(h.groupId()) : rest;
            } else {
                // no "starter" word: pick last segment as type, rest as framework
                type      = parts[parts.length - 1];
                framework = parts.length > 1 ? join(parts, 0, parts.length - 1, "-") : "";
                domain    = lastSeg(h.groupId());
            }
            if (framework.isEmpty()) framework = lastSeg(h.groupId());
            String mark = isCanon(h.groupId()) ? YLW + "★" + R : " ";
            brs.add(new Breakdown(mark, type, h.latestVersion(), framework, domain));
        }

        var rows = new ArrayList<String[]>();
        int wM = 1, wT = 4, wV = 6, wF = 9, wD = 6;
        for (var b : brs) {
            rows.add(new String[]{b.mark, b.type, b.version, b.framework, b.domain});
            wT = Math.max(wT, b.type.length());
            wV = Math.max(wV, b.version.length());
            wF = Math.max(wF, b.framework.length());
            wD = Math.max(wD, b.domain.length());
        }
        table(new String[]{"", "tipo", "versão", "framework", "domínio"},
              new int[]{wM, wT, wV, wF, wD}, rows);
    }

    private static int indexOf(String[] a, String s) {
        for (int i = 0; i < a.length; i++) if (a[i].equals(s)) return i;
        return -1;
    }

    private static String join(String[] a, int from, int to, String sep) {
        StringBuilder sb = new StringBuilder();
        for (int i = from; i < to; i++) {
            if (sb.length() > 0) sb.append(sep);
            sb.append(a[i]);
        }
        return sb.toString();
    }

    private static String lastSeg(String g) {
        int i = g.lastIndexOf('.');
        return i < 0 ? g : g.substring(i + 1);
    }

    private static boolean isCanon(String g) {
        return CANON.stream().anyMatch(g::startsWith);
    }
}
