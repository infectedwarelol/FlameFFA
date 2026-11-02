// author flamesentinell
plugins {
    java
}

group = "dev.flamesentinell"
version = "1.0.0"

repositories {
    maven("https://repo.papermc.io/repository/maven-public/")
    mavenCentral()
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    // luckperms is a required server plugin  hikari h2 and mariadb are pulled at runtime by FlameFFACoreLoader
    compileOnly("net.luckperms:api:5.4")
    compileOnly("com.zaxxer:HikariCP:6.2.1")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(21)
}
