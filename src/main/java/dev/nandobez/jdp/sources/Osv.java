package dev.nandobez.jdp.sources;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.nandobez.jdp.core.Coord;

import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;

/** OSV.dev vulnerability query for Maven coordinates. */
public class Osv {
    private static final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(8)).build();
    private static final ObjectMapper M = new ObjectMapper();

    public record Vuln(String id, String summary, String severity, String fixedVersion) {}

    public static List<Vuln> query(Coord c) throws Exception {
        if (c.version() == null) return List.of();
        ObjectNode body = M.createObjectNode();
        body.putObject("package")
            .put("name", c.ga())
            .put("ecosystem", "Maven");
        body.put("version", c.version());

        var req = HttpRequest.newBuilder(URI.create("https://api.osv.dev/v1/query"))
            .timeout(Duration.ofSeconds(10))
            .header("Content-Type", "application/json")
            .header("User-Agent", "jdp/0.1")
            .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
            .build();
        var r = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        if (r.statusCode() / 100 != 2) return List.of();
        JsonNode tree = M.readTree(r.body());
        List<Vuln> out = new ArrayList<>();
        for (JsonNode v : tree.path("vulns")) {
            String sev = "UNKNOWN";
            JsonNode s = v.path("severity");
            if (s.isArray() && s.size() > 0) sev = s.get(0).path("score").asText("UNKNOWN");
            else if (v.has("database_specific")) sev = v.path("database_specific").path("severity").asText("UNKNOWN");
            String fix = firstFixed(v);
            out.add(new Vuln(v.path("id").asText(), v.path("summary").asText(""), sev, fix));
        }
        return out;
    }

    private static String firstFixed(JsonNode v) {
        for (JsonNode aff : v.path("affected")) {
            for (JsonNode r : aff.path("ranges")) {
                for (JsonNode ev : r.path("events")) {
                    if (ev.has("fixed")) return ev.path("fixed").asText();
                }
            }
        }
        return null;
    }
}
