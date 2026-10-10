import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import org.gradle.api.publish.maven.tasks.PublishToMavenRepository

plugins {
	kotlin("jvm") version "2.3.20"
	`maven-publish`
}

group = "io.github.team-sneakymouse"

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

val magicSpellsJar = file("libs/MagicSpells-4.0-Beta-18.jar").takeIf { it.exists() }
	?: error(
		"MagicSpells jar not found. Put MagicSpells-4.0-Beta-18.jar in libs/ " +
			"(CI downloads it before build).",
	)
val sneakyMannequinsJar = file("libs/SneakyMannequins-Plugin-1.0.0.jar")

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
	implementation("org.jetbrains.kotlin:kotlin-stdlib:2.3.20")
	compileOnly("io.papermc.paper:paper-api:26.2.build.121-stable")
	compileOnly("me.clip:placeholderapi:2.11.5")
	compileOnly(files(magicSpellsJar))
	sneakyMannequinsJar.takeIf { it.exists() }?.let { compileOnly(files(it)) }
	testImplementation(kotlin("test"))
	testCompileOnly("io.papermc.paper:paper-api:26.2.build.121-stable")
	testCompileOnly(files(magicSpellsJar))
	testRuntimeOnly("io.papermc.paper:paper-api:26.2.build.121-stable")
	testRuntimeOnly(files(magicSpellsJar))
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
			url = uri("https://maven.sneakyrp.com/releases")
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
