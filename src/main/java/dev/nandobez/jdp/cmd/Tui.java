package dev.nandobez.jdp.cmd;

import java.util.List;

/** Plain ANSI helpers + adaptive table renderer (box vs narrow). */
public class Tui {
    public static final String R = "\u001B[0m";
    public static final String BLD = "\u001B[1m";
    public static final String DIM = "\u001B[2m";
    public static final String RED = "\u001B[31m";
    public static final String GRN = "\u001B[32m";
    public static final String YLW = "\u001B[33m";
    public static final String BLU = "\u001B[34m";
    public static final String CYN = "\u001B[36m";

    /** Detects terminal width via $COLUMNS, falls back to 80. */
    /** Terminal width. Only set $COLUMNS or JDP_NARROW to force narrow mode. */
    public static int termWidth() {
        String c = System.getenv("COLUMNS");
        if (c != null) try { return Integer.parseInt(c.trim()); } catch (NumberFormatException ignored) {}
        if (System.getenv("JDP_NARROW") != null) return 60;
        // Don't trust stty/tput when invoked via pipe — default to "wide".
        return 9999;
    }

    /** Colorize a GAV: group dim, artifact bold cyan, version yellow. */
    public static String coloredGav(String groupId, String artifactId, String version) {
        StringBuilder sb = new StringBuilder();
        sb.append(DIM).append(groupId).append(R)
          .append(DIM).append(":").append(R)
          .append(BLD).append(CYN).append(artifactId).append(R);
        if (version != null && !version.isBlank()) {
            sb.append(DIM).append(":").append(R)
              .append(YLW).append(version).append(R);
        }
        return sb.toString();
    }

    public static String coloredGa(String groupId, String artifactId) {
        return coloredGav(groupId, artifactId, null);
    }

    public static String pad(String s, int w) {
        if (s == null) s = "";
        int vis = visibleLen(s);
        if (vis >= w) return s;
        return s + " ".repeat(w - vis);
    }

    public static int visibleLen(String s) {
        int n = 0;
        boolean esc = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (esc) { if (c == 'm') esc = false; continue; }
            if (c == 0x1B) { esc = true; continue; }
            n++;
        }
        return n;
    }

    /** Render table. Auto-grows each column to fit header text. Switches to narrow plain layout when too wide. */
    public static void table(String[] headers, int[] widths, List<String[]> rows) {
        int[] w = widths.clone();
        for (int i = 0; i < headers.length; i++) {
            int hl = visibleLen(headers[i]);
            if (w[i] < hl) w[i] = hl;
        }
        int needed = 1;
        for (int x : w) needed += x + 3;
        if (needed > termWidth()) renderNarrow(headers, w, rows);
        else renderBox(headers, w, rows);
    }

    private static void renderBox(String[] headers, int[] widths, List<String[]> rows) {
        top(widths);
        boxedRow(widths, mapBold(headers));
        rule(widths);
        for (String[] r : rows) boxedRow(widths, r);
        bot(widths);
    }

    private static String[] mapBold(String[] headers) {
        String[] out = new String[headers.length];
        for (int i = 0; i < headers.length; i++) out[i] = BLD + headers[i] + R;
        return out;
    }

    private static void renderNarrow(String[] headers, int[] widths, List<String[]> rows) {
        // re-tighten widths to actual longest cell to save horizontal space
        int[] w = new int[headers.length];
        for (int i = 0; i < headers.length; i++) {
            w[i] = visibleLen(headers[i]);
            for (String[] r : rows) {
                if (i < r.length) w[i] = Math.max(w[i], visibleLen(r[i] == null ? "" : r[i]));
            }
        }
        // Header
        StringBuilder h = new StringBuilder("# ");
        for (int i = 0; i < headers.length; i++) {
            h.append(BLD).append(pad(headers[i], w[i])).append(R);
            if (i < headers.length - 1) h.append("  ");
        }
        System.out.println(h);
        int n = 1;
        for (String[] r : rows) {
            StringBuilder line = new StringBuilder();
            line.append(DIM).append(String.format("%2d ", n++)).append(R);
            for (int i = 0; i < headers.length; i++) {
                String cell = i < r.length && r[i] != null ? r[i] : "";
                line.append(pad(cell, w[i]));
                if (i < headers.length - 1) line.append("  ");
            }
            System.out.println(line);
        }
    }

    // --- low-level box primitives (still used by table()) ---
    public static void rule(int... widths) {
        StringBuilder sb = new StringBuilder("├");
        for (int i = 0; i < widths.length; i++) {
            sb.append("─".repeat(widths[i] + 2));
            sb.append(i == widths.length - 1 ? "┤" : "┼");
        }
        System.out.println(sb);
    }
    public static void top(int... widths) { box('┌','┬','┐', widths); }
    public static void bot(int... widths) { box('└','┴','┘', widths); }
    private static void box(char l, char m, char r, int... widths) {
        StringBuilder sb = new StringBuilder(String.valueOf(l));
        for (int i = 0; i < widths.length; i++) {
            sb.append("─".repeat(widths[i] + 2));
            sb.append(i == widths.length - 1 ? r : m);
        }
        System.out.println(sb);
    }
    private static void boxedRow(int[] widths, String[] cells) {
        StringBuilder sb = new StringBuilder("│");
        for (int i = 0; i < cells.length; i++)
            sb.append(" ").append(pad(cells[i], widths[i])).append(" │");
        System.out.println(sb);
    }
    /** Legacy row() helper kept for code paths still using manual layout. */
    public static void row(int[] widths, String... cells) { boxedRow(widths, cells); }
}
