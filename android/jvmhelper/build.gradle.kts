// Classes the Android side calls inside the JVM; put on the boot class path.
plugins {
    java
}

java {
    toolchain { languageVersion.set(JavaLanguageVersion.of(17)) }
}

tasks.jar {
    archiveFileName.set("rlm-jvmhelper.jar")
}
