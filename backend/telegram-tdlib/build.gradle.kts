plugins {
    id("spring.kotlin.module.conventions")
    id("kotlin.detekt.conventions")
}

// The TDLight binding is an `implementation` dependency, so it.tdlight.* never reaches backend:app's compile
// classpath (ADR-0002, ADR-0004). Natives are runtime-only, one classifier per supported platform:
// linux x64 and arm64 (glibc + OpenSSL 3, as in eclipse-temurin:25-jre) and macOS on Apple silicon.
dependencies {
    implementation(libs.tdlight.java)
    runtimeOnly(variantOf(libs.tdlight.natives) { classifier("linux_amd64_gnu_ssl3") })
    runtimeOnly(variantOf(libs.tdlight.natives) { classifier("linux_arm64_gnu_ssl3") })
    runtimeOnly(variantOf(libs.tdlight.natives) { classifier("macos_arm64") })

    testImplementation(libs.junit.jupiter)
}
