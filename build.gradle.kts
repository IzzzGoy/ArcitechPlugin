import com.vanniktech.maven.publish.GradlePlugin
import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.SonatypeHost

plugins {
    `kotlin-dsl`
    `maven-publish`
    `java-gradle-plugin`
    alias(libs.plugins.serialization)
    alias(libs.plugins.kotlinJvm)
    signing
    id("com.vanniktech.maven.publish") version "0.30.0"
}

group = "io.github.izzzgoy.plugin"
version = "1.1.1"

kotlin {
    jvmToolchain(17)

    sourceSets {
        java {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")
                implementation("com.squareup:kotlinpoet:2.1.0")
                implementation("io.github.izzzgoy:ndimmatrix:1.0.3")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
                implementation("net.pwall.json:json-kotlin-schema:0.56")
            }
        }
    }
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17)) // Downgrade to 17
    }
}



gradlePlugin {
    website.set("https://github.com/IzzzGoy/Arcitech")
    vcsUrl.set("https://github.com/IzzzGoy/Arcitech")
    plugins {
        create("architect") {
            id = "io.github.izzzgoy.plugin"
            implementationClass = "io.github.izzzgoy.plugin.ArchitectPlugin"

            displayName = "N-Dimm matrixes architect plugin"
            description = "Generate more! Write less!"
            tags.set(listOf("ndimmatrix", "architect", "generation"))
        }
    }
}

mavenPublishing {
    configure(
        GradlePlugin(
            javadocJar = JavadocJar.Javadoc(),
            sourcesJar = true,
        )
    )

    publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL)

    signAllPublications()
    coordinates(group.toString(), "architect", version.toString())
    pom {
        name.set("NDM Achitect")
        description.set("Architecture component system")
        url.set("https://github.com/IzzzGoy/Arcitech")

        licenses {
            license {
                name.set("MIT")
                url.set("https://opensource.org/licenses/MIT")
            }
        }
        developers {
            developer {
                id.set("FromGoy")
                name.set("Alexey")
                email.set("xzadmoror@gmail.com")
            }
        }
        scm {
            url.set("https://github.com/IzzzGoy/Arcitech")
        }
    }
}
