package dev.nandobez.jdp.cmd;

import dev.nandobez.jdp.core.Coord;
import dev.nandobez.jdp.core.PomReader;
import dev.nandobez.jdp.sources.Central;
import dev.nandobez.jdp.sources.Osv;
import picocli.CommandLine.*;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import static dev.nandobez.jdp.cmd.Tui.*;

@Command(name = "doctor", mixinStandardHelpOptions = true, description = "Health check: CVEs, outdated versions, known incompat. Aggregated score.")
public class DoctorCmd implements Callable<Integer> {

    @Option(names = {"-p", "--pom"}, defaultValue = "pom.xml")
    Path pom;

    @Option(names = "--fix", description = "Sobe versões com CVE para a versão patched (do OSV).")
    boolean fix;

    @Option(names = {"-y", "--yes"}, description = "Auto-confirm em --fix.")
    boolean autoYes;

    /** Hand-curated incompat rules. */
    private static final List<Rule> RULES = List.of(
        new Rule("io.jsonwebtoken", "jjwt-api", "0.12", "jjwt-impl < 0.12 is API-incompatible with jjwt-api 0.12+"),
        new Rule("org.projectlombok", "lombok", "1.18.30", "lombok < 1.18.30 fails on JDK 21+"),
        new Rule("org.apache.logging.log4j", "log4j-core", "2.17.1", "log4j-core < 2.17.1 → Log4Shell family (CVE-2021-44228 etc)"),
        new Rule("com.fasterxml.jackson.core", "jackson-databind", "2.13.0", "jackson-databind < 2.13.0 has multiple RCE advisories")
    );

    public Integer call() throws Exception {
        var p = PomReader.read(pom);
        if (p.deps().isEmpty()) { System.out.println(DIM + "no deps." + R); return 0; }

        // First pass — gather row data so columns size to content.
        record Row(String pkg, String ver, int cves, String latest, boolean isOld, String note) {}
        List<Row> rows = new ArrayList<>();
        int totalCve = 0, outdated = 0, incompat = 0;

        System.out.println(BLD + "Doctor · " + pom + R);
        System.out.println(DIM + "querying OSV + Central…" + R);

        for (Coord c : p.deps()) {
            // CVE
            int cves = 0;
            try { cves = Osv.query(c).size(); } catch (Exception ignored) {}
            totalCve += cves;

            // Latest
            String latest = null;
            try { latest = Central.latestVersion(c.groupId(), c.artifactId()); } catch (Exception ignored) {}
            boolean isOld = latest != null && c.version() != null && !latest.equals(c.version());
            if (isOld) outdated++;

            // Rules
            String note = "";
            for (Rule r : RULES) {
                if (r.matches(c)) { note = r.message; incompat++; break; }
            }

            String ver = c.version() == null ? "(BOM)" : c.version();
            rows.add(new Row(c.shortName(), ver, cves, latest, isOld, note));
        }

        // Compute widths from longest cell per column (with sensible minimums).
        int wPkg  = Math.max(6,  rows.stream().mapToInt(r -> r.pkg.length()).max().orElse(6));
        int wVer  = Math.max(6,  rows.stream().mapToInt(r -> r.ver.length()).max().orElse(6));
        int wCve  = Math.max(5,  rows.stream().mapToInt(r -> r.cves == 0 ? 1 : ("⚠ " + r.cves).length()).max().orElse(5));
        int wNew  = Math.max(5,  rows.stream().mapToInt(r -> r.isOld ? r.latest.length() : 2).max().orElse(5));
        int wNote = Math.max(8,  rows.stream().mapToInt(r -> r.note.length()).max().orElse(8));
        // cap nota at 70 cols
        if (wNote > 70) wNote = 70;
        boolean hasNotes = rows.stream().anyMatch(r -> !r.note.isBlank());
        var tableRows = new ArrayList<String[]>();
        for (Row r : rows) {
            String verCell = r.ver.equals("(BOM)") ? DIM + "(BOM)" + R : r.ver;
            String cveCell = r.cves == 0 ? GRN + "0" + R : RED + "⚠ " + r.cves + R;
            String newCell = r.isOld ? YLW + r.latest + R : GRN + "ok" + R;
            if (hasNotes) {
                String note = r.note.isBlank() ? GRN + "ok" + R : YLW + r.note + R;
                String noteCell = r.note.length() > wNote ? note.substring(0, wNote - 1) + "…" + R : note;
                tableRows.add(new String[]{ r.pkg, verCell, cveCell, newCell, noteCell });
            } else {
                tableRows.add(new String[]{ r.pkg, verCell, cveCell, newCell });
            }
        }
        if (hasNotes) {
            int[] w = {wPkg, wVer, wCve, wNew, wNote};
            table(new String[]{"package","version","CVE","newer?","note"}, w, tableRows);
        } else {
            int[] w = {wPkg, wVer, wCve, wNew};
            table(new String[]{"package","version","CVE","newer?"}, w, tableRows);
        }
        int score = Math.max(0, 100 - totalCve * 15 - outdated * 3 - incompat * 10);
        String color = score >= 80 ? GRN : (score >= 50 ? YLW : RED);
        System.out.printf("%nscore: %s%d/100%s · CVEs=%d outdated=%d incompat=%d%n",
            color, score, R, totalCve, outdated, incompat);

        if (fix && totalCve > 0) runFix(p.deps(), pom);
        else if (fix) System.out.println(DIM + "nothing to fix." + R);

        return 0;
    }

    private void runFix(List<Coord> deps, Path pom) throws Exception {
        System.out.println();
        System.out.println(BLD + "Fix mode — fetching patched versions…" + R);
        var fixes = new ArrayList<String[]>(); // [oldGav, newVer]
        for (Coord c : deps) {
            var vulns = Osv.query(c);
            if (vulns.isEmpty()) continue;
            String fixed = vulns.stream()
                .map(Osv.Vuln::fixedVersion)
                .filter(v -> v != null && !v.isBlank())
                .max((a, b) -> Rule.cmp(a, b))
                .orElse(null);
            if (fixed == null) {
                System.out.println("  " + YLW + "?" + R + " " + Tui.coloredGav(c.groupId(), c.artifactId(), c.version())
                    + DIM + "  (OSV has no fixed version)" + R);
                continue;
            }
            System.out.println("  " + GRN + "→" + R + " " + Tui.coloredGav(c.groupId(), c.artifactId(), c.version())
                + DIM + "  " + R + YLW + fixed + R);
            fixes.add(new String[]{c.groupId(), c.artifactId(), c.version(), fixed});
        }
        if (fixes.isEmpty()) return;
        System.out.println();
        if (!autoYes && System.console() != null) {
            System.out.print("  " + BLD + "apply?" + R + DIM + " [s/N]: " + R);
            String ans = System.console().readLine();
            if (ans == null || !ans.trim().equalsIgnoreCase("s")) {
                System.out.println(DIM + "  aborted." + R);
                return;
            }
        }
        for (String[] f : fixes) {
            dev.nandobez.jdp.core.PomWriter.add(pom, new Coord(f[0], f[1], f[3]));
            System.out.println("  " + GRN + "✓ " + R + f[1] + DIM + " " + f[2] + " → " + R + YLW + f[3] + R);
        }
    }

    record Rule(String groupId, String artifactId, String minVersion, String message) {
        boolean matches(Coord c) {
            if (!c.groupId().equals(groupId) || !c.artifactId().equals(artifactId)) return false;
            if (c.version() == null) return false;
            return cmp(c.version(), minVersion) < 0;
        }

        static int cmp(String a, String b) {
            String[] aa = a.split("[.\\-]"), bb = b.split("[.\\-]");
            for (int i = 0; i < Math.min(aa.length, bb.length); i++) {
                try {
                    int d = Integer.parseInt(aa[i]) - Integer.parseInt(bb[i]);
                    if (d != 0) return d;
                } catch (NumberFormatException e) {
                    int d = aa[i].compareTo(bb[i]);
                    if (d != 0) return d;
                }
            }
            return aa.length - bb.length;
        }
    }
}
