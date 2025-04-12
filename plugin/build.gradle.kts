import java.util.Properties

plugins {
    `kotlin-dsl`
    `maven-publish`
    `java-gradle-plugin`
    alias(libs.plugins.serialization)
    signing
}

group = "com.ndmatrix"
version = "1.0.3"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17)) // Downgrade to 17
    }
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")
    implementation("com.squareup:kotlinpoet:2.1.0")
    implementation("io.github.izzzgoy:ndimmatrix:1.0.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
    implementation("net.pwall.json:json-kotlin-schema:0.56")
}

gradlePlugin {
    plugins {
        create("architect") {
            id = "io.github.izzzgoy.architect"
            implementationClass = "com.ndmatrix.plugin.ArchitectPlugin"
        }
    }
}


val secretPropsFile = project.rootProject.file("local.properties")
if (secretPropsFile.exists()) {
    secretPropsFile.reader().use {
        Properties().apply {
            load(it)
        }
    }.onEach { (name, value) ->
        ext[name.toString()] = value
    }
} else {
    ext["signing.keyId"] = System.getenv("SIGNING_KEY_ID")
    ext["signing.password"] = System.getenv("SIGNING_PASSWORD")
    ext["signing.secretKeyRingFile"] = System.getenv("SIGNING_SECRET_KEY_RING_FILE")
    ext["ossrhUsername"] = System.getenv("OSSRH_USERNAME")
    ext["ossrhPassword"] = System.getenv("OSSRH_PASSWORD")
}

fun getExtraString(name: String) = ext[name]?.toString()

val javadocJar by tasks.registering(Jar::class) {
    archiveClassifier.set("javadoc")
}

publishing {
    publications {
        withType<MavenPublication> {
            groupId = "io.github.izzzgoy"
            artifactId = "architect"
            version = "1.0.3"

            // Stub javadoc.jar artifact
            artifact(tasks["javadocJar"])
            // Provide artifacts information requited by Maven Central
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
    }
    repositories {
        maven {
            name = "sonatype"
            setUrl("https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/")
            credentials {
                username = getExtraString("ossrhUsername")
                password = getExtraString("ossrhPassword")
            }
        }
        mavenLocal()
    }
}

tasks.withType<AbstractPublishToMaven>().configureEach {
    dependsOn(tasks.withType<Sign>())
}

signing {
    sign(publishing.publications)
}
