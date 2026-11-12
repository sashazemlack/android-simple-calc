// Root build file.
//
// AGP 9 ships "built-in Kotlin": the org.jetbrains.kotlin.android plugin must NOT be applied.
// AGP 9.5.1 would otherwise use its bundled KGP 2.2.10; pin the Kotlin Gradle Plugin to the
// same version as the Compose compiler plugin (see AGP 9.0 release notes, "Upgrade to a higher KGP").
buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        classpath(libs.kotlin.gradle.plugin)
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
