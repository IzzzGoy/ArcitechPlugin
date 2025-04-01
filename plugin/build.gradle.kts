plugins {
    `kotlin-dsl`
    `maven-publish`
    `java-gradle-plugin`
    alias(libs.plugins.serialization)
}

group = "com.ndmatrix"
version = "1.0.0"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17)) // Downgrade to 17
    }
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")
    implementation("com.squareup:kotlinpoet:2.1.0")
    implementation("com.ndmatrix.parameter:library:1.0.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
}

gradlePlugin {
    plugins {
        create("architect") {
            id = "com.ndmatrix.plugin"
            implementationClass = "com.ndmatrix.plugin.ArchitectPlugin"
        }
    }
}

publishing {
    repositories {
        mavenLocal()
    }
}
