package dev.nandobez.jdp.cmd;

import dev.nandobez.jdp.sources.Central;
import picocli.CommandLine.*;
import java.util.List;
import java.util.concurrent.Callable;

import static dev.nandobez.jdp.cmd.Tui.*;

@Command(name = "diff", mixinStandardHelpOptions = true, description = "Compare two versions of an artifact (release-note pointer + Central metadata).")
public class DiffCmd implements Callable<Integer> {

    @Parameters(arity = "1", description = "artifactId (g:a accepted) e.g. spring-boot-starter-web or org.springframework.boot:spring-boot-starter-web")
    String artifact;

    @Parameters(arity = "1", description = "Version range, e.g. 3.3.4..3.4.0")
    String range;

    /** Group prefixes considered canonical when the user gives only an artifactId. */
    private static final List<String> CANONICAL = List.of(
        "org.springframework", "org.apache", "com.fasterxml", "io.jsonwebtoken",
        "org.projectlombok", "com.google", "io.micronaut", "io.quarkus",
        "org.junit", "org.mockito", "io.netty", "io.grpc", "org.postgresql",
        "mysql", "redis.clients", "org.hibernate", "jakarta", "javax"
    );

    public Integer call() throws Exception {
        String[] vv = range.split("\\.\\.");
        if (vv.length != 2) {
            System.out.println();
            System.out.println("  " + RED + BLD + "✗ formato inválido" + R);
            System.out.println("    " + DIM + "esperado: " + R + BLD + "A..B" + R
                + DIM + "  ex: " + R + BLD + "3.3.4..3.4.0" + R);
            return 2;
        }

        String groupId, artifactId;
        if (artifact.contains(":")) {
            String[] p = artifact.split(":");
            groupId = p[0]; artifactId = p[1];
        } else {
            final String aId = artifact;
            artifactId = aId;
            var hits = Central.search("a:\"" + aId + "\"", 20);
            if (hits.isEmpty()) {
                System.out.println();
                System.out.println("  " + RED + BLD + "✗ não encontrado no Maven Central" + R);
                System.out.println("    " + DIM + "artifact '" + R + BLD + aId + R + DIM + "' não existe." + R);
                System.out.println("    " + DIM + "use o GAV completo (groupId:artifactId) se for ambíguo." + R);
                return 2;
            }
            var exact = hits.stream().filter(h -> h.artifactId().equals(aId)).toList();
            if (exact.isEmpty()) exact = hits;
            var finalExact = exact;
            var canon = finalExact.stream()
                .filter(h -> CANONICAL.stream().anyMatch(c -> h.groupId().startsWith(c)))
                .findFirst().orElse(finalExact.get(0));
            groupId = canon.groupId();
        }

        System.out.println(BLD + groupId + ":" + artifactId + R);
        System.out.println("  from: " + YLW + vv[0] + R + "   →   to: " + GRN + vv[1] + R);
        System.out.println();
        System.out.println(BLD + "Release notes (best-effort links):" + R);
        System.out.println("  https://central.sonatype.com/artifact/" + groupId + "/" + artifactId + "/" + vv[1]);

        // Project-specific shortcuts
        if (groupId.startsWith("org.springframework.boot")) {
            System.out.println("  https://github.com/spring-projects/spring-boot/releases/tag/v" + vv[1]);
            System.out.println("  https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-" + vv[1] + "-Release-Notes");
        } else if (groupId.startsWith("org.springframework")) {
            System.out.println("  https://github.com/spring-projects/spring-framework/releases/tag/v" + vv[1]);
        } else if (groupId.startsWith("com.fasterxml.jackson")) {
            System.out.println("  https://github.com/FasterXML/jackson/wiki/Jackson-Release-" + vv[1]);
        } else if (groupId.startsWith("io.jsonwebtoken")) {
            System.out.println("  https://github.com/jwtk/jjwt/releases/tag/" + vv[1]);
        } else if (groupId.startsWith("org.projectlombok")) {
            System.out.println("  https://projectlombok.org/changelog");
        } else if (groupId.startsWith("org.apache.logging.log4j")) {
            System.out.println("  https://logging.apache.org/log4j/2.x/release-notes.html");
        } else if (groupId.startsWith("org.postgresql")) {
            System.out.println("  https://jdbc.postgresql.org/changelogs/");
        }
        return 0;
    }
}
