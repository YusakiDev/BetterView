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
