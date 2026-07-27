// Root build: declares plugin versions once (apply false), modules apply them
// without repeating versions. See gradle/libs.versions.toml for the catalog.
plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.compose) apply false
}
