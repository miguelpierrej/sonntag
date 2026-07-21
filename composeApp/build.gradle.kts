import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeHotReload)
    alias(libs.plugins.sqldelight)
}

configurations.configureEach {
    resolutionStrategy.force(
        "org.jetbrains.kotlinx:kotlinx-datetime:0.6.1",
        "org.jetbrains.kotlinx:kotlinx-datetime-jvm:0.6.1",
    )
}

kotlin {
    jvm()

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
            packageName = "com.example.sonntag"
            packageVersion = "1.0.0"
        }
    }
}

