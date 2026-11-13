# 늘봄 Drive (Neulbom Drive)

> **나이가 들어도, 운전의 즐거움은 계속되도록.**

늘봄 Drive는 주차 중 3~4분의 인지 활동과 일상 주행 기록으로, 고령 운전자가 자신의 변화를 스스로 이해하도록 돕는 Pleos Connect 기반 차량 앱입니다.

> **이 저장소는 현대자동차그룹 차량용 앱 개발 공모전 시연본입니다(버전 `1.0.0-contest`).**
> 실제 차량 SDK와는 아직 연결하지 않았고, 차량 신호는 **모의(mock) 구현**, 웹의 주행 기록과 일부 기능은 **모의 기록·합성 데이터**로 동작합니다. 정식 출시본이 아닙니다.

**바로 보기**

- 시연 영상(2분 37초): https://youtu.be/uYZdidaSaO4
- 읽기 전용 웹 시연(합성 데이터, 로그인·설치 불필요): https://sujungleeml.github.io/neulbom-drive/
- 시연 APK 내려받기: [Release `v1.0.0-contest`](https://github.com/sujungleeml/neulbom-drive/releases/tag/v1.0.0-contest)

- 차 안에서는 결과를 보여 주지 않습니다. 완료 화면에는 저장 상태와 닫기만 있습니다.
- 주행 습관 기록과 점검 기록은 하나의 값으로 합치지 않습니다(`NoCombinedScoreTest`).
- 위치, 경로, 목적지는 받지 않습니다. 마이크 권한도 없습니다.
- 이 앱은 의료기기가 아니며, 진단하거나 운전을 계속할지 판단·권유하지 않습니다.
- 활동 방식과 제안 간격의 값은 모두 사용성 확인 전 임시값입니다.

## 1. 차량 앱과 웹 대시보드의 역할

| 구분 | 하는 일 | 하지 않는 일 |
|---|---|---|
| **차량 앱** (Pleos Connect, Android Automotive) | 주차가 확인될 때만 점검을 제안하고, 운전자가 고르면 세 가지 활동을 진행해 기록을 저장. 주행 습관 기록(동의 시)을 모아 서버로 전송 | 운전 중 알림·점검, 결과·점수 표시 |
| **웹 대시보드** (휴대폰·컴퓨터 브라우저) | 나의 주의·반응 기록 추이, 이번 달 주행 습관과 지난달 비교, 설정·권한(동의 켜고 끄기, 제안 쉬기, 내려받기, 모두 지우기), 가족 공유 링크 | 판정, 진단, 다른 사람과의 순위 비교 |

## 2. 세 가지 인지 활동 (주의 및 반응)

| 순서 | 활동 | 본 시행 | 화면 |
|---|---|---|---|
| 1 | **화살표 보고 누르기**: 화살표 방향과 같은 쪽 버튼 누르기 | 24회 | 큰 버튼 2개 |
| 2 | **누를 때와 멈출 때**(Go/No-Go): 누르기 신호에는 누르고, 멈춤 신호에는 참기 | 40회 | 큰 버튼 1개 |
| 3 | **규칙 바꾸기**(홀짝·크기): 화면 위 규칙에 따라 홀수/짝수, 5보다 작다/크다 고르기 | 32회 | 큰 버튼 2개 |

첫 점검에는 활동마다 연습이 붙습니다. 처음 4번의 점검으로 나만의 기준을 만들고, 이후에는 그 기준과 비교한 변화만 웹에서 보여 줍니다.

## 3. 구현 범위

| 영역 | 상태 |
|---|---|
| 차량 앱 흐름(온보딩·동의, 운전자 확인, 대기, 점검 제안, 안내, 세 활동, 완료, 중단, 주행 화면) | **구현** |
| 주차 확인 게이트(P단·정지·전원을 함께 확인, 속도 0만으로는 주차로 보지 않음), 주행 전환·신호 불명 시 즉시 중단 | **구현**, 모의 차량 신호로 확인 |
| 차량 신호가 1.5초 넘게 갱신되지 않으면 상태 불명으로 보고 점검을 막음(Fail-closed) | **구현**(Pleos 어댑터 기준값), 실차 신호로는 미확인 |
| 결과와 무관한 제안 스케줄러(처음 4회로 기준, 이후 28일 간격), 제안 쉬기 | **구현** |
| 시행 원자료 즉시 저장(Room), 서버 동기화(중복 방지, 재시도) | **구현** |
| 주행 습관 기록(선택 동의, 기본 꺼짐), 서버 업로드, 웹 「나의 드라이빙」(지난달과 비교, 급감속 100 km당) | **구현**, 시연은 모의 주행 기록 |
| 웹 「이번 달의 기록과 작은 제안」(두 기록이 함께 달라졌을 때 짧은 실천 제안, R01~R10) | **합성 데이터 시연 전용**. 실제 기록에는 제안을 만들지 않음(기준값 검증 전) |
| 웹 「패턴 지도」(비슷한 나이대 주행 패턴 속 나의 위치, 3D) | **합성 데이터 시연 전용** |
| 웹 설정·권한, 내 기록 내려받기·모두 지우기, 가족 공유 링크(범위·기간·취소) | **구현** |
| 실제 Pleos Vehicle SDK 신호 연결 | **미구현**(아래 6절) |
| 로그인·차량 연결, 운영 서버, 차 안 저장소 암호화 | **미구현**(운영 전 필수) |

## 4. 기술 스택

- **차량 앱**: Kotlin 2.0.21, Jetpack Compose(BOM 2024.12.01), Coroutines, Room 2.6.1, Android Gradle Plugin 8.7.3, Gradle 8.14.3, compileSdk·targetSdk 34, minSdk 29, Java 17
- **서버·웹**: Ktor 2.3.12, SQLite, 외부 라이브러리 없는 HTML·CSS·JavaScript(3D 패턴 지도도 직접 그린 캔버스)
- **테스트**: JUnit, Robolectric 4.14.1, Ktor 테스트 호스트
- **빌드 플래버**
  - `mock`: 모의 차량 신호 + 시연 도구. 에뮬레이터에서 바로 체험 가능. **심사용 APK는 이 플래버입니다.**
  - `pleos`: 실제 Pleos 어댑터 자리(공식 SDK 확인 전 안전 기본값). 차량 상태를 모두 「알 수 없음」으로 두어 점검이 열리지 않습니다.

## 5. 테스트 환경

- **에뮬레이터**: Pleos Connect Emulator v2.1 (Android Automotive, android-34 `pleos_car_emulator`, arm64-v8a), 화면 **2560×1440, 160 dpi**
- **확인한 개발 환경**: macOS(Apple Silicon), Android Studio, JDK 17 이상(JDK 23에서도 빌드 확인)
- **테스트 결과**: `./gradlew test --rerun-tasks` 기준 **테스트 287개 모두 통과(Android 변형별 중복 실행 포함 321회, 실패 0). 2026-11-13 재실행**
- **에뮬레이터 확인(2026-11-13, 서명된 심사용 APK, 네트워크 끈 상태)**: 설치·첫 실행, 온보딩 1~4(동의 기본 꺼짐), 운전자 확인, 점검 제안, 안내, 연습, 화살표 활동 27회 → 「다음 활동」 화면까지 확인. 점검 중 `주행` 전환 시 즉시 중단, 앱 화면이 가려지면 중단도 확인. 앱 오류·강제 종료 0건
- **알려진 에뮬레이터 문제**: Pleos Connect Emulator v2.1에서 차량 HAL이 응답하지 않으면(`Unable to retrieve vehicle property config`) 차량 서비스와 시스템 화면이 몇 분마다 다시 시작될 수 있습니다. 이때 화면이 검게 바뀌거나 내비게이션이 앞에 뜨고, 늘봄 Drive는 안전 규칙대로 점검을 멈춥니다(앱 결함 아님). 에뮬레이터를 **콜드 부트**(Device Manager → Cold Boot Now)로 다시 켠 뒤 해 보세요.

## 6. 실제 차량 SDK 연동 상태와 후속 과제

현재 **실제 Pleos Vehicle SDK는 연결하지 않았습니다.** 차량 신호는 `vehicle/mock`의 모의 시뮬레이터가 만듭니다.

후속 과제
1. Pleos Vehicle SDK로 기어·차속·전원·주차 브레이크 신호를 받아 `vehicle/pleos` 어댑터에 연결하고, 신호마다 `observedAt`을 갱신(현재 TODO 표시)
2. 실차·시뮬레이터에서 P단↔D단 전환 시 점검 즉시 중단 확인
3. 주행 습관 지표를 실제 신호(가속도, 브레이크)로 계산하고 지원 여부를 신호별로 확인
4. 로그인·차량 연결, 운영 서버, 차 안 저장소 암호화
5. 「작은 제안」 기준값을 실제 반복 점검 자료로 검증한 뒤에만 실제 기록에 적용

## 7. 설치와 실행

### APK 다운로드

- 저장소 안: [`apk/neulbom-drive-v1.0.0-contest-mock-vehicle-signals-release.apk`](apk/neulbom-drive-v1.0.0-contest-mock-vehicle-signals-release.apk)
- 플래버 `mock`(모의 차량 신호), 빌드 `release`, 버전 `1.0.0-contest-mock`(versionCode 2)
- SHA-256: `637965210bcad150dd756275d5fe6ef36ec70161ad247897dd842e3527dba05a`
- 서명: 릴리즈 키(APK Signature Scheme v2), 인증서 `CN=Neulbom Drive`, RSA 4096, 인증서 SHA-256 `01cc2f30aaea2a27ff71de64107110ce29a0339dba92fbc859871635e8993f21`
- 확인: `apksigner verify --print-certs apk/neulbom-drive-v1.0.0-contest-mock-vehicle-signals-release.apk`

### A. APK만으로 체험하기 (JDK·서버 설정 없이)

1. Android Studio에서 **Pleos Connect Emulator v2.1**을 켭니다.
2. APK를 에뮬레이터 화면에 끌어다 놓거나, `adb install apk/neulbom-drive-v1.0.0-contest-mock-vehicle-signals-release.apk`로 설치합니다.
3. 앱 목록에서 **늘봄 Drive**를 엽니다. 처음 실행하면 소개, 동의 선택, 연습이 나옵니다.
4. 상단의 **「늘봄 Drive」 글자를 길게 누르면** 시연 도구가 열립니다.
   - `P` / `주행` / `불명`: 모의 차량 상태 바꾸기(주차일 때만 점검 제안, 점검 중 `주행`을 누르면 즉시 멈춤)
   - 「시연 처음부터」: 기기 안 기록을 지우고 첫 사용 상태로
   - 「모의 주행 4주 만들기」: 모의 주행 기록 생성
5. 대기 화면에서 「지금 점검하기」 → 안내 → 세 활동 → 완료 화면까지 진행할 수 있습니다.

이 방법으로 **체험할 수 있는 것**: 차량 앱 전체 흐름(제안, 세 활동, 완료, 주행 전환 시 중단, 주행 중 화면, 동의 설정, 음성 안내).
**체험할 수 없는 것**: 실시간 웹 동기화. 서버가 없으면 앱은 기록을 기기에 저장하고 「연결되면 다시 보내요」라고 안내합니다.
웹 대시보드 화면은 아래 「읽기 전용 웹 시연」으로 서버 없이 볼 수 있습니다.

> 다른 키로 서명된 늘봄 Drive(예: 직접 빌드한 디버그 APK)가 이미 설치되어 있으면 먼저 지운 뒤 설치해 주세요(`adb uninstall com.neulbom.drive.mock`).

### 웹 대시보드를 서버 없이 보기 (읽기 전용 웹 시연)

https://sujungleeml.github.io/neulbom-drive/ 에서 바로 볼 수 있습니다(설치·서버·로그인 불필요). 저장소를 내려받았다면 [`docs/web-demo/index.html`](docs/web-demo/index.html)을 브라우저로 열어도 됩니다.

- 웹 화면 코드는 그대로 쓰고, 시연 서버에서 미리 받아 둔 **모의 기록·합성 데이터**를 보여 줍니다. 화면 맨 위에 「읽기 전용 시연」 표시가 붙습니다.
- 설정 바꾸기, 내려받기, 지우기, 공유 링크 만들기는 막혀 있고 안내만 나옵니다.
- 다시 만들기: 시연 서버를 켠 뒤 `python3 tools/web-demo/build_web_demo.py --server http://localhost:8080`

### B. 웹 대시보드까지 체험하기 (서버 실행)

웹과 동기화를 보려면 개발 PC에서 시연 서버를 켭니다(Java 17 이상 필요. Android Studio에 들어 있는 JDK를 써도 됩니다).

```bash
# macOS: 에뮬레이터, 앱 설치, 서버, 브라우저를 한 번에 (Android Studio 내장 JDK 자동 사용)
./start-demo.command

# 직접 실행(Windows는 gradlew.bat)
NEULBOM_DEMO_SEED=true ./gradlew -Pneulbom.jvmOnly=true :server:run
# 브라우저: http://localhost:8080
```

에뮬레이터 안 mock 앱은 `http://10.0.2.2:8080`(개발 PC의 서버)으로 기록을 보냅니다. `NEULBOM_DEMO_SEED=true`이면 모의 주행 8주와 모의 점검 6회가 미리 들어갑니다.

### C. 소스에서 빌드하기

필요: JDK 17 이상, Android SDK(API 34). `local.properties`에 `sdk.dir`을 적거나 `ANDROID_HOME`을 설정합니다.

```bash
./gradlew -Pneulbom.jvmOnly=true test   # 도메인·서버 테스트(Android SDK 없이)
./gradlew test                          # 전체 테스트(Robolectric 포함)
./gradlew assembleMockRelease           # 심사용과 같은 빌드 → app/build/outputs/apk/mock/release/
./gradlew assemblePleosDebug            # Pleos 빌드(차량 신호 연결 전, 점검은 열리지 않음)
```

릴리즈 서명 키가 없는 PC에서는 디버그 키로 서명됩니다(`app/build.gradle.kts` 참고). 서명 키와 비밀번호는 저장소에 넣지 않습니다.

## 8. 시연 자료

- 시연 영상(2분 37초): https://youtu.be/uYZdidaSaO4 (YouTube 일부 공개, 영상 파일은 저장소에 넣지 않음)
- 읽기 전용 웹 시연: https://sujungleeml.github.io/neulbom-drive/ (원본 파일 [`docs/web-demo/`](docs/web-demo/))
- 시연 APK: [Release `v1.0.0-contest`](https://github.com/sujungleeml/neulbom-drive/releases/tag/v1.0.0-contest)
- 주요 화면: [`docs/screens/`](docs/screens/)
  - `car-screens.png` 차량 앱 화면 9종, `car-while-driving.png` 주행 중 화면
  - `web-checks.png` 나의 주의·반응 기록, `web-settings.jpg` 설정·권한
  - `web-suggestion-demo.png` 작은 제안(합성 데이터 시연), `web-pattern-demo.png` 패턴 지도(합성 데이터 시연)

## 9. 모의 신호·합성 데이터 사용 범위

| 무엇 | 실제 | 모의·합성 |
|---|---|---|
| 차량 상태(주차, 주행, 전원) | — | 모의 시뮬레이터(`vehicle/mock`), 시연 도구로 전환 |
| 점검 기록(세 활동의 응답) | 앱에서 실제로 누른 기록 | 서버 시연 모드의 미리 넣은 점검 6회 |
| 주행 습관 기록 | — | 「모의 주행 4주 만들기」, 서버 시연 모드의 8주치 |
| 작은 제안(R01~R10) | 실제 기록에는 만들지 않음 | 시나리오별 합성 기록 10개 |
| 패턴 지도 | — | 연령대별 합성 주행 패턴 |

웹과 영상에는 「모의 기록」, 「합성 데이터 기반 기능 시연」 표시를 붙였습니다.

## 10. 구조

```
core/               공통 모델
vehicle/api         차량 어댑터 계약(움직임, 주차, 전원, 기능 지원, 신호 지원, 신호 나이)
vehicle/mock        모의 차량(시연)
vehicle/pleos       Pleos 어댑터(공식 자료 확인 전 안전 기본값)
domain/consent      동의와 보관 기간
domain/cognitive    주차 게이트, 세 활동, 점검 진행, 기록 계산
domain/driving      주행 습관 기록과 지표
domain/scheduler    점검 제안 규칙
data/local          차 안 저장(Room)
data/sync           서버 업로드(중복 방지, 재시도)
presentation        화면 상태와 문구
app                 Android 앱(Jetpack Compose)
server              Ktor 서버, SQLite, 웹(server/src/main/resources/web)
apk/                심사용 APK
docs/screens        주요 화면
docs/web-demo       읽기 전용 웹 시연(서버 없이 열림)
tools/web-demo      웹 시연 만들기 스크립트
```

## 라이선스 참고

- 글꼴 Pretendard: SIL Open Font License 1.1 (`docs/licenses/Pretendard.md`)
