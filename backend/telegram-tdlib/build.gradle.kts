plugins {
    id("spring.kotlin.module.conventions")
}

// The TDLib Java binding joins as an `implementation` dependency in the E02 spike, so
// org.drinkless.tdlib.* never reaches backend:app's compile classpath (ADR-0002).
