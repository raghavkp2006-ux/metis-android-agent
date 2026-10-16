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
        testInstrumentationRunner = "dev.metis.agent.testing.IsolatedAndroidJUnitRunner"
        buildConfigField("boolean", "IS_CAPABILITY_TEST", "false")
    }

    buildTypes {
        create("capabilityTest") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".capabilitytest"
            versionNameSuffix = "-synthetic-capability-test"
            matchingFallbacks += "debug"
            buildConfigField("boolean", "IS_CAPABILITY_TEST", "true")
        }
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
        buildConfig = true
    }

    // Production variants cannot be instrumentation targets.
    testBuildType = "capabilityTest"
    sourceSets.getByName("capabilityTest") {
        java.srcDir("src/debug/java")
        manifest.srcFile("src/debug/AndroidManifest.xml")
        res.srcDir("src/capabilityTest/res")
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
            // Native regression certificates bind source bytes, including comment-only edits.
            it.inputs.files(fileTree("src/main/java") { include("**/*.kt") },
                rootProject.file("docs/evaluation/phase15/candidate_v15/regression.tsv"))
            it.inputs.file(rootProject.file("docs/evaluation/phase15/read_candidate_v16/regression.tsv"))
        }
    }
    sourceSets.getByName("androidTest").assets.srcDir("schemas")
}

ksp { arg("room.schemaLocation", "$projectDir/schemas") }

// Both variants export the same schema. Serialize writers when verified in one Gradle invocation.
tasks.matching { it.name == "kspReleaseKotlin" }.configureEach { mustRunAfter("kspDebugKotlin", "kspCapabilityTestKotlin") }
tasks.matching { it.name == "kspCapabilityTestKotlin" }.configureEach { mustRunAfter("kspDebugKotlin") }
// A newly exported schema must exist before instrumentation assets are copied on a clean build.
tasks.matching { it.name == "mergeCapabilityTestAndroidTestAssets" }.configureEach {
    dependsOn("kspCapabilityTestKotlin")
}

val verifyCapabilityTestApks by tasks.registering(Exec::class) {
    group = "verification"
    description = "Fail closed on the final APK identities before any device installation."
    dependsOn("assembleCapabilityTest", "assembleCapabilityTestAndroidTest")
    val python = providers.gradleProperty("capabilityPython").getOrElse("python")
    commandLine(python, rootProject.file("tools/capability_testing/harness.py"), "verify",
        "--sdk", android.sdkDirectory.absolutePath,
        "--app", layout.buildDirectory.file("outputs/apk/capabilityTest/app-capabilityTest.apk").get().asFile,
        "--test", layout.buildDirectory.file("outputs/apk/androidTest/capabilityTest/app-capabilityTest-androidTest.apk")
            .get().asFile,
        "--output", layout.buildDirectory.file("reports/capability-testing/apk-verification.json").get().asFile)
}

tasks.configureEach {
    val deviceTask = name.startsWith("connected") || name.startsWith("install") || name.startsWith("uninstall") ||
        name.endsWith("AndroidTest") && (name.startsWith("managed") || name.startsWith("device"))
    if (deviceTask) {
        if (name.contains("CapabilityTest")) {
            // Device writes are owned by the manifest-checking host harness, never AGP cleanup.
            doFirst { error("Use tools/capability_testing/harness.py; direct Gradle device tasks are disabled.") }
        } else {
            doFirst { error("REFUSED: production METIS must never be an instrumentation/install/uninstall target.") }
        }
    }
}

// Legacy build-only command remains safe; no legacy connected/install alias is provided.
tasks.register("assembleDebugAndroidTest") { dependsOn("assembleCapabilityTestAndroidTest") }

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        allWarningsAsErrors.set(true)
    }
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(rootProject.files("config/detekt.yml"))
    // Existing V17 guard/style findings only; preserve validator bytes during retirement.
    baseline = rootProject.file("config/detekt-legacy-retirement-baseline.xml")
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
    add("capabilityTestImplementation", libs.androidx.compose.ui.tooling)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}
