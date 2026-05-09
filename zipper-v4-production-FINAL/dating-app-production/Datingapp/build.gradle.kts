// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.google.services) apply false
    alias(libs.plugins.firebase.crashlytics) apply false
}

/** Local clean helper. Pair with `rm -rf app/build` for a full fresh compile. */
tasks.register<Delete>("cleanBuildCache") {
    group = "build"
    description = "Runs :app:clean and deletes the root build/ directory."
    dependsOn(":app:clean")
    delete(rootProject.layout.buildDirectory)
}