plugins {
    id("java")
}
group = "me.yisang"
version = "2.5.1"

repositories {
    mavenCentral()
    // Paper 官方倉庫
    maven("https://repo.papermc.io/repository/maven-public/")
    // ProtocolLib 倉庫
    maven("https://repo.dmulloy2.net/repository/public/")
}

dependencies {
    // 試試看換成這個更寬鬆的寫法，強制抓取
    compileOnly("io.papermc.paper", "paper-api", "1.21.4-R0.1-SNAPSHOT")
    compileOnly("com.comphenix.protocol:ProtocolLib:5.3.0")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

tasks {
    withType<JavaCompile> {
        options.encoding = "UTF-8"
        options.release.set(21)
    }
    
}