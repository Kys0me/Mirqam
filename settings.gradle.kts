@file:Suppress("UnstableApiUsage")

pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
}

// Auto-provision a JDK 17 toolchain if the machine's default JDK differs.
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
}

rootProject.name = "Mirqam"

include(
    ":core-platform",
    ":lang-engine",
    ":editor",
    ":terminal-engine",
    ":ui-components",
    ":ui-shell",
    ":app",
)
