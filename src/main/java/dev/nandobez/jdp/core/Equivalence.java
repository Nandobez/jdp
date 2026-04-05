package dev.nandobez.jdp.core;

import java.util.*;

/** Equivalence classes: artifacts that fill the same role. Used to flag conflicts on add. */
public class Equivalence {

    public record Class(String category, String description, List<String> gas) {}

    private static final List<Class> CLASSES = List.of(
        new Class("logger-impl", "implementação de logging (use só 1)", List.of(
            "org.apache.logging.log4j:log4j-core",
            "ch.qos.logback:logback-classic",
            "org.slf4j:slf4j-simple",
            "org.slf4j:slf4j-jdk14",
            "org.apache.logging.log4j:log4j-slf4j-impl"
        )),
        new Class("jwt", "biblioteca JWT (use só 1)", List.of(
            "io.jsonwebtoken:jjwt-api",
            "com.nimbusds:nimbus-jose-jwt",
            "com.auth0:java-jwt",
            "org.bitbucket.b_c:jose4j"
        )),
        new Class("json-serializer", "biblioteca de serialização JSON (use só 1 geralmente)", List.of(
            "com.fasterxml.jackson.core:jackson-databind",
            "com.google.code.gson:gson",
            "jakarta.json.bind:jakarta.json.bind-api",
            "com.squareup.moshi:moshi"
        )),
        new Class("web-framework", "framework web Spring (use só 1)", List.of(
            "org.springframework.boot:spring-boot-starter-web",
            "org.springframework.boot:spring-boot-starter-webflux"
        )),
        new Class("jpa-provider", "implementação JPA (use só 1)", List.of(
            "org.hibernate.orm:hibernate-core",
            "org.hibernate:hibernate-core",
            "org.eclipse.persistence:eclipselink",
            "org.apache.openjpa:openjpa"
        )),
        new Class("test-framework", "framework de teste (use só 1)", List.of(
            "org.junit.jupiter:junit-jupiter",
            "org.junit.jupiter:junit-jupiter-api",
            "junit:junit",
            "org.testng:testng"
        )),
        new Class("mock-lib", "biblioteca de mock (geralmente 1)", List.of(
            "org.mockito:mockito-core",
            "org.mockito:mockito-junit-jupiter",
            "org.easymock:easymock",
            "org.powermock:powermock-api-mockito2"
        )),
        new Class("http-client", "cliente HTTP (use 1 ou 2 com propósitos diferentes)", List.of(
            "com.squareup.okhttp3:okhttp",
            "org.apache.httpcomponents.client5:httpclient5",
            "org.apache.httpcomponents:httpclient",
            "io.github.resilience4j:resilience4j-retrofit"
        )),
        new Class("validation", "implementação de Bean Validation (use só 1)", List.of(
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
