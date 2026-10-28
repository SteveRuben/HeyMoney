import java.util.Properties

plugins {
    id("com.android.application")
    id("kotlin-android")
    id("org.jetbrains.kotlin.android")
    org.jetbrains.kotlin.plugin.compose
    id("dagger.hilt.android.plugin")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
    id("com.google.gms.google-services")
    id("com.google.firebase.crashlytics")
    id("io.gitlab.arturbosch.detekt")
}

android {
    namespace = "com.ivy.wallet"
    compileSdk = libs.versions.target.sdk.get().toInt()

    defaultConfig {
        applicationId = "org.abodah.heymoney"
        minSdk = libs.versions.min.sdk.get().toInt()
        targetSdk = libs.versions.target.sdk.get().toInt()
        versionName = libs.versions.version.name.get()
        versionCode = libs.versions.version.code.get().toInt()
    }

    androidResources {
        generateLocaleConfig = true
    }

    signingConfigs {
        getByName("debug") {
            storeFile = file("../debug.jks")
            storePassword = "IVY7834!DEbug"
            keyAlias = "debug"
            keyPassword = "IVY7834!DEbug"
        }

        create("release") {
            // keystore.properties (local, git-ignored) wins, then the CI environment variables
            val keystoreProps = Properties().apply {
                val file = rootProject.file("keystore.properties")
                if (file.exists()) file.inputStream().use { load(it) }
            }
            storeFile = file(keystoreProps.getProperty("storeFile") ?: "../sign.jks")
            storePassword = keystoreProps.getProperty("storePassword") ?: System.getenv("SIGNING_STORE_PASSWORD")
            keyAlias = keystoreProps.getProperty("keyAlias") ?: System.getenv("SIGNING_KEY_ALIAS")
            keyPassword = keystoreProps.getProperty("keyPassword") ?: System.getenv("SIGNING_KEY_PASSWORD")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )

            isDebuggable = false
            isDefault = false

            signingConfig = signingConfigs.getByName("release")

            resValue("string", "app_name", "HeyMoney")
        }

        debug {
            isMinifyEnabled = false
            isShrinkResources = false

            isDebuggable = true
            isDefault = true

            signingConfig = signingConfigs.getByName("debug")

            applicationIdSuffix = ".debug"
            resValue("string", "app_name", "HeyMoney Debug")
        }

        create("demo") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )

            matchingFallbacks.add("release")

            isDebuggable = false
            isDefault = false

            signingConfig = signingConfigs.getByName("debug")

            applicationIdSuffix = ".debug"
            resValue("string", "app_name", "HeyMoney")
        }
    }

    packaging {
        // the Anthropic SDK pulls several jars that ship the same metadata files
        resources {
            excludes += setOf(
                "META-INF/DEPENDENCIES",
                "META-INF/INDEX.LIST",
                "META-INF/LICENSE*",
                "META-INF/NOTICE*",
                "META-INF/io.netty.versions.properties"
            )
        }
    }

    val javaVersion = libs.versions.jvm.target.get()
    kotlinOptions {
        jvmTarget = javaVersion
    }

    compileOptions {
        sourceCompatibility = JavaVersion.valueOf("VERSION_$javaVersion")
        targetCompatibility = JavaVersion.valueOf("VERSION_$javaVersion")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    lint {
        disable += "ComposeViewModelInjection"
        checkDependencies = true
        abortOnError = false
        checkReleaseBuilds = false
        htmlReport = true
        htmlOutput = file("${project.rootDir}/build/reports/lint/lint.html")
        xmlReport = true
        xmlOutput = file("${project.rootDir}/build/reports/lint/lint.xml")
        baseline = file("lint-baseline.xml")
    }
}

dependencies {
    implementation(projects.feature.attributions)
    implementation(projects.feature.balance)
    implementation(projects.feature.budgets)
    implementation(projects.feature.categories)
    implementation(projects.feature.contributors)
    implementation(projects.feature.disclaimer)
    implementation(projects.feature.editTransaction)
    implementation(projects.feature.exchangeRates)
    implementation(projects.feature.features)
    implementation(projects.feature.home)
    implementation(projects.feature.importData)
    implementation(projects.feature.loans)
    implementation(projects.feature.main)
    implementation(projects.feature.onboarding)
    implementation(projects.feature.piechart)
    implementation(projects.feature.plannedPayments)
    implementation(projects.feature.poll.impl)
    implementation(projects.feature.poll.public)
    implementation(projects.feature.releases)
    implementation(projects.feature.reports)
    implementation(projects.feature.search)
    implementation(projects.feature.settings)
    implementation(projects.feature.transactions)
    implementation(projects.feature.poll.impl)
    implementation(projects.shared.base)
    implementation(projects.shared.data.core)
    implementation(projects.shared.domain)
    implementation(projects.shared.ui.core)
    implementation(projects.shared.ui.navigation)
    implementation(projects.temp.legacyCode)
    implementation(projects.temp.oldDesign)
    implementation(projects.widget.addTransaction)
    implementation(projects.widget.balance)

    implementation(libs.bundles.kotlin)
    implementation(libs.bundles.kotlin.android)
    implementation(libs.bundles.ktor)
    implementation(libs.bundles.arrow)
    implementation(libs.bundles.compose)
    implementation(libs.bundles.activity)
    implementation(libs.bundles.google)
    implementation(libs.bundles.firebase)
    implementation(libs.datastore)
    implementation(libs.androidx.security)
    implementation(libs.androidx.biometrics)

    implementation(libs.bundles.hilt)
    implementation(libs.material)
    ksp(libs.hilt.compiler)

    implementation(libs.bundles.room)
    ksp(libs.room.compiler)

    implementation(libs.timber)
    implementation("com.google.zxing:core:3.5.3")
    implementation("com.google.mlkit:text-recognition:16.0.1")
    implementation("androidx.camera:camera-camera2:1.3.4")
    implementation("androidx.camera:camera-lifecycle:1.3.4")
    implementation("androidx.camera:camera-view:1.3.4")
    implementation("com.google.guava:guava:33.3.1-android")
    implementation("com.anthropic:anthropic-java:2.34.0")
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
    implementation(libs.keval)
    implementation(libs.bundles.opencsv)
    implementation(libs.androidx.work)
    implementation(libs.androidx.recyclerview)

    testImplementation(libs.bundles.testing)
    testImplementation("org.json:json:20240303")
    testImplementation(libs.androidx.work.testing)

    lintChecks(libs.slack.lint.compose)
}
