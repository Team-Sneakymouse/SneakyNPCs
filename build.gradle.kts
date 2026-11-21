import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import org.gradle.api.publish.maven.tasks.PublishToMavenRepository

plugins {
	kotlin("jvm") version "2.3.20"
	`maven-publish`
}

group = "io.github.team-sneakymouse"

val sneakyVariablesVersion = "2026.11.17-8fcaabc393e3"

version =
	providers.exec {
		workingDir(rootDir)
		commandLine("git", "show", "-s", "--format=%ct:%h", "--abbrev=12", "HEAD")
	}.standardOutput.asText.map { commit ->
		val (timestamp, hash) = commit.trim().split(":", limit = 2)
		val date =
			DateTimeFormatter.ofPattern("yyyy.MM.dd").withZone(ZoneOffset.UTC)
				.format(Instant.ofEpochSecond(timestamp.toLong()))
		"$date-$hash"
	}.get()

repositories {
	maven {
		url = uri("https://plugins.gradle.org/m2/")
	}
	maven {
		url = uri("https://repo.extendedclip.com/content/repositories/placeholderapi/")
	}
	mavenCentral()
	maven("https://repo.papermc.io/repository/maven-public/")
	maven("https://maven.sneakyrp.com/sneakymouse")
	maven("https://maven.playpro.com")
	maven("https://maven.enginehub.org/repo/")
}

dependencies {
	implementation("org.jetbrains.kotlin:kotlin-stdlib:2.3.20")
	compileOnly("io.papermc.paper:paper-api:26.2.build.121-stable")
	compileOnly("me.clip:placeholderapi:2.11.5")
	// NPC Screens 3: hides the player's inventory under the hub art by packets (same as SneakyRoamin)
	compileOnly("io.netty:netty-transport:4.2.15.Final")
	compileOnly("net.coreprotect:coreprotect:22.4") { isTransitive = false }
	compileOnly("io.github.team-sneakymouse:magicspells:2026.11.08-eca39854a666") {
		isTransitive = false
	}
	compileOnly("io.github.team-sneakymouse:sneakyitems:2026.11.20-b1b03c4a324f")
	compileOnly("io.github.team-sneakymouse:sneakyutils:2026.11.10-7e1262ebd376")
	compileOnly("io.github.team-sneakymouse:sneakyvariables:$sneakyVariablesVersion")
	// Bank: right-clicking a note in the bank region deposits it; optional at runtime
	compileOnly("com.sk89q.worldguard:worldguard-bukkit:7.0.18") { isTransitive = false }
	compileOnly("com.sk89q.worldguard:worldguard-core:7.0.18") { isTransitive = false }
	compileOnly("com.sk89q.worldedit:worldedit-core:7.4.0") { isTransitive = false }
	compileOnly("com.sk89q.worldedit:worldedit-bukkit:7.4.0") { isTransitive = false }
	testImplementation(kotlin("test"))
	testCompileOnly("io.papermc.paper:paper-api:26.2.build.121-stable")
	testCompileOnly("io.github.team-sneakymouse:magicspells:2026.11.08-eca39854a666") {
		isTransitive = false
	}
	testCompileOnly("io.github.team-sneakymouse:sneakyitems:2026.11.20-b1b03c4a324f")
	testCompileOnly("io.github.team-sneakymouse:sneakyvariables:$sneakyVariablesVersion")
	testRuntimeOnly("io.papermc.paper:paper-api:26.2.build.121-stable")
	testRuntimeOnly("io.github.team-sneakymouse:magicspells:2026.11.08-eca39854a666") {
		isTransitive = false
	}
	testRuntimeOnly("io.github.team-sneakymouse:sneakyitems:2026.11.20-b1b03c4a324f")
}

tasks.processResources {
	inputs.property("version", project.version.toString())
	filesMatching("paper-plugin.yml") {
		expand("version" to project.version.toString())
	}
}

tasks.jar {
	archiveBaseName.set(rootProject.name)
	archiveFileName.set("${rootProject.name}-${project.version}.jar")
	manifest {
		attributes["Main-Class"] = "com.danidipp.sneakynpcs.SneakyNPCs"
	}

	from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
	duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.test {
	useJUnitPlatform()
}

configure<JavaPluginExtension> {
	toolchain.languageVersion = JavaLanguageVersion.of(25)
	sourceSets {
		main {
			java.srcDir("src/main/kotlin")
			resources.srcDir(file("src/resources"))
		}
	}
}

publishing {
	publications {
		create<MavenPublication>("maven") {
			artifactId = rootProject.name
			artifact(tasks.jar) {
				classifier = null
			}
			pom {
				name.set("SneakyNPCs")
				description.set("Paper plugin for managing NPC GUIs.")
				url.set("https://github.com/Team-Sneakymouse/SneakyNPCs")
				properties.put("sneakyrp.deployment.type", "paper-plugin")
				scm {
					url.set("https://github.com/Team-Sneakymouse/SneakyNPCs")
					connection.set("scm:git:https://github.com/Team-Sneakymouse/SneakyNPCs.git")
				}
			}
		}
	}
	repositories {
		maven {
			name = "sneakyrp"
			url = uri("https://maven.sneakyrp.com/sneakymouse")
			credentials(PasswordCredentials::class)
			authentication {
				create<org.gradle.authentication.http.BasicAuthentication>("basic")
			}
		}
	}
}

tasks.withType<PublishToMavenRepository>().configureEach {
	dependsOn(tasks.check)
}
