// OmniNative plugin build. Keep the omni-osrs-api reference: the OmniNative build service checks for it.
// The build service builds src/main/java and src/main/resources itself, with exactly this setup, and then
// obfuscates the result. This file is for building and testing on your own PC.
plugins {
    java
}

java {
    toolchain { languageVersion.set(JavaLanguageVersion.of(17)) }
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(11)
    options.encoding = "UTF-8"
}

repositories {
    mavenCentral()
}

dependencies {
    // The OmniNative plugin API (omni-osrs-api). Download it from the Developer page (SDK) into libs/.
    compileOnly(files("libs/omni-osrs-api.jar"))
    // Provided by the client at run time: never bundle these.
    compileOnly("com.google.code.gson:gson:2.8.5")
    compileOnly("org.slf4j:slf4j-api:1.7.25")
    compileOnly("javax.inject:javax.inject:1")
    compileOnly("org.projectlombok:lombok:1.18.30")
    annotationProcessor("org.projectlombok:lombok:1.18.30")
    // Other libraries must be approved by staff and listed in omni-plugin-libraries.txt.
}
