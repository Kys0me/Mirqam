plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compose)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

kotlin { jvmToolchain(17) }

dependencies {
    implementation(compose.runtime)
    // Highlighter emits Compose AnnotatedString/Color/SpanStyle.
    implementation(compose.ui)
    implementation(libs.kotlinx.serialization.json)
    
    testImplementation(kotlin("test"))
    testImplementation("junit:junit:4.13.2")
}
