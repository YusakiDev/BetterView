plugins {
    alias(libs.plugins.loom)
}

dependencies {
    minecraft(libs.minecraft.v1211)
    mappings(loom.layered {
        officialMojangMappings()
        parchment(variantOf(libs.parchment.v1221) { artifactType("zip") })
    })
    modImplementation(libs.fabric.loader)
}

tasks.named<ProcessResources>("processResources") {
    inputs.property("version", project.version)
    filesMatching("fabric.mod.json") {
        expand("version" to project.version)
    }
}
