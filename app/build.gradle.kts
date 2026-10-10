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

// Development-only baseline export calls the production grammar without an Android device.
tasks.register<JavaExec>("phase15RulesSnapshot") {
    group = "verification"
    description = "Export the actual EnglishRules predictions for the Phase 15 synthetic evaluation."
    dependsOn("compileDebugUnitTestKotlin")
    classpath = files(tasks.named<Test>("testDebugUnitTest").map { it.classpath })
    mainClass.set("dev.metis.agent.evaluation.Phase15RulesSnapshot")
    args(rootProject.file("docs/evaluation/phase15/rules-input-v2.tsv").absolutePath,
        layout.buildDirectory.file("reports/phase15/rules-v2.tsv").get().asFile.absolutePath,
        rootProject.projectDir.absolutePath)
}

tasks.register<JavaExec>("phase15ReadRulesSnapshot") {
    group = "verification"
    description = "Export production rules for the read-query v3 development candidate."
    dependsOn("compileDebugUnitTestKotlin")
    classpath = files(tasks.named<Test>("testDebugUnitTest").map { it.classpath })
    mainClass.set("dev.metis.agent.evaluation.Phase15RulesSnapshot")
    args(rootProject.file(providers.gradleProperty("phase15ReadInput").getOrElse(
        "docs/evaluation/phase15/read_queries_v3/rules-input-v3.tsv")).absolutePath,
        rootProject.file(providers.gradleProperty("phase15ReadOutput").getOrElse(
            "app/build/reports/phase15/rules-v3.tsv")).absolutePath,
        rootProject.projectDir.absolutePath)
}

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
