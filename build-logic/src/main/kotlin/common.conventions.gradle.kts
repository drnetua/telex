repositories {
    mavenCentral()
    // TDLight Java and its prebuilt TDLib natives are published only here (ADR-0004).
    maven("https://mvn.mchv.eu/repository/mchv/") {
        content { includeGroup("it.tdlight") }
    }
}
