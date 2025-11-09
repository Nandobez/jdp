package dev.nandobez.jdp.sources;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.nandobez.jdp.core.Coord;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

/** Maven Central solr-search wrapper. */
public class Central {
    private static final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(8)).build();
    private static final ObjectMapper M = new ObjectMapper();

    public record Hit(String groupId, String artifactId, String latestVersion) {
        public Coord toCoord() { return new Coord(groupId, artifactId, latestVersion); }
    }

    /** Search by free-text. Adds wildcard fallback if the direct query gives no hits. */
    public static List<Hit> search(String query, int limit) throws Exception {
        // 1. As-is — covers users who pass full GA queries, "a:foo", "g:bar AND a:baz"
        var hits = solr(query, limit);
        if (!hits.isEmpty() || query.contains(":") || query.contains(" ")) return hits;
        // 2. fuzzy: substring on artifactId
        hits = solr("a:*" + query + "*", limit);
        if (!hits.isEmpty()) return hits;
        // 3. last resort: substring on either column
        return solr("a:*" + query + "* OR g:*" + query + "*", limit);
    }

    private static List<Hit> solr(String q, int limit) throws Exception {
        String url = "https://search.maven.org/solrsearch/select?q=" +
            URLEncoder.encode(q, StandardCharsets.UTF_8) + "&rows=" + limit + "&wt=json";
        return fetchHits(url);
    }

    /** Fetch every version of a given GA, newest first. */
    public static List<String> versions(String groupId, String artifactId) throws Exception {
        String q = "g:\"" + groupId + "\"+AND+a:\"" + artifactId + "\"";
        String url = "https://search.maven.org/solrsearch/select?q=" + q + "&core=gav&rows=40&wt=json";
        var docs = fetchDocs(url);
        List<String> out = new ArrayList<>();
        for (JsonNode d : docs) out.add(d.path("v").asText());
        return out;
    }

    /** Resolve the latest version for a GA. Returns null if unknown. */
    public static String latestVersion(String groupId, String artifactId) throws Exception {
        var hits = search("g:\"" + groupId + "\" AND a:\"" + artifactId + "\"", 1);
        return hits.isEmpty() ? null : hits.get(0).latestVersion();
    }

    private static List<Hit> fetchHits(String url) throws Exception {
        var docs = fetchDocs(url);
        List<Hit> out = new ArrayList<>();
        for (JsonNode d : docs) {
            out.add(new Hit(d.path("g").asText(), d.path("a").asText(),
                d.has("latestVersion") ? d.path("latestVersion").asText() : d.path("v").asText()));
        }
        return out;
    }

    private static JsonNode fetchDocs(String url) throws Exception {
        var req = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(10))
            .header("Accept", "application/json")
            .header("User-Agent", "jdp/0.1")
            .build();
        var r = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        if (r.statusCode() / 100 != 2) throw new RuntimeException("central " + r.statusCode());
        return M.readTree(r.body()).path("response").path("docs");
    }
}
