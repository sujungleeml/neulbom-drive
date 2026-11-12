import java.util.Properties

plugins {
    id("com.android.application")
    kotlin("android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// 릴리즈 서명: 저장소 밖 속성 파일(storeFile, keyAlias)과 환경 변수(비밀번호)가 모두 있을 때만 쓴다.
// 없으면 디버그 키로 서명한다(CI, 다른 PC에서도 빌드되도록). 키와 비밀번호는 저장소에 두지 않는다.
val releaseSigningProps: Properties? = run {
    val path = System.getenv("NEULBOM_RELEASE_SIGNING")
        ?: "${System.getProperty("user.home")}/.neulbom-keys/release-signing.properties"
    val f = file(path)
    val storePw = System.getenv("NEULBOM_KEYSTORE_PASSWORD")
    if (!f.isFile || storePw.isNullOrEmpty()) null
    else Properties().apply { f.inputStream().use { load(it) } }
}

android {
    namespace = "com.neulbom.drive"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.neulbom.drive"
        minSdk = 29
        targetSdk = 34
        versionCode = 2
        // 공모전 시연용 빌드(정식 출시본 아님).
        versionName = "1.0.0-contest"
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

    signingConfigs {
        releaseSigningProps?.let { p ->
            create("release") {
                storeFile = file(p.getProperty("storeFile"))
                keyAlias = p.getProperty("keyAlias")
                storePassword = System.getenv("NEULBOM_KEYSTORE_PASSWORD")
                keyPassword = System.getenv("NEULBOM_KEY_PASSWORD") ?: System.getenv("NEULBOM_KEYSTORE_PASSWORD")
            }
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
            // 릴리즈 키가 준비된 PC에서만 릴리즈 키, 아니면 디버그 키(위 releaseSigningProps 참고).
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
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
