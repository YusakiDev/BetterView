plugins {
    alias(libs.plugins.shadow) apply false
    alias(libs.plugins.paperweight.userdev) apply false
    alias(libs.plugins.loom) apply false
    alias(libs.plugins.pluginyml.bukkit) apply false
    alias(libs.plugins.runtask.paper) apply false
}

allprojects {
    group = "dev.booky.betterview"
    version = "2.0.1-SNAPSHOT"
}

subprojects {
    apply<JavaLibraryPlugin>()
    apply<MavenPublishPlugin>()

    repositories {
        maven("https://repo.minceraft.dev/public/")
        maven("https://repo.tcoded.com/releases")
    }

    configure<JavaPluginExtension> {
        withSourcesJar()
        toolchain {
            languageVersion = JavaLanguageVersion.of(21)
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
        archiveBaseName.set("${rootProject.name}-${project.name}".lowercase())

        // include GPL license file and optional LGPL license file,
        // if the specific subproject is licensed as LGPL (e.g. required for fabric)
        sequenceOf(
            rootProject.layout.projectDirectory.file("LICENSE"),
            project.layout.projectDirectory.file("LICENSE.LESSER"),
        )
            .filter { it.asFile.exists() }
            .forEach { file ->
                from(file) {
                    rename { name -> "${rootProject.name}_${project.name}_${name}".uppercase() }
                }
            }
    }
}

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}
