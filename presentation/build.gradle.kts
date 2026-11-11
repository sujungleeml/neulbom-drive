plugins {
    kotlin("jvm")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

// 화면 상태·흐름·문구 등 Android에 의존하지 않는 UI 로직. Compose 화면은 이 모듈의 상태를 그리기만 한다.
dependencies {
    api(project(":domain:consent"))
    api(project(":domain:cognitive"))
    testImplementation(project(":vehicle:mock"))
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
}

// AppSourceCopyScanTest가 app 모듈 소스를 읽으므로 입력으로 선언해 캐시가 변경을 놓치지 않게 한다.
tasks.test {
    inputs.dir(rootProject.file("app/src")).withPropertyName("appSources")
    inputs.dir(rootProject.file("server/src/main")).withPropertyName("serverSources")
    inputs.dir(rootProject.file("domain/driving/src/main")).withPropertyName("drivingSources")
    inputs.dir(rootProject.file("data/sync/src/main")).withPropertyName("syncSources")
}
