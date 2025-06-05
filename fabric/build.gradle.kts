import net.fabricmc.loom.task.AbstractRemapJarTask
import net.fabricmc.loom.task.RemapJarTask
import net.fabricmc.loom.task.prod.ServerProductionRunTask

plugins {
    alias(libs.plugins.loom)
}

// intermediary mappings are useless here
loom.noIntermediateMappings()

val includeAll: Configuration by configurations.creating
val runtimeMods: Configuration by configurations.creating

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

    // include all fabric versions
    rootProject.subprojects
        .filter { it.name.matches("^fabric-\\d+$".toRegex()) }
        .forEach { include(it) }

    // version-specific runtime mods
    runtimeMods(libs.moonrise.v1214)
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
    // final fabric jar, place it in root build dir
    destinationDirectory = rootProject.layout.buildDirectory.dir("libs")
}

tasks.withType<AbstractRemapJarTask> {
    archiveBaseName = "${rootProject.name}-${project.name}".lowercase()
}

loom {
    mixin.defaultRefmapName = "${rootProject.name}-${project.name}-refmap.json".lowercase()
}

// fabric's default task doesn't allow us to specify that we want to have standard input
@UntrackedTask(because = "Always rerun this task.")
abstract class CustomServerProductionRunTask : ServerProductionRunTask {

    @Inject
    constructor() : super()

    override fun configureProgramArgs(exec: ExecSpec?) {
        super.configureProgramArgs(exec)
        exec!!.standardInput = System.`in`
    }
}

tasks.register<CustomServerProductionRunTask>("prodServer") {
    minecraftVersion = "1.21.4"
    loaderVersion = libs.versions.fabric.loader
    javaLauncher = javaToolchains.launcherFor {
        languageVersion = JavaLanguageVersion.of(21)
    }
    // include runtime mods
    mods.from(runtimeMods)
}
