plugins {
    //trick: for the same plugin versions in all sub-modules
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.serialization) apply false
}


version = "1.0.0"
group = "io.github.izzzgoy"
