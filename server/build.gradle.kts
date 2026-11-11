plugins {
    kotlin("jvm")
    id("org.jetbrains.kotlin.plugin.serialization")
    application
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

application {
    mainClass.set("com.neulbom.drive.server.MainKt")
}

// 늘봄 Drive 시연 서버: 주행 세션 업로드, 리포트 API, 반응형 웹(조회·설정 전용).
dependencies {
    implementation(project(":domain:driving"))
    implementation(project(":domain:cognitive"))
    implementation(project(":domain:consent"))
    implementation(project(":vehicle:mock")) // 시연용 모의 주행 시드(NEULBOM_DEMO_SEED)
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.serialization.json)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.sqlite.jdbc) // NEULBOM_DB_PATH가 있으면 SQLite 파일에 영속 저장
    runtimeOnly(libs.logback)

    testImplementation(project(":data:sync"))
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.kotlin.test)
}

// NoCombinedScoreTest가 다른 모듈 소스를 읽으므로 입력으로 선언한다.
tasks.test {
    inputs.dir(rootProject.file("domain/driving/src/main")).withPropertyName("drivingSources")
    inputs.dir(rootProject.file("data/sync/src/main")).withPropertyName("syncSources")
    inputs.dir(rootProject.file("core/src/main")).withPropertyName("coreSources")
}
