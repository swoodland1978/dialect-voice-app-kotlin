plugins {
    id("com.android.application") version "8.13.2" apply false
    // Bumped from 1.9.20 - com.android.billingclient:billing-ktx 8.x (the minimum Play
    // requires for updates from 2026-08-31) itself depends on a kotlin-stdlib metadata
    // version the 1.9 compiler can't read. Pinned to match the stdlib version billing-ktx
    // actually resolves to (see :app:dependencyInsight --dependency kotlin-stdlib), so
    // there's exactly one stdlib version across the whole dependency graph.
    id("org.jetbrains.kotlin.android") version "2.2.10" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.2.10" apply false
    // Compose Compiler is a separate Gradle plugin as of Kotlin 2.0+, replacing the old
    // composeOptions { kotlinCompilerExtensionVersion = ... } block in app/build.gradle.kts.
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.10" apply false
    id("com.google.gms.google-services") version "4.5.0" apply false
}

tasks.register("clean", Delete::class) {
    delete(rootProject.buildDir)
}
