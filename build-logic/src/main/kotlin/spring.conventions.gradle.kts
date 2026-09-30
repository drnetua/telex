plugins {
    id("java.conventions")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

dependencies {
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
