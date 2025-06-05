import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import net.minecrell.pluginyml.bukkit.BukkitPluginDescription
import xyz.jpenilla.runpaper.task.RunServer

plugins {
    alias(libs.plugins.shadow)
    alias(libs.plugins.pluginyml.bukkit)
    alias(libs.plugins.runtask.paper)
}

dependencies {
    compileOnly(libs.paper.api.base)
    implementation(projects.paperCommon)

    rootProject.subprojects
        .filter { it.name.matches("^paper-\\d+$".toRegex()) }
        .forEach { runtimeOnly(it) }
}

tasks.withType<Jar> {
    manifest.attributes(
        mapOf(
            "paperweight-mappings-namespace" to "mojang"
        )
    )
}

tasks.withType<ShadowJar> {
    mergeServiceFiles()
    // final paper jar, place it in root build dir
    destinationDirectory = rootProject.layout.buildDirectory.dir("libs")
    archiveClassifier = ""
}

tasks.named("assemble") {
    dependsOn(tasks.withType<ShadowJar>())
}

configure<BukkitPluginDescription> {
    name = rootProject.name
    main = "${project.group}.BetterViewPlugin"
    authors = listOf("booky10")
    apiVersion = "1.21.1"
}

tasks.named<RunServer>("runServer") {
    minecraftVersion("1.21.3")
}
