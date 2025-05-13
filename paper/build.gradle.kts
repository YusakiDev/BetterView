import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import net.minecrell.pluginyml.bukkit.BukkitPluginDescription

plugins {
    alias(libs.plugins.shadow)
    alias(libs.plugins.pluginyml.bukkit)
}

dependencies {
    compileOnly(libs.paper.api.base)
    implementation(projects.paperCommon)

    rootProject.subprojects
        .filter { it.name.matches("^paper-\\d+$".toRegex()) }
        .forEach { runtimeOnly(it) }
}

tasks.withType<ShadowJar> {
    // final paper jar, place it in root build dir
    destinationDirectory = rootProject.layout.buildDirectory.dir("libs")
    archiveClassifier = ""
    // this subproject can't be licensed as LGPL as the paper api is GPL
    exclude("${rootProject.name.uppercase()}_LICENSE.LESSER")
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
