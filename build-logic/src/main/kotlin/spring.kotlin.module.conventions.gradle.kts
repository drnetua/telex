import org.springframework.boot.gradle.tasks.bundling.BootJar

plugins {
    id("spring.kotlin.conventions")
}

tasks.withType<BootJar> {
    enabled = false
}
