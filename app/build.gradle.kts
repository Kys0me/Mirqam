import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compose)
    alias(libs.plugins.kotlin.compose)
}

kotlin { jvmToolchain(17) }

dependencies {
    implementation(project(":ui-shell"))
    // The OS-specific desktop runtime (Skiko + launcher). Only the app needs this.
    implementation(compose.desktop.currentOs)
    // Provides Dispatchers.Main (Swing) used by Compose on desktop.
    implementation(libs.kotlinx.coroutines.swing)
}

compose.desktop {
    application {
        mainClass = "rtlide.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "RtlIde"
            packageVersion = "1.0.0"
        }
    }
}
