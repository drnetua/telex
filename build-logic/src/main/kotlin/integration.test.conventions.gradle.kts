/**
 * Adds the `integrationTest` JVM test suite (`src/integrationTest/kotlin`): Spring context,
 * Testcontainers, WireMock. It inherits the unit-test dependencies, runs after `test`, and is part of `check`.
 */
plugins {
    java
}

val integrationTestSuite =
    testing.suites.register<JvmTestSuite>("integrationTest") {
        dependencies {
            implementation(project())
        }
        targets.configureEach {
            testTask.configure {
                shouldRunAfter(tasks.named("test"))
            }
        }
    }

configurations.named("integrationTestImplementation") {
    extendsFrom(configurations.testImplementation.get())
}
configurations.named("integrationTestRuntimeOnly") {
    extendsFrom(configurations.testRuntimeOnly.get())
}

tasks.named("check") {
    dependsOn(integrationTestSuite)
}
