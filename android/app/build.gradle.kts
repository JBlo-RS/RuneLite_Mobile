import java.security.MessageDigest

plugins {
    id("com.android.application")
}

// JRE tarball produced by jre-build/build-jre17-arm64.sh, checked in under /assets.
// Override with -Prlm.jre=/path/to/jre17-arm64-XXXX-release.tar.xz
val jreTarball: File = (findProperty("rlm.jre") as String?)?.let(::File)
    ?: rootDir.resolve("../assets").listFiles { f -> f.name.matches(Regex("jre17-arm64-.*-release\\.tar\\.xz")) }
        ?.maxByOrNull { it.name }
    ?: error("No JRE tarball found in ../assets; build one with jre-build/build-jre17-arm64.sh")

abstract class StageRuntimeAssets : DefaultTask() {
    @get:InputFile abstract val jre: RegularFileProperty
    @get:InputFile abstract val probeJar: RegularFileProperty
    @get:InputFile abstract val awtDemoJar: RegularFileProperty
    @get:InputDirectory abstract val cacioDir: DirectoryProperty
    @get:InputFile abstract val helperJar: RegularFileProperty
    @get:InputFile abstract val launcherJar: RegularFileProperty
    @get:InputDirectory abstract val pluginsDir: DirectoryProperty
    @get:OutputDirectory abstract val outputDir: DirectoryProperty

    @TaskAction
    fun stage() {
        val out = outputDir.get().asFile
        out.deleteRecursively()
        out.mkdirs()
        val jreFile = jre.get().asFile
        jreFile.copyTo(out.resolve("jre.tar.xz"))
        // Digest lets the app detect a changed bundled JRE and re-extract it
        val digest = MessageDigest.getInstance("SHA-256").digest(jreFile.readBytes())
        out.resolve("jre.sha256").writeText(digest.joinToString("") { "%02x".format(it) })
        probeJar.get().asFile.copyTo(out.resolve("probe.jar"))
        awtDemoJar.get().asFile.copyTo(out.resolve("awtdemo.jar"))
        cacioDir.get().asFile.listFiles { f -> f.name.endsWith(".jar") }!!.forEach {
            it.copyTo(out.resolve("cacio/" + it.name))
        }
        // Everything in assets/cacio goes on the JVM's boot class path
        helperJar.get().asFile.copyTo(out.resolve("cacio/" + helperJar.get().asFile.name))
        launcherJar.get().asFile.copyTo(out.resolve("RuneLite.jar"))
        // Built-in side-loaded plugins (tools/build-plugins.sh)
        pluginsDir.get().asFile.listFiles { f -> f.name.endsWith(".jar") }!!.forEach {
            it.copyTo(out.resolve("plugins/" + it.name))
        }
    }
}

val stageRuntimeAssets = tasks.register<StageRuntimeAssets>("stageRuntimeAssets") {
    jre.set(jreTarball)
    probeJar.set(project(":probe").tasks.named<Jar>("jar").flatMap { it.archiveFile })
    awtDemoJar.set(project(":awtdemo").tasks.named<Jar>("jar").flatMap { it.archiveFile })
    cacioDir.set(layout.projectDirectory.dir("cacio"))
    helperJar.set(project(":jvmhelper").tasks.named<Jar>("jar").flatMap { it.archiveFile })
    // Stock RuneLite launcher, built by tools/build-launcher.sh
    launcherJar.set(layout.projectDirectory.file("runelite/RuneLite.jar"))
    pluginsDir.set(layout.projectDirectory.dir("plugins"))
}

android {
    namespace = "com.jblors.runelitemobile"
    compileSdk = 36
    ndkVersion = "27.1.12297006"

    defaultConfig {
        applicationId = "com.jblors.runelitemobile"
        minSdk = 26
        targetSdk = 35
        versionCode = 53
        versionName = "0.21.4"
        ndk { abiFilters += "arm64-v8a" }
        externalNativeBuild {
            cmake { arguments += "-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON" }
        }
    }

    signingConfigs {
        // Committed debug key so APKs built in different sessions upgrade in place
        getByName("debug") {
            storeFile = rootProject.file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        getByName("debug") { signingConfig = signingConfigs.getByName("debug") }
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.31.6"
        }
    }

    packaging {
        // Extract .so files to nativeLibraryDir: Caciocavallo's CTCScreen needs
        // libpojavexec_awt.so to exist as a real file there
        jniLibs {
            useLegacyPackaging = true
            // Link-time placeholder for libawt_xawt.so; the JRE ships the real one
            excludes += "**/libawt_headless.so"
        }
    }

    androidResources {
        noCompress += listOf("xz", "jar")
    }

    buildFeatures { buildConfig = true }
}

androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(stageRuntimeAssets, StageRuntimeAssets::outputDir)
    }
}

dependencies {
    implementation("org.apache.commons:commons-compress:1.28.0")
    implementation("org.tukaani:xz:1.12")
}
