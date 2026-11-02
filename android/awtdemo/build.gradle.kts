// Swing demo run inside the on-device JVM on Caciocavallo (milestone 2).
plugins {
    java
}

java {
    toolchain { languageVersion.set(JavaLanguageVersion.of(17)) }
}

tasks.jar {
    archiveFileName.set("awtdemo.jar")
    manifest { attributes("Main-Class" to "awtdemo.Main") }
}
