// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    // Se declara aquí y se aplica en :app solo si existe app/google-services.json (ver app/build.gradle.kts)
    alias(libs.plugins.google.services) apply false
}
