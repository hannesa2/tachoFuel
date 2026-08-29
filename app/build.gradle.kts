import java.io.FileInputStream
import java.util.Properties
import kotlin.text.ifEmpty
import info.git.versionHelper.colored
import info.git.versionHelper.getGitCommitCount
import info.git.versionHelper.getGitOriginRemote
import info.git.versionHelper.getLatestGitHash
import info.git.versionHelper.getSHA1
import info.git.versionHelper.getVersionText
import info.git.versionHelper.println
import info.shell.getDate

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    id("com.google.gms.google-services")
}

val patKey = "GITHUB_PERSONAL_ACCESS_TOKEN"
var patValue = "undefined"
val keystorePropertiesFile = rootProject.file("signing/keystore.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(FileInputStream(keystorePropertiesFile))
    patValue = keystoreProperties.getProperty(patKey).ifEmpty { "abc" }
} else {
    colored {
        println("ERROR: keystore.properties is missing. Please run ./signing/decrypt.sh".red.bold)
        throw Exception("keystore.properties is missing")
    }
}

android {
    namespace = "com.example.vespatacho"
    compileSdk = 37

    defaultConfig {
        applicationId = "info.hannes.vespatacho"
        minSdk = 26
        versionCode = "${getGitCommitCount()}".toInt()
        versionName = "${getVersionText()}.$versionCode-${getLatestGitHash()}"
        println { "versionName=${versionName.green.bold} versionCode=${versionCode.green.bold}" }

        buildConfigField("String", "BASE_URL", "\"https://abcdomain.co/xyz/\"")
        buildConfigField("String", "BUILD_DATE", "\"" + getDate() + "\"")
        buildConfigField("String", "GIT_REPOSITORY", "\"" + getGitOriginRemote() + "\"")
        buildConfigField("String", "PAT", "\"" + patValue + "\"")
        buildConfigField("String", "SHA1", "\"" + getSHA1() + "\"")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    signingConfigs {
        register("debugCI") {
            storeFile = file("../signing/debug.keystore")
            storePassword = "android"
            keyPassword = "android"
            keyAlias = "androiddebugkey"
        }
        register("release") {
            storeFile = file("../signing/release.keystore")
            storePassword = keystoreProperties["storePassword"] as String
            keyAlias = keystoreProperties["keyAlias"] as String
            keyPassword = keystoreProperties["keyPassword"] as String
        }
    }
    buildTypes {
        debug {
            if (System.getenv("CI") == "true") { // Github action
                println("I run on Github and use for 'debug' the RELEASE signing")
                signingConfig = signingConfigs.findByName("release")
            }
        }
        release {
            signingConfig = signingConfigs.findByName("release")
            if (System.getenv("CI_SERVER") != null) { // gitlab
                println("I run on Gitlab and use RELEASE signing")
                signingConfig = signingConfigs.findByName("release")
            } else if (System.getenv("CI") == "true") { // Github
                println("I run on Github and use RELEASE signing")
                signingConfig = signingConfigs.findByName("release")
            } else if (file("../signing/release.keystore").exists()) {
                println("I use RELEASE signing")
                signingConfig = signingConfigs.findByName("release")
            } else {
                println("I run somewhere else and I use debug signing")
                signingConfig = signingConfigs.findByName("debugCI")
            }
            isMinifyEnabled = false
            proguardFiles.addAll(
                listOf(
                    getDefaultProguardFile("proguard-android-optimize.txt"),
                    file("proguard-rules.pro"),
                ),
            )
        }
    }
}

configurations.all {
    resolutionStrategy.force("org.jetbrains.kotlin:kotlin-stdlib:2.3.20")
    resolutionStrategy.force("org.jetbrains.kotlin:kotlin-stdlib-jdk7:2.3.20")
    resolutionStrategy.force("org.jetbrains.kotlin:kotlin-stdlib-jdk8:2.3.20")
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.08.00")
    implementation(composeBom)
    implementation("com.github.AppDevNext.Logcat:LogcatCoreLib:3.6")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // CameraX
    implementation("androidx.camera:camera-camera2:1.3.3")
    implementation("androidx.camera:camera-lifecycle:1.3.3")
    implementation("androidx.camera:camera-view:1.3.3")

    // ML Kit — on-device text recognition (no network needed)
    implementation("com.google.mlkit:text-recognition:16.0.1")

    // Room
    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    ksp("androidx.room:room-compiler:2.8.4")

    debugImplementation("androidx.compose.ui:ui-tooling")

    // AndroidChart line chart
    implementation("com.github.AppDevNext.AndroidChart:chartLib:5.2.4")

    // GitHub in-app update checker
    implementation("com.github.hannesa2:githubAppUpdate:2.4")

    // Firebase
    implementation(platform("com.google.firebase:firebase-bom:34.18.0"))
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-storage")
}
