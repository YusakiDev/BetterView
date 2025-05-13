import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import net.minecrell.pluginyml.bukkit.BukkitPluginDescription

plugins {
    alias(libs.plugins.shadow)
    alias(libs.plugins.pluginyml.bukkit)
}

dependencies {
    implementation(projects.paperCommon)

    sequenceOf("1.21.1")
        .map { it.replace(".", "") }
        .forEach { runtimeOnly(project(":paper-$it")) }
}

tasks.withType<ShadowJar> {
    destinationDirectory = rootProject.layout.buildDirectory.dir("libs")
    from(rootProject.layout.projectDirectory.file("LICENSE"))
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
