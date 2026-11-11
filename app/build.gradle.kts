plugins {
    id("com.android.application")
    kotlin("android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.neulbom.drive"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.neulbom.drive"
        minSdk = 29
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0-mvp"
    }

    // 차량 연동 방식별 빌드 플래버. mock: SDK 없이 실행 가능한 데모, pleos: 공식 SDK 검증 후 연동.
    flavorDimensions += "vehicle"
    productFlavors {
        create("mock") {
            dimension = "vehicle"
            applicationIdSuffix = ".mock"
            versionNameSuffix = "-mock"
            buildConfigField("boolean", "DEMO_TOOLS_ENABLED", "true")
            // 에뮬레이터에서 개발 PC의 시연 서버(:server, 포트 8080)
            buildConfigField("String", "SERVER_BASE_URL", "\"http://10.0.2.2:8080\"")
        }
        create("pleos") {
            dimension = "vehicle"
            buildConfigField("boolean", "DEMO_TOOLS_ENABLED", "false")
            // 운영 서버 확정 전에는 업로드하지 않는다.
            buildConfigField("String", "SERVER_BASE_URL", "\"\"")
        }
    }

    buildTypes {
        debug {
            // Visual Layout Studio(개발 PC, 포트 8095) Hot Reload. mock 플래버에서만 켠다(LayoutStore).
            buildConfigField("String", "LAYOUT_STUDIO_URL", "\"http://10.0.2.2:8095\"")
        }
        release {
            buildConfigField("String", "LAYOUT_STUDIO_URL", "\"\"")
            isMinifyEnabled = false
            // 데모 배포용: 디버그 키로 서명(공모전 제출 시 정식 키로 교체).
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

dependencies {
    implementation(project(":core"))
    implementation(project(":vehicle:api"))
    implementation(project(":domain:consent"))
    implementation(project(":domain:cognitive"))
    implementation(project(":domain:scheduler"))
    implementation(project(":data:local"))
    implementation(project(":data:sync"))
    implementation(project(":domain:driving"))
    implementation(project(":presentation"))
    "mockImplementation"(project(":vehicle:mock"))
    "pleosImplementation"(project(":vehicle:pleos"))

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.navigation.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}

// Compose UI 테스트용 매니페스트(ui-test-manifest)는 debug에만 붙으므로 단위 테스트는 debug 변형에서만 실행한다.
androidComponents {
    beforeVariants(selector().withBuildType("release")) { variant ->
        variant.enableUnitTest = false
    }
}
