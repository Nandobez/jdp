package dev.nandobez.jdp.core;

import java.util.*;

/** Equivalence classes: artifacts that fill the same role. Used to flag conflicts on add. */
public class Equivalence {

    public record Class(String category, String description, List<String> gas) {}

    private static final List<Class> CLASSES = List.of(
        new Class("logger-impl", "logging implementation (use only 1)", List.of(
            "org.apache.logging.log4j:log4j-core",
            "ch.qos.logback:logback-classic",
            "org.slf4j:slf4j-simple",
            "org.slf4j:slf4j-jdk14",
            "org.apache.logging.log4j:log4j-slf4j-impl"
        )),
        new Class("jwt", "JWT library (use only 1)", List.of(
            "io.jsonwebtoken:jjwt-api",
            "com.nimbusds:nimbus-jose-jwt",
            "com.auth0:java-jwt",
            "org.bitbucket.b_c:jose4j"
        )),
        new Class("json-serializer", "JSON serialization library (use only 1, usually)", List.of(
            "com.fasterxml.jackson.core:jackson-databind",
            "com.google.code.gson:gson",
            "jakarta.json.bind:jakarta.json.bind-api",
            "com.squareup.moshi:moshi"
        )),
        new Class("web-framework", "Spring web framework (use only 1)", List.of(
            "org.springframework.boot:spring-boot-starter-web",
            "org.springframework.boot:spring-boot-starter-webflux"
        )),
        new Class("jpa-provider", "JPA implementation (use only 1)", List.of(
            "org.hibernate.orm:hibernate-core",
            "org.hibernate:hibernate-core",
            "org.eclipse.persistence:eclipselink",
            "org.apache.openjpa:openjpa"
        )),
        new Class("test-framework", "test framework (use only 1)", List.of(
            "org.junit.jupiter:junit-jupiter",
            "org.junit.jupiter:junit-jupiter-api",
            "junit:junit",
            "org.testng:testng"
        )),
        new Class("mock-lib", "mock library (typically 1)", List.of(
            "org.mockito:mockito-core",
            "org.mockito:mockito-junit-jupiter",
            "org.easymock:easymock",
            "org.powermock:powermock-api-mockito2"
        )),
        new Class("http-client", "HTTP client (1, or 2 for different purposes)", List.of(
            "com.squareup.okhttp3:okhttp",
            "org.apache.httpcomponents.client5:httpclient5",
            "org.apache.httpcomponents:httpclient",
            "io.github.resilience4j:resilience4j-retrofit"
        )),
        new Class("validation", "Bean Validation implementation (use only 1)", List.of(
            "org.hibernate.validator:hibernate-validator",
            "org.glassfish:jakarta.el"
        ))
    );

    public record Conflict(Class category, String existingGa) {}

    /** Returns the first existing dep that shares a category with the new coord, or null. */
    public static Conflict find(Coord newCoord, List<Coord> existing) {
        String wantGa = newCoord.ga();
        for (Class cls : CLASSES) {
            if (!cls.gas().contains(wantGa)) continue;
            for (Coord e : existing) {
                if (e.ga().equals(wantGa)) continue;
                if (cls.gas().contains(e.ga())) return new Conflict(cls, e.ga());
            }
        }
        return null;
    }
}
