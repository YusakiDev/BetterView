dependencies {
    compileOnlyApi(libs.slf4j.api)
    compileOnlyApi(libs.jspecify)
    compileOnlyApi(libs.checker.qual)
    compileOnlyApi(libs.netty.buffer)
    compileOnlyApi(libs.netty.transport)
    compileOnlyApi(libs.fastutil)
    compileOnlyApi(libs.adventure.api)
    api(libs.caffeine)
    api(libs.configurate.yaml)
}

tasks.named<ProcessResources>("processResources") {
    inputs.property("version", project.version)
    filesMatching("fabric.mod.json") {
        expand("version" to project.version)
    }
}
