plugins {
    id("java.conventions")
    kotlin("jvm")
    id("spotless.conventions")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xjsr305=strict")
    }
}

// Java files that sit next to Kotlin sources (e.g. Modulith `package-info.java`) are compiled by javac too.
sourceSets.configureEach {
    java.srcDir("src/$name/kotlin")
}
