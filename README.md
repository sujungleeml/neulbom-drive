# 늘봄 Drive (Neulbom Drive)

> 나이가 들어도, 운전의 즐거움은 계속되도록.

Pleos Connect 차량 앱입니다. 차가 주차된 것이 확인될 때만 화면을 보고 큰 버튼을 누르는 세 가지 짧은 활동(3~4분)으로 나의 주의와 반응을 기록합니다. 그 기록과 주행 습관은 차 안이 아니라 휴대폰이나 컴퓨터 웹에서 스스로 살펴봅니다.

- 차 안에서는 결과를 보여 주지 않습니다. 완료 화면에는 저장 상태와 닫기만 있습니다.
- 주행 습관 기록과 점검 기록은 하나의 값으로 합치지 않습니다(`NoCombinedScoreTest`).
- 위치, 경로, 목적지는 받지 않습니다. 마이크 권한도 없습니다.
- 이 앱은 의료기기가 아니며, 운전을 계속할지 판단하거나 권하지 않습니다.
- 활동 방식과 제안 간격의 값은 모두 사용성 확인 전 임시값입니다.

## 현재 상태

| 영역 | 상태 |
|---|---|
| 차량 앱 흐름(온보딩, 운전자 확인, 대기, 제안, 안내, 세 활동, 완료, 중단, 주행 화면) | 구현 |
| 주차 확인 게이트(기어 P단, 정지, 전원을 함께 확인, 속도 0만으로는 주차로 보지 않음)와 주행 전환 시 즉시 멈춤 | 구현, 모의 차량으로 확인 |
| 세 활동(화살표 보고 누르기 24회, 누를 때와 멈출 때 40회, 규칙 바꾸기 32회, 첫 점검에만 연습) | 구현 |
| 결과와 무관한 제안 스케줄러(처음 4회로 기준, 이후 28일 간격) | 구현 |
| 주행 습관 기록(선택 동의, 기본 꺼짐), 서버 업로드, 웹 「나의 드라이빙」 | 구현, 모의 주행 기록으로 시연 |
| 웹(한눈에 보기, 나의 드라이빙, 나의 주의와 반응 기록, 나의 여정, 패턴 지도(시연), 운전 습관 정보, 가족 공유, 설정과 권한) | 구현 |
| 실제 Pleos SDK 신호 연결 | 미구현. SDK 자료 확인 후 연결 예정이며, 그 전까지 Pleos 빌드는 모든 차량 상태를 「알 수 없음」으로 두어 점검을 열지 않음 |
| 로그인과 차량 연결, 운영 웹 주소, 차 안 저장소 암호화 | 미구현(운영 전 필수) |

## 빌드와 실행

필요: JDK 17 이상, Android SDK(API 34).

```bash
# 도메인과 서버 테스트(Android SDK 없이)
./gradlew -Pneulbom.jvmOnly=true test

# 전체 테스트와 데모 APK
./gradlew test
./gradlew assembleMockDebug     # 모의 차량 빌드 → app/build/outputs/apk/mock/debug/
./gradlew assemblePleosDebug    # Pleos 빌드(차량 신호 연결 전, 점검은 열리지 않음)

# 서버와 웹(시연용 모의 기록 포함)
NEULBOM_DEMO_SEED=true ./gradlew -Pneulbom.jvmOnly=true :server:run
# 브라우저: http://localhost:8080
```

- 에뮬레이터의 모의 차량 앱은 `http://10.0.2.2:8080`(개발 PC 서버)로 기록을 보냅니다. 화면 위 시뮬레이터로 주차와 주행을 바꿀 수 있습니다.
- Mac에서는 `start-demo.command`를 실행하면 에뮬레이터, 앱 설치, 서버, 웹을 한 번에 엽니다.

## 구조

```
core/               공통 모델
vehicle/api         차량 어댑터 계약(움직임, 주차, 전원, 기능 지원, 신호 지원)
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
```

## 라이선스 참고

- 글꼴 Pretendard: SIL Open Font License 1.1 (`docs/licenses/Pretendard.md`)
