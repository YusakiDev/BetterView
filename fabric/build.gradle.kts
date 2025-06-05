import net.fabricmc.loom.task.AbstractRemapJarTask
import net.fabricmc.loom.task.RemapJarTask

plugins {
    alias(libs.plugins.loom)
}

// intermediary mappings are useless here
loom.noIntermediateMappings()

dependencies {
    // dummy fabric env setup
    minecraft(libs.minecraft.base)
    mappings(loom.officialMojangMappings())
}

tasks.named<ProcessResources>("processResources") {
    inputs.property("version", project.version)
    filesMatching("fabric.mod.json") {
        expand("version" to project.version)
    }
}

tasks.named<RemapJarTask>("remapJar") {
    rootProject.subprojects
        .filter { it.name.startsWith("fabric-") }
        .forEach { subproject ->
            // as the subproject hasn't been initialized yet,
            // we can't just use tasks.named
            subproject.tasks
                .matching { it.name == "remapJar" }
                .configureEach {
                    nestedJars.from(this)
                }
        }
    // final fabric jar, place it in root build dir
    destinationDirectory = rootProject.layout.buildDirectory.dir("libs")
}

tasks.withType<AbstractRemapJarTask> {
    archiveBaseName = "${rootProject.name}-${project.name}".lowercase()
}

loom {
    mixin.defaultRefmapName = "${rootProject.name}-${project.name}-refmap.json".lowercase()
}
