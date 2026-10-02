pluginManagement {
    includeBuild("build-logic")
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "tele-x"

// The Spring Boot app: all 13 Modulith modules as packages of `telex` (ADR-0002).
include("backend:app")

// Kotlin facade over TDLib — the only project that sees org.drinkless.tdlib.* (ADR-0002).
include("backend:telegram-tdlib")

// pnpm workspace packages, driven from Gradle (see pnpm.conventions).
include("frontend")

// The Playwright e2e package: only its type-check runs in the Gradle build (the tests need the whole stack, see CI).
include("e2e")
