plugins { java }

group = "com.dungeonitems"
version = "0.1.0"

java { toolchain.languageVersion.set(JavaLanguageVersion.of(21)) }

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    compileOnly("com.bountysmp:ConfigurableItems:0.1.0")
    testImplementation("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    testImplementation("com.bountysmp:ConfigurableItems:0.1.0")
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testImplementation("org.mockbukkit.mockbukkit:mockbukkit-v1.21:4.110.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:6.0.3")
}

tasks {
    compileJava { options.encoding = "UTF-8"; options.release.set(21) }
    processResources {
        filteringCharset = "UTF-8"
        filesMatching("plugin.yml") { expand("version" to project.version) }
    }
    test { useJUnitPlatform() }
}
