plugins {
    `maven-publish`
}

group = "xyz.ksharma.krail"
version = file("version.txt").readText().trim()

// Package all .proto files into a JAR at the root so Wire's srcJar resolves
// imports without a subdirectory prefix. Both KRAIL-BFF (Wire JVM 5.x) and
// KRAIL (Wire KMP 6.x) resolve "api/trip.proto" from this JAR.
val protoJar by tasks.registering(Jar::class) {
    archiveBaseName = "api-proto"
    archiveVersion = version.toString()
    archiveClassifier = "proto"
    destinationDirectory = layout.buildDirectory.dir("libs")
    // Wire expects imports to resolve as "api/trip.proto", "data/routes_dataset.proto"
    // etc. — so we include from proto/ with that path structure preserved.
    from("proto")
    include("**/*.proto")
}

publishing {
    publications {
        create<MavenPublication>("proto") {
            artifactId = "api-proto"
            artifact(protoJar)
            pom {
                name = "KRAIL API Proto"
                description = "Shared protobuf contract for KRAIL BFF and KMP client"
                url = "https://github.com/ksharma-xyz/KRAIL-API-PROTO"
            }
        }
    }
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/ksharma-xyz/KRAIL-API-PROTO")
            credentials {
                username = System.getenv("GITHUB_ACTOR")
                password = System.getenv("GITHUB_TOKEN")
            }
        }
    }
}
