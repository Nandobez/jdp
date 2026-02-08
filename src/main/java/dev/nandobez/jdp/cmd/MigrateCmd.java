package dev.nandobez.jdp.cmd;

import dev.nandobez.jdp.core.Coord;
import dev.nandobez.jdp.core.PomReader;
import picocli.CommandLine.*;
import java.nio.file.*;
import java.util.concurrent.Callable;

import static dev.nandobez.jdp.cmd.Tui.*;

@Command(name = "migrate", mixinStandardHelpOptions = true, description = "Convert manifest format. Currently: maven → gradle (Kotlin DSL).")
public class MigrateCmd implements Callable<Integer> {

    @Parameters(arity = "1", description = "Conversion direction: maven->gradle")
    String direction;

    @Option(names = {"-p", "--pom"}, defaultValue = "pom.xml")
    Path pom;

    @Option(names = {"-o", "--out"}, defaultValue = "build.gradle.kts")
    Path out;

    public Integer call() throws Exception {
        if (!"maven->gradle".equals(direction) && !"maven-gradle".equals(direction)) {
            System.out.println(RED + "only maven->gradle for now" + R);
            return 2;
        }
        var p = PomReader.read(pom);
        StringBuilder sb = new StringBuilder("""
            plugins {
                java
                application
            }

            repositories { mavenCentral() }

            dependencies {
            """);
        for (Coord c : p.deps()) {
            String scope = "implementation";
            sb.append("    ").append(scope).append("(\"").append(c.gav()).append("\")\n");
        }
        sb.append("}\n");
        Files.writeString(out, sb.toString());
        System.out.println(GRN + "wrote " + out + " (" + p.deps().size() + " deps)" + R);
        return 0;
    }
}
