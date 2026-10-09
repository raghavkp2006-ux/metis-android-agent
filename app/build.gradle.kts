plugins {
    alias(libs.plugins.ksp)
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.detekt)
}

android {
    namespace = "dev.metis.agent"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.metis.agent"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    lint {
        abortOnError = true
        warningsAsErrors = true
        checkReleaseBuilds = true
        // Keep pinned, compatible versions; report upgrade availability without hiding it.
        informational.addAll(listOf("GradleDependency", "AndroidGradlePluginVersion"))
    }

    testOptions {
        unitTests.all {
            it.systemProperty("metis.projectDir", projectDir.absolutePath)
        }
    }
    sourceSets.getByName("androidTest").assets.srcDir("schemas")
}

ksp { arg("room.schemaLocation", "$projectDir/schemas") }

// Both variants export the same schema. Serialize writers when verified in one Gradle invocation.
tasks.matching { it.name == "kspReleaseKotlin" }.configureEach { mustRunAfter("kspDebugKotlin") }
// A newly exported schema must exist before instrumentation assets are copied on a clean build.
tasks.matching { it.name == "mergeDebugAndroidTestAssets" }.configureEach { dependsOn("kspDebugKotlin") }

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        allWarningsAsErrors.set(true)
    }
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(rootProject.files("config/detekt.yml"))
}

dependencies {
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)
    androidTestImplementation(libs.androidx.room.testing)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.ui.tooling.preview)

    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}
