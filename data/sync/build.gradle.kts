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

// 주행 세션 업로드(멱등·재시도). Android 의존성 없이 JVM에서 테스트한다.
dependencies {
    api(project(":domain:driving"))
    api(project(":domain:consent"))
    implementation(libs.kotlinx.serialization.json)
    api(libs.kotlinx.coroutines.core)
    testImplementation(project(":vehicle:mock"))
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
}
