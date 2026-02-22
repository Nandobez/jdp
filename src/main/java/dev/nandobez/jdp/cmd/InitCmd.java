package dev.nandobez.jdp.cmd;

import picocli.CommandLine.*;
import java.nio.file.*;
import java.util.concurrent.Callable;

import static dev.nandobez.jdp.cmd.Tui.*;

@Command(name = "init", mixinStandardHelpOptions = true, description = "Scaffold a new Java project. Templates: rest-api | batch | lib")
public class InitCmd implements Callable<Integer> {

    @Parameters(arity = "1", description = "Project directory")
    Path dir;

    @Option(names = {"-t", "--template"}, defaultValue = "rest-api",
        description = "rest-api | batch | lib")
    String template;

    @Option(names = "--group", defaultValue = "com.example")
    String groupId;

    @Option(names = "--artifact")
    String artifactId;

    @Option(names = "--java", defaultValue = "17")
    String javaVersion;

    public Integer call() throws Exception {
        if (artifactId == null) artifactId = dir.getFileName().toString();
        Files.createDirectories(dir.resolve("src/main/java/" + groupId.replace('.', '/')));
        Files.createDirectories(dir.resolve("src/test/java"));

        String pkg = groupId;
        String deps = switch (template) {
            case "rest-api" -> """
                    <dependency>
                        <groupId>org.springframework.boot</groupId>
                        <artifactId>spring-boot-starter-web</artifactId>
                        <version>3.3.4</version>
                    </dependency>
                    <dependency>
                        <groupId>org.springframework.boot</groupId>
                        <artifactId>spring-boot-starter-actuator</artifactId>
                        <version>3.3.4</version>
                    </dependency>""";
            case "batch" -> """
                    <dependency>
                        <groupId>org.springframework.boot</groupId>
                        <artifactId>spring-boot-starter-batch</artifactId>
                        <version>3.3.4</version>
                    </dependency>""";
            case "lib" -> "";
            default -> { System.out.println(RED + "unknown template: " + template + R); yield ""; }
        };

        String pom = """
            <?xml version="1.0" encoding="UTF-8"?>
            <project xmlns="http://maven.apache.org/POM/4.0.0">
                <modelVersion>4.0.0</modelVersion>
                <groupId>%s</groupId>
                <artifactId>%s</artifactId>
                <version>0.1.0</version>
                <packaging>jar</packaging>
                <properties>
                    <maven.compiler.release>%s</maven.compiler.release>
                    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
                </properties>
                <dependencies>
            %s
                </dependencies>
                <build>
                    <plugins>
                        <plugin>
                            <artifactId>maven-compiler-plugin</artifactId>
                            <version>3.13.0</version>
                            <configuration><release>%s</release></configuration>
                        </plugin>
                    </plugins>
                </build>
            </project>
            """.formatted(groupId, artifactId, javaVersion, deps, javaVersion);
        Files.writeString(dir.resolve("pom.xml"), pom);

        String main = """
            package %s;

            public class App {
                public static void main(String[] args) {
                    System.out.println("Hello from %s");
                }
            }
            """.formatted(pkg, artifactId);
        Files.writeString(dir.resolve("src/main/java/" + pkg.replace('.', '/') + "/App.java"), main);

        System.out.println(GRN + "created project '" + artifactId + "' at " + dir + " (template=" + template + ")" + R);
        System.out.println(DIM + "cd " + dir + " && jdp list" + R);
        return 0;
    }
}
