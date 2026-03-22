package dev.nandobez.jdp.core;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.*;

/** Public, mutable so commands can read the parsed reason after a failed run. */

import static dev.nandobez.jdp.cmd.Tui.*;

/** Background build verifier with spinner. Main thread paints, worker runs Maven/Gradle. */
public class Verify {

    private static final String[] SPINNER = {"⠋","⠙","⠹","⠸","⠼","⠴","⠦","⠧","⠇","⠏"};
    /** Last failure reason extracted from build output. Null when last run succeeded. */
    public static volatile String lastFailureReason = null;

    public static int run(Path projectDir) {
        if (System.getenv("JDP_SKIP_BUILD") != null) {
            System.out.println(DIM + "JDP_SKIP_BUILD set — skipping post-mutation build." + R);
            return 0;
        }
        boolean isMaven  = Files.exists(projectDir.resolve("pom.xml"));
        boolean isGradle = Files.exists(projectDir.resolve("build.gradle"))
                       || Files.exists(projectDir.resolve("build.gradle.kts"));
        if (!isMaven && !isGradle) {
            System.out.println(DIM + "no build manifest detected — skipping verification." + R);
            return 0;
        }

        List<Step> steps;
        if (isMaven) {
            steps = List.of(
                new Step("resolve", new String[]{"mvn","-q","-B","dependency:resolve"}),
                new Step("compile", new String[]{"mvn","-q","-B","-DskipTests","clean","package"}),
                new Step("install", new String[]{"mvn","-q","-B","-DskipTests","install"})
            );
        } else {
            String g = Files.exists(projectDir.resolve("gradlew")) ? "./gradlew" : "gradle";
            steps = List.of(
                new Step("resolve", new String[]{g,"-q","dependencies","--configuration","runtimeClasspath"}),
                new Step("compile", new String[]{g,"-q","clean","build","-x","test"}),
                new Step("install", new String[]{g,"-q","publishToMavenLocal"})
            );
        }

        lastFailureReason = null;
        System.out.println("  " + DIM + "verificando build (" + (isMaven ? "maven" : "gradle") + ")…" + R);
        long total = System.currentTimeMillis();
        for (Step step : steps) {
            int rc = runWithSpinner(projectDir, step);
            if (rc != 0) return rc;
        }
        System.out.println();
        System.out.println("    " + GRN + "✓" + R + DIM + " concluído em " + (System.currentTimeMillis() - total) + "ms" + R);
        return 0;
    }

    private static int runWithSpinner(Path dir, Step step) {
        var stage = new AtomicReference<>("starting");
        var capture = new ByteArrayOutputStream();
        var pool = Executors.newSingleThreadExecutor(r -> { var t = new Thread(r, "jdp-build"); t.setDaemon(true); return t; });
        long t0 = System.currentTimeMillis();

        // Drop -q so we get progress lines, but keep -B for non-interactive mode.
        Future<Integer> fut = pool.submit(() -> {
            try {
                stage.set("running");
                String[] verboseCmd = java.util.Arrays.stream(step.cmd)
                    .filter(s -> !s.equals("-q")).toArray(String[]::new);
                ProcessBuilder pb = new ProcessBuilder(verboseCmd).directory(dir.toFile()).redirectErrorStream(true);
                Process p = pb.start();
                try (var br = new java.io.BufferedReader(new java.io.InputStreamReader(p.getInputStream()))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        capture.write((line + "\n").getBytes());
                        stage.set(summarizeStage(line, stage.get()));
                    }
                }
                if (!p.waitFor(15, TimeUnit.MINUTES)) { p.destroyForcibly(); return 124; }
                return p.exitValue();
            } catch (Exception e) {
                capture.writeBytes(("\n" + e.getMessage()).getBytes());
                return -1;
            }
        });

        boolean tty = System.console() != null;
        if (!tty) System.out.println("    " + pad(step.label, 8) + DIM + "running…" + R);
        int frame = 0;
        try {
            while (!fut.isDone()) {
                if (tty) {
                    long ms = System.currentTimeMillis() - t0;
                    String line = String.format("\r\033[2K    %s %s %s %s",
                        CYN + SPINNER[frame++ % SPINNER.length] + R,
                        pad(step.label, 8),
                        DIM + stage.get() + R,
                        DIM + "(" + (ms / 1000) + "s)" + R);
                    System.out.print(line);
                    System.out.flush();
                }
                try { Thread.sleep(tty ? 90 : 500); } catch (InterruptedException ignored) {}
            }
        } finally {
            pool.shutdown();
        }

        int rc;
        try { rc = fut.get(); } catch (Exception e) { rc = -1; }
        long ms = System.currentTimeMillis() - t0;
        String mark = rc == 0 ? GRN + "✓" + R : RED + "✗" + R;
        System.out.print("\r\033[2K");
        System.out.println();
        System.out.println("    " + mark + " " + pad(step.label, 8) +
            (rc == 0 ? GRN + "ok" + R : RED + "falhou" + R) +
            DIM + "  (" + ms + "ms)" + R);

        if (rc != 0) {
            lastFailureReason = parseReason(capture.toString());
        }
        return rc;
    }

    /** Extract the most useful single-line cause from maven/gradle output. */
    private static String parseReason(String output) {
        Matcher m;
        // "was not found ... cached" → distinct from never-found
        if ((m = Pattern.compile("([^\\s]+:[^\\s]+:jar:[^\\s]+) was not found.*cached").matcher(output)).find())
            return "artefato " + m.group(1) + " não existe no Maven Central\n(falha cacheada no ~/.m2 — rode `mvn -U` ou apague o diretório do artefato pra reverificar)";
        if ((m = Pattern.compile("Could not find artifact ([^\\s]+) in").matcher(output)).find())
            return "artefato " + m.group(1) + " não existe no Maven Central";
        if ((m = Pattern.compile("Failed to read artifact descriptor for ([^:]+:[^:]+:[^:]+:[^\\s]+)").matcher(output)).find())
            return "descriptor inválido em " + m.group(1) + "\n(o POM do artefato referencia uma versão não publicada)";
        if (output.contains("COMPILATION ERROR"))
            return "erro de compilação no projeto (rode `mvn -e` para detalhes)";
        if (output.contains("Source option") && output.contains("no longer supported"))
            return "JDK incompatível com a configuração do projeto";
        if ((m = Pattern.compile("Failed to execute goal[^:]*:\\s*([^\\n\\[]+)").matcher(output)).find())
            return m.group(1).trim();
        // Fallback: last [ERROR] line that isn't a hint.
        String last = null;
        for (String line : output.split("\\R")) {
            String t = line.replaceAll("\\u001B\\[[0-9;]*m", "").trim();
            if (!t.startsWith("[ERROR]")) continue;
            String body = t.substring(7).trim();
            if (body.isEmpty()) continue;
            if (body.startsWith("->") || body.startsWith("To see") || body.startsWith("Re-run")
                || body.startsWith("For more") || body.startsWith("[Help"))  continue;
            last = body;
        }
        return last != null ? last : "build falhou (sem causa identificável)";
    }

    /** Pick a human-readable stage summary from a single Maven log line. */
    private static String summarizeStage(String line, String previous) {
        if (line == null) return previous;
        if (line.startsWith("Downloading from")) {
            int at = line.lastIndexOf('/');
            if (at < 0) return "downloading…";
            String name = line.substring(at + 1).trim();
            if (name.length() > 40) name = name.substring(0, 37) + "…";
            return "↓ " + name;
        }
        if (line.startsWith("Downloaded from")) return "downloaded";
        if (line.contains("--- maven-compiler-plugin")) return "compiling";
        if (line.contains("--- maven-jar-plugin")) return "packaging";
        if (line.contains("--- maven-install-plugin")) return "installing";
        if (line.contains("--- maven-resources-plugin")) return "resources";
        if (line.contains("--- maven-clean-plugin")) return "cleaning";
        return previous;
    }

    private record Step(String label, String[] cmd) {}
}
