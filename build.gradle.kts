plugins {
	kotlin("jvm") version "2.2.20"
}

repositories {
	maven {
		url = uri("https://plugins.gradle.org/m2/")
	}
	maven {
		url = uri("https://repo.extendedclip.com/content/repositories/placeholderapi/")
	}
	mavenCentral()
	maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
	implementation("org.jetbrains.kotlin:kotlin-stdlib:2.2.20")
    compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
 	compileOnly("me.clip:placeholderapi:2.11.5")
	compileOnly(files("libs/MagicSpells-4.0-Beta-13.jar"))
	testImplementation(kotlin("test"))
	testCompileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
	testCompileOnly(files("libs/MagicSpells-4.0-Beta-13.jar"))
	testRuntimeOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
	testRuntimeOnly(files("libs/MagicSpells-4.0-Beta-13.jar"))
}

tasks.jar {
	manifest {
		attributes["Main-Class"] = "com.danidipp.sneakynpcs.SneakyNPCs"
	}

	from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
}

tasks.test {
	useJUnitPlatform()
}

configure<JavaPluginExtension> {
	sourceSets {
		main {
			java.srcDir("src/main/kotlin")
			resources.srcDir(file("src/resources"))
		}
	}
}
