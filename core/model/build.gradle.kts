plugins {
    alias(libs.plugins.kotlin.jvm)
}

// :core:model haengt von nichts ab - reine Datenklassen und Enums, keine Android-API.
kotlin {
    jvmToolchain(17)
}

dependencies {
    testImplementation(libs.junit)
    testImplementation(libs.truth)
}
