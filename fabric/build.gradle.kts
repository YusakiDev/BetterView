import net.fabricmc.loom.task.AbstractRemapJarTask
import net.fabricmc.loom.task.RemapJarTask

plugins {
    alias(libs.plugins.loom)
}

// intermediary mappings are useless here
loom.noIntermediateMappings()

val includeAll: Configuration by configurations.creating

dependencies {
    // dummy fabric env setup
    minecraft(libs.minecraft.base)
    mappings(loom.officialMojangMappings())

    // include common project once
    include(projects.common)

    // include common dependencies
    sequenceOf(libs.caffeine, libs.configurate.yaml).forEach {
        includeAll(it) {
            exclude("net.kyori", "option") // included in adventure platforms
            exclude("com.google.errorprone", "error_prone_annotations") // useless
            exclude("org.jspecify") // useless
        }
    }
}

tasks.named<ProcessResources>("processResources") {
    inputs.property("version", project.version)
    filesMatching("fabric.mod.json") {
        expand("version" to project.version)
    }
}

tasks.named<RemapJarTask>("remapJar") {
    // include common dependencies transitively
    fun doInclude(dep: ResolvedDependency) {
        configurations.named("include").get().withDependencies {
            this.add(dependencyFactory.create(dep.moduleGroup, dep.moduleName, dep.moduleVersion))
        }
        dep.children.forEach { doInclude(it) }
    }
    includeAll.resolvedConfiguration.firstLevelModuleDependencies.forEach { doInclude(it) }
    // include all fabric versions
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
