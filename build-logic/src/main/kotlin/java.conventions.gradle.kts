plugins {
    id("common.conventions")
    java
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(libs.version("java"))
    }
}
