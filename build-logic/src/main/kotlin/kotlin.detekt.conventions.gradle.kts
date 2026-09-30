plugins {
    id("kotlin.conventions")
    id("dev.detekt")
    id("com.commonsware.detektifier")
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(isolated.rootProject.projectDirectory.file("config/detekt/detekt.yml"))
    // `detekt` (the task `check` runs) covers the integrationTest suite too, not only main + test.
    source.from("src/integrationTest/kotlin")
}
