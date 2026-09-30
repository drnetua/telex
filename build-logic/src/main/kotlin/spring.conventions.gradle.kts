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
    // Mockito's agent appends to the bootstrap classpath, which makes CDS warn on every test JVM.
    jvmArgs("-Xshare:off")
}
