plugins {
    id("spring.kotlin.service.conventions")
    id("spring.modulith.conventions")
    id("spring.ai.conventions")
    id("kotlin.detekt.conventions")
    id("integration.test.conventions")
}

val spa = configurations.dependencyScope("spa")
val spaFiles = configurations.resolvable("spaFiles") { extendsFrom(spa.get()) }

dependencies {
    spa(project(path = ":frontend", configuration = "spa"))
    implementation(project(":backend:telegram-tdlib"))

    implementation(libs.spring.boot.starter.webmvc)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.spring.boot.starter.data.jdbc)
    implementation(libs.spring.boot.starter.flyway)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.spring.boot.starter.mail)
    implementation(libs.spring.boot.starter.security)
    implementation(libs.spring.modulith.starter.core)
    implementation(libs.spring.modulith.starter.jdbc)
    implementation(libs.jackson.module.kotlin)
    implementation(libs.kotlin.reflect)
    runtimeOnly(libs.flyway.database.postgresql)
    runtimeOnly(libs.postgresql)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.boot.starter.webmvc.test)
    testImplementation(libs.spring.modulith.starter.test)
    testImplementation(libs.spring.modulith.docs)

    integrationTestImplementation(libs.spring.boot.testcontainers)
    integrationTestImplementation(libs.testcontainers.junit.jupiter)
    integrationTestImplementation(libs.testcontainers.postgresql)
}

// The React SPA is served by Spring Web from classpath:/static/.
tasks.processResources {
    from(spaFiles) { into("static") }
}
