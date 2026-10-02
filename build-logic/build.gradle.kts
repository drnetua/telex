plugins {
    `kotlin-dsl`
}

repositories {
    gradlePluginPortal()
}

dependencies {
    implementation(libs.kotlin.gradle.plugin)
    implementation(libs.kotlin.spring.gradle.plugin)
    implementation(libs.spring.boot.gradle.plugin)
    implementation(libs.spring.dependency.management.gradle.plugin)
    implementation(libs.detekt.dev.gradle.plugin)
    implementation(libs.detekt.detektifier.gradle.plugin)
    implementation(libs.spotless.gradle.plugin)
}
