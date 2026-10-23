import com.android.build.api.artifact.SingleArtifact
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.room)
}

/**
 * Release signing comes from keystore.properties at the repository root (git-ignored), which
 * points to a keystore stored outside the repository. Without it, release builds are unsigned.
 * See README.md, "Release signing".
 */
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.isFile) file.inputStream().use(::load)
}

android {
    namespace = "io.github.imlemos.clock"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.imlemos.clock"
        minSdk = 31
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (keystoreProperties.isNotEmpty()) {
            create("release") {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    lint {
        abortOnError = true
        warningsAsErrors = true
        checkDependencies = true
        disable += setOf(
            // targetSdk 36 is deliberate: the reference device runs Android 16 (docs/PLANO.md).
            "OldTargetApi",
            // Version bumps are done on purpose through gradle/libs.versions.toml, not by CI failing.
            "GradleDependency",
            "AndroidGradlePluginVersion",
            "NewerVersionAvailable",
        )
    }

    androidResources {
        // Feeds the per-app language picker (Android 13+) from the values-* folders.
        generateLocaleConfig = true
    }

    sourceSets {
        // Exported Room schemas double as test assets for MigrationTestHelper.
        named("androidTest") {
            assets.directories += "$projectDir/schemas"
        }
    }
}

/**
 * Fails the build if the merged manifest of any variant requests a forbidden permission, e.g. one
 * added silently by a dependency. The app must never reach the network or shared storage.
 */
abstract class VerifyPermissionsTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val mergedManifest: RegularFileProperty

    @get:Input
    abstract val forbidden: ListProperty<String>

    @get:OutputFile
    abstract val report: RegularFileProperty

    @TaskAction
    fun verify() {
        val manifest = mergedManifest.get().asFile.readText()
        val found = forbidden.get().filter { manifest.contains("\"$it\"") }
        check(found.isEmpty()) { "Forbidden permissions in merged manifest: $found" }
        report.get().asFile.writeText("OK\n")
    }
}

val forbiddenPermissions = listOf(
    "android.permission.INTERNET",
    "android.permission.READ_EXTERNAL_STORAGE",
    "android.permission.WRITE_EXTERNAL_STORAGE",
    "android.permission.MANAGE_EXTERNAL_STORAGE",
    "android.permission.READ_MEDIA_IMAGES",
    "android.permission.READ_MEDIA_VIDEO",
    "android.permission.READ_MEDIA_AUDIO",
)

androidComponents {
    onVariants { variant ->
        val name = variant.name.replaceFirstChar(Char::titlecase)
        val verify = tasks.register<VerifyPermissionsTask>("verify${name}Permissions") {
            mergedManifest.set(variant.artifacts.get(SingleArtifact.MERGED_MANIFEST))
            forbidden.set(forbiddenPermissions)
            report.set(layout.buildDirectory.file("reports/permissions/${variant.name}.txt"))
        }
        tasks.named("check") { dependsOn(verify) }
    }
}

room3 {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(project(":core:scheduling"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)

    implementation(libs.haze)
    implementation(libs.haze.blur)
    implementation(libs.haze.glass)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    implementation(platform(libs.kotlinx.serialization.bom))
    implementation(libs.kotlinx.serialization.core)

    testImplementation(libs.junit4)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}
