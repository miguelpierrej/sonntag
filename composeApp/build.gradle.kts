import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.composeHotReload)
    alias(libs.plugins.sqldelight)
}

configurations.configureEach {
    resolutionStrategy.force(
        "org.jetbrains.kotlinx:kotlinx-datetime:0.6.1",
        "org.jetbrains.kotlinx:kotlinx-datetime-jvm:0.6.1",
    )
}

val appName = "Sonntag"

/** Vem de gradle.properties; o CI sobrepoe com -PappVersion=<x.y.z> no release. */
val appVersion: String = providers.gradleProperty("appVersion").getOrElse("1.0.0")

/**
 * Fixo: e o que permite ao MSI atualizar a versao instalada no lugar de instalar
 * uma copia paralela. Nao mude entre releases.
 */
val appUpgradeUuid = "8f3c1d64-2c7a-4c0e-9a5b-6d1f7e2b9c34"

kotlin {
    jvm()

    androidTarget {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.materialIconsExtended)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.kotlinx.coroutinesCore)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.serializationJson)
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)
            implementation(libs.voyager.navigator)
            implementation(libs.voyager.screenmodel)
            implementation(libs.sqldelight.runtime)
            implementation(libs.sqldelight.coroutinesExtensions)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        androidMain.dependencies {
            implementation(compose.preview)
            implementation(libs.androidx.activityCompose)
            implementation(libs.sqldelight.androidDriver)
            // Port do PDFBox 2 para Android: o PDFBox oficial depende de java.awt.
            implementation(libs.pdfbox.android)
        }
        jvmMain.dependencies {
             implementation(compose.desktop.currentOs)
             implementation(libs.kotlinx.coroutinesSwing)
             implementation(libs.kotlinx.datetime)
             implementation(libs.sqldelight.jdbc.driver)
             implementation(libs.sqlite.jdbc)
             implementation(libs.hikari)
             implementation(libs.pdfbox)
         }
    }
}

android {
    namespace = "com.example.sonntag"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.sonntag"
        // 26 e o piso do java.util.Base64 e do java.time usados no pacote de dados.
        minSdk = 26
        targetSdk = 35
        // Derivado da versao: o Android so aceita instalar por cima quando este
        // numero cresce. 1.2.3 -> 10203.
        versionCode = appVersion.split(".").let { (maior, menor, correcao) ->
            maior.toInt() * 10000 + menor.toInt() * 100 + correcao.toInt()
        }
        versionName = appVersion
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources.excludes += setOf("META-INF/*")
    }
}

sqldelight {
    databases {
        create("SonntagDatabase") {
            packageName.set("com.example.sonntag.data.sqldelight")
        }
    }
}



compose.desktop {
    application {
        mainClass = "com.example.sonntag.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = appName
            packageVersion = appVersion

            windows {
                iconFile.set(project.file("icons/app-icon.ico"))
                // Atalho no menu Iniciar (dentro do grupo) e na area de trabalho.
                menu = true
                menuGroup = appName
                shortcut = true
                // Deixa o usuario escolher a pasta de instalacao.
                dirChooser = true
                upgradeUuid = appUpgradeUuid
            }
            linux {
                iconFile.set(project.file("icons/app-icon.png"))
            }
            macOS {
                iconFile.set(project.file("icons/app-icon.icns"))
            }
        }
    }
}

/**
 * MSI que pergunta ao usuario quais atalhos criar, via --win-shortcut-prompt do
 * jpackage — flag que o plugin Compose nao expoe no DSL `windows { }`.
 *
 * Roda somente no Windows (o jpackage nao gera instalador de outra plataforma).
 * Saida: build/compose/binaries/main/msi-prompt/.
 */
tasks.register<Exec>("packageMsiWithPrompt") {
    group = "compose desktop"
    description = "Gera o MSI perguntando quais atalhos criar (somente Windows)"
    dependsOn("createDistributable")

    val appImage = layout.buildDirectory.dir("compose/binaries/main/app/$appName").get().asFile
    val destination = layout.buildDirectory.dir("compose/binaries/main/msi-prompt").get().asFile
    val javaHome = providers.systemProperty("java.home").get()
    val isWindows = providers.systemProperty("os.name").get().startsWith("Windows")

    onlyIf {
        if (!isWindows) {
            logger.lifecycle("packageMsiWithPrompt: ignorado — o jpackage so gera MSI no Windows.")
        }
        isWindows
    }

    executable = File(javaHome, "bin/jpackage.exe").absolutePath
    args(
        "--type", "msi",
        "--app-image", appImage.absolutePath,
        "--name", appName,
        "--app-version", appVersion,
        "--dest", destination.absolutePath,
        "--win-menu",
        "--win-menu-group", appName,
        "--win-shortcut",
        "--win-shortcut-prompt",
        "--win-dir-chooser",
        "--win-upgrade-uuid", appUpgradeUuid,
    )
}

/** Regera icons/app-icon.png a partir de AppIcon.kt (fonte unica do desenho). */
tasks.register<JavaExec>("exportAppIcon") {
    group = "build"
    description = "Exporta o PNG mestre do icone do app"
    val jvmMain = kotlin.jvm().compilations.getByName("main")
    dependsOn(jvmMain.compileTaskProvider)
    classpath = jvmMain.output.allOutputs + jvmMain.runtimeDependencyFiles
    mainClass.set("com.example.sonntag.tools.ExportAppIconKt")
    args(project.file("icons").absolutePath)
}

tasks.register<JavaExec>("renderScreens") {
    val jvmMain = kotlin.jvm().compilations.getByName("main")
    dependsOn(jvmMain.compileTaskProvider)
    classpath = jvmMain.output.allOutputs + jvmMain.runtimeDependencyFiles
    mainClass.set("com.example.sonntag.tools.RenderScreensKt")
    args(System.getenv("OUT_DIR") ?: "/tmp/screens", System.getenv("SCREEN") ?: "painel")
}
