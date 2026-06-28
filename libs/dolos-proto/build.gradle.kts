/*
 * dolos-proto (Phase 2C) — the synchronous gRPC contract.
 *
 * Holds the protobuf/gRPC service definition (src/main/proto) and the generated Java stubs for the
 * ScoringService. scoring-service implements the server; alert-service calls it. Kept in its own
 * module so the .proto is the single source of truth both sides depend on — never on each other.
 *
 * The protobuf Gradle plugin downloads protoc + the grpc-java plugin as platform binaries and
 * generates messages (java) + service stubs (grpc) into build/generated, added to the main sources.
 */
import com.google.protobuf.gradle.id

plugins {
    id("dolos.java-conventions")
    id("com.google.protobuf") version "0.9.4"
}

dependencies {
    // Exposed (api) so every consumer of this module gets the protobuf + gRPC stub types.
    api("io.grpc:grpc-protobuf:1.68.1")
    api("io.grpc:grpc-stub:1.68.1")
    api("com.google.protobuf:protobuf-java:3.25.5")

    // Generated gRPC stubs reference javax.annotation.Generated; needed only at compile time.
    compileOnly("org.apache.tomcat:annotations-api:6.0.53")
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:3.25.5"
    }
    plugins {
        id("grpc") {
            artifact = "io.grpc:protoc-gen-grpc-java:1.68.1"
        }
    }
    generateProtoTasks {
        all().forEach { task ->
            task.plugins {
                id("grpc")
            }
        }
    }
}
