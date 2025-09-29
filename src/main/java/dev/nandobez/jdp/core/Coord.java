package dev.nandobez.jdp.core;

/** Maven coordinate. version may be null when resolved via BOM. */
public record Coord(String groupId, String artifactId, String version) {
    public String ga() { return groupId + ":" + artifactId; }
    public String gav() { return groupId + ":" + artifactId + ":" + (version == null ? "" : version); }

    /** "starter-web" out of "spring-boot-starter-web" — drops the framework prefix when it matches the group's last token. */
    public String shortName() {
        // spring-boot-starter-X → starter-X (only for known framework prefixes)
        for (String pfx : new String[]{"spring-boot-", "micronaut-", "quarkus-"}) {
            if (artifactId.startsWith(pfx)) return artifactId.substring(pfx.length());
        }
        String last = lastSegment(groupId);
        // strip group prefix only if remainder is still informative (>= 4 chars)
        if (artifactId.startsWith(last + "-")) {
            String rest = artifactId.substring(last.length() + 1);
            // keep prefix when the remainder is a generic word
            if (rest.length() >= 5 && !rest.matches("(core|api|impl|util|utils|common|commons)")) return rest;
        }
        return artifactId;
    }

    public String shortGroup() {
        String[] parts = groupId.split("\\.");
        if (parts.length <= 2) return parts[parts.length - 1];
        String last = parts[parts.length - 1];
        // last segment is generic? prefer the middle one
        if (last.length() <= 5 || last.equals("core") || last.equals("common") || last.equals("api")) {
            return parts[parts.length - 2];
        }
        return last;
    }

    private static String lastSegment(String g) {
        int i = g.lastIndexOf('.');
        return i < 0 ? g : g.substring(i + 1);
    }

    public static Coord parse(String gav) {
        String[] p = gav.split(":");
        if (p.length == 2) return new Coord(p[0], p[1], null);
        if (p.length >= 3) return new Coord(p[0], p[1], p[2]);
        throw new IllegalArgumentException("not a GAV: " + gav);
    }
}
