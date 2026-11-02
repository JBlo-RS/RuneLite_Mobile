// Tiny desktop-Java program run inside the on-device JVM to smoke-test the JRE.
plugins {
    java
}

java {
    toolchain { languageVersion.set(JavaLanguageVersion.of(17)) }
}

tasks.jar {
    archiveFileName.set("probe.jar")
    manifest { attributes("Main-Class" to "probe.Main") }
}
