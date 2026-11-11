// 플러그인은 루트 buildscript 클래스패스에 한 번만 올린다(AGP·KGP가 같은 클래스로더를 공유해야 함).
// -Pneulbom.jvmOnly=true 이면 Android 모듈과 AGP를 제외하고 순수 Kotlin 도메인 모듈만 빌드·테스트한다
// (Google Maven에 접근할 수 없는 CI/샌드박스용).
buildscript {
    val jvmOnly = gradle.startParameter.projectProperties["neulbom.jvmOnly"] == "true"
    repositories {
        if (!jvmOnly) google()
        mavenCentral()
        gradlePluginPortal()
    }
    dependencies {
        classpath(libs.kotlin.gradle.plugin)
        classpath(libs.kotlin.serialization.gradle.plugin)
        if (!jvmOnly) {
            classpath(libs.android.gradle.plugin)
            classpath(libs.compose.compiler.gradle.plugin)
            classpath(libs.ksp.gradle.plugin)
        }
    }
}
