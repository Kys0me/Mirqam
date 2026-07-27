plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compose)
    alias(libs.plugins.kotlin.compose)
}

kotlin { jvmToolchain(17) }

dependencies {
    implementation(project(":core-platform"))
    implementation(compose.runtime)
    implementation(compose.foundation)
    implementation(compose.ui)
    implementation(compose.material3)
    implementation(libs.kotlinx.coroutines.core)

    // OPTIONAL — swap the ProcessBuilder backend for a real PTY (job control,
    // isatty, window resize). Uncomment and implement Pty4jBackend : TerminalBackend.
    implementation("org.jetbrains.pty4j:pty4j:0.13.4")
}
