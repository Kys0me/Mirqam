plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compose)
    alias(libs.plugins.kotlin.compose)
}

kotlin { jvmToolchain(17) }

dependencies {
    implementation(project(":core-platform"))
    implementation(project(":lang-engine"))
    implementation(compose.runtime)
    implementation(compose.foundation)
    implementation(compose.ui)
    implementation(compose.material3)
    implementation(libs.material.icons.extended)
}
