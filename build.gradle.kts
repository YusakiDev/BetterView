plugins {
    alias(libs.plugins.shadow) apply false
    alias(libs.plugins.paperweight.userdev) apply false
    alias(libs.plugins.loom) apply false
    alias(libs.plugins.pluginyml.bukkit) apply false
}

allprojects {
    group = "dev.booky.betterview"
    version = "1.0.0-SNAPSHOT"
}

subprojects {
    apply<JavaLibraryPlugin>()
    apply<MavenPublishPlugin>()

    repositories {
        maven("https://repo.minceraft.dev/public/")
    }

    configure<JavaPluginExtension> {
        withSourcesJar()
        toolchain {
            languageVersion = JavaLanguageVersion.of(21)
            vendor = JvmVendorSpec.ADOPTIUM
        }
    }

    configure<PublishingExtension> {
        publications.create<MavenPublication>("maven") {
            artifactId = "${rootProject.name}-${project.name}".lowercase()
            from(components["java"])
        }
        repositories.maven("https://repo.minceraft.dev/releases/") {
            name = "minceraft"
            authentication { create<BasicAuthentication>("basic") }
            credentials(PasswordCredentials::class)
        }
    }

    tasks.withType<JavaCompile> {
        options.encoding = Charsets.UTF_8.name()
        options.compilerArgs.add("-Xlint:unchecked")
        options.compilerArgs.add("-Xlint:deprecation")
        options.compilerArgs.add("-Xlint:removal")
    }

    tasks.withType<Jar> {
        archiveBaseName = "${rootProject.name}-${project.name}".lowercase()
    }
}

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}
