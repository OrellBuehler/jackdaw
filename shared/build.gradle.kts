import java.io.File

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.sqldelight)
    alias(libs.plugins.skie)
    alias(libs.plugins.kover)
}

abstract class GenerateFormatResources : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val examplesDir: DirectoryProperty

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val compatDir: DirectoryProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val vectorsFile: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val promptFile: RegularFileProperty

    @get:OutputDirectory
    abstract val mainOutput: DirectoryProperty

    @get:OutputDirectory
    abstract val testOutput: DirectoryProperty

    @TaskAction
    fun generate() {
        val samples =
            examplesDir
                .get()
                .asFile
                .listFiles { f -> f.name.endsWith(".jackdaw") }!!
                .sortedBy { it.name }
        val main = File(mainOutput.get().asFile, "ch/orellbuehler/jackdaw/samples/GeneratedSamples.kt")
        main.parentFile.mkdirs()
        main.writeText(
            buildString {
                appendLine("// Generated from format/examples by the generateFormatResources Gradle task. Do not edit.")
                appendLine("package ch.orellbuehler.jackdaw.samples")
                appendLine()
                appendLine("internal val GENERATED_SAMPLE_PACKS: List<Pair<String, String>> =")
                appendLine("    listOf(")
                samples.forEach { appendLine("        \"${it.name}\" to ${kotlinLiteral(it.readText())},") }
                appendLine("    )")
            },
        )
        val promptSource = promptFile.get().asFile
        val prompt = promptSource.readText().trim()
        val authoring = File(mainOutput.get().asFile, "ch/orellbuehler/jackdaw/GeneratedAuthoring.kt")
        authoring.writeText(
            buildString {
                appendLine("// Generated from format/prompt.txt by the generateFormatResources Gradle task. Do not edit.")
                appendLine("package ch.orellbuehler.jackdaw")
                appendLine()
                appendLine("internal val GENERATED_AUTHORING_PROMPT: String = ${kotlinLiteral(prompt)}")
            },
        )
        val test = File(testOutput.get().asFile, "ch/orellbuehler/jackdaw/format/GeneratedVectors.kt")
        test.parentFile.mkdirs()
        test.writeText(
            buildString {
                appendLine("// Generated from format/test-vectors.json by the generateFormatResources Gradle task. Do not edit.")
                appendLine("package ch.orellbuehler.jackdaw.format")
                appendLine()
                appendLine("internal val GENERATED_TEST_VECTORS: String = ${kotlinLiteral(vectorsFile.get().asFile.readText())}")
            },
        )
        val frozen = compatDir.get().asFile
        File(testOutput.get().asFile, "ch/orellbuehler/jackdaw/format/GeneratedCompat.kt").writeText(
            buildString {
                appendLine("// Generated from format/compat by the generateFormatResources Gradle task. Do not edit.")
                appendLine("package ch.orellbuehler.jackdaw.format")
                appendLine()
                appendLine("internal val GENERATED_COMPAT_MANIFEST: String = ${kotlinLiteral(File(frozen, "manifest.json").readText())}")
                appendLine()
                appendLine("internal val GENERATED_COMPAT_PACKS: Map<String, String> =")
                appendLine("    mapOf(")
                frozen.listFiles { f -> f.name.endsWith(".jackdaw") }!!.sortedBy { it.name }.forEach {
                    appendLine("        \"${it.name}\" to ${kotlinLiteral(it.readText())},")
                }
                appendLine("    )")
            },
        )
    }

    private fun kotlinLiteral(text: String): String {
        val out = StringBuilder("\"")
        for (c in text) {
            when {
                c == '\\' -> out.append("\\\\")
                c == '"' -> out.append("\\\"")
                c == '$' -> out.append("\\$")
                c == '\n' -> out.append("\\n")
                c == '\r' -> out.append("\\r")
                c == '\t' -> out.append("\\t")
                c.code < 0x20 || c.code > 0x7e -> out.append("\\u").append(c.code.toString(16).padStart(4, '0'))
                else -> out.append(c)
            }
        }
        return out.append('"').toString()
    }
}

val generateFormatResources =
    tasks.register<GenerateFormatResources>("generateFormatResources") {
        examplesDir.set(rootProject.layout.projectDirectory.dir("format/examples"))
        compatDir.set(rootProject.layout.projectDirectory.dir("format/compat"))
        vectorsFile.set(rootProject.layout.projectDirectory.file("format/test-vectors.json"))
        promptFile.set(rootProject.layout.projectDirectory.file("format/prompt.txt"))
        mainOutput.set(layout.buildDirectory.dir("generated/formatResources/commonMain/kotlin"))
        testOutput.set(layout.buildDirectory.dir("generated/formatResources/commonTest/kotlin"))
    }

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach {
        it.binaries.framework {
            baseName = "shared"
            isStatic = true
        }
    }

    sourceSets {
        commonMain {
            kotlin.srcDir(generateFormatResources.flatMap { it.mainOutput })
        }
        commonTest {
            kotlin.srcDir(generateFormatResources.flatMap { it.testOutput })
        }
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)
            implementation(libs.sqldelight.coroutines)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.turbine)
        }
        androidMain.dependencies {
            implementation(libs.sqldelight.android)
            implementation(libs.kotlinx.coroutines.android)
        }
        iosMain.dependencies {
            implementation(libs.sqldelight.native)
        }
        iosTest.dependencies {
            implementation(libs.sqldelight.native)
        }
        val androidUnitTest by getting {
            dependencies {
                implementation(libs.sqldelight.jvm)
            }
        }
    }
}

android {
    namespace = "ch.orellbuehler.jackdaw.shared"
    compileSdk = 37
    defaultConfig {
        minSdk = 26
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

skie {
    // SKIE uploads anonymized build analytics by default; disable both the
    // capture and upload phases so CI/dev framework builds make no network
    // calls and leak no project metadata.
    analytics {
        enabled.set(false)
    }
}

sqldelight {
    databases {
        create("JackdawDatabase") {
            packageName.set("ch.orellbuehler.jackdaw.db")
            srcDirs.setFrom("src/commonMain/sqldelight")
            schemaOutputDirectory.set(file("src/commonMain/sqldelight/databases"))
            verifyMigrations.set(true)
        }
    }
}
