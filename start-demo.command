#!/bin/bash
# 늘봄 Drive 시연 한 번에 열기 (macOS: Finder에서 더블클릭, VS Code: Cmd+Shift+B)
#  1) 가상 기기(에뮬레이터)가 꺼져 있으면 자동으로 켜고, 차량 앱(mock)을 설치·실행
#  2) 시연 서버(모의 주행 8주 + 모의 점검 6회)를 켜고
#  3) 브라우저에 웹 화면을 연다
# 끝내려면 이 창에서 Enter 또는 창 닫기.
cd "$(dirname "$0")" || exit 1

say() { printf '\n▶ %s\n' "$1"; }
pause_exit() { printf '\n%s\n엔터를 누르면 창을 닫아요.' "$1"; read -r _; exit "${2:-1}"; }

# --- Java: 따로 설치하지 않았으면 Android Studio에 들어 있는 것을 쓴다 ---
if [ -z "$JAVA_HOME" ]; then
  for j in "/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
           "/Applications/Android Studio.app/Contents/jre/Contents/Home"; do
    [ -d "$j" ] && export JAVA_HOME="$j" && break
  done
fi
if [ -z "$JAVA_HOME" ] && ! command -v java >/dev/null 2>&1; then
  pause_exit "Java를 찾지 못했어요. Android Studio를 '응용 프로그램' 폴더에 설치했는지 확인해 주세요."
fi

# --- Android SDK 위치(차량 앱 설치용) ---
SDK="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
ADB="$SDK/platform-tools/adb"
if [ -d "$SDK" ] && [ ! -f local.properties ]; then
  echo "sdk.dir=$SDK" > local.properties
fi

# --- 1) 에뮬레이터: 꺼져 있으면 자동으로 켠다 ---
EMU="$SDK/emulator/emulator"
emulator_running() { [ -x "$ADB" ] && "$ADB" devices | grep -q 'emulator-.*device$'; }

pick_avd() {
  # Android Studio에서 만들어 둔 가상 기기 중 태블릿·자동차용을 먼저 고른다.
  local list
  list=$("$EMU" -list-avds 2>/dev/null | grep -v '^INFO')
  [ -z "$list" ] && return 1
  echo "$list" | grep -i -m1 'automotive\|tablet' || echo "$list" | head -n1
}

create_avd() {
  # 가상 기기가 하나도 없을 때: 필요한 도구가 있으면 태블릿 가상 기기를 만든다(처음 한 번, 내려받기 때문에 오래 걸림).
  local tools="$SDK/cmdline-tools/latest/bin"
  [ -x "$tools/avdmanager" ] && [ -x "$tools/sdkmanager" ] || return 1
  local abi="x86_64"; [ "$(uname -m)" = "arm64" ] && abi="arm64-v8a"
  local image="system-images;android-34;google_apis;$abi"
  say "가상 기기를 처음 만들어요. 안드로이드 화면 파일을 내려받는 중이라 10분쯤 걸릴 수 있어요."
  yes | "$tools/sdkmanager" --install "emulator" "platform-tools" "$image" >/tmp/neulbom-sdk.log 2>&1 || return 1
  echo no | "$tools/avdmanager" create avd -n Neulbom_Tablet -k "$image" -d pixel_tablet --force >/tmp/neulbom-avd.log 2>&1 || return 1
  echo Neulbom_Tablet
}

if [ -x "$EMU" ] && [ -x "$ADB" ] && ! emulator_running; then
  AVD=$(pick_avd) || AVD=$(create_avd)
  if [ -n "$AVD" ]; then
    say "가상 기기 '$AVD'를 켜는 중이에요(1~2분)."
    "$EMU" -avd "$AVD" -no-snapshot-save >/tmp/neulbom-emulator.log 2>&1 &
    "$ADB" wait-for-device
    for _ in $(seq 1 240); do
      [ "$("$ADB" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ] && break
      sleep 1
    done
    "$ADB" shell input keyevent 82 >/dev/null 2>&1 # 잠금 화면 풀기
  else
    say "가상 기기가 아직 없어요. Android Studio 첫 화면의 'More Actions → Virtual Device Manager → +'에서 'Pixel Tablet'을 한 번만 만들어 주세요. 그다음부터는 이 파일이 알아서 켜요."
  fi
fi

# --- 차량 앱 설치 ---
if emulator_running; then
  say "차량 앱을 설치하는 중이에요(처음에는 몇 분 걸려요)."
  if ./gradlew -q installMockDebug; then
    APP_OK=1
  else
    say "차량 앱 설치에 실패했어요. 웹 화면만 열게요."
  fi
else
  say "가상 기기를 켜지 못해서 웹 화면만 열게요."
fi

# --- 2) 시연 서버 ---
URL="http://localhost:8080"
if curl -s -o /dev/null "$URL"; then
  say "시연 서버가 이미 켜져 있어요."
else
  say "시연 서버를 켜는 중이에요."
  # 기록은 ~/.neulbom/neulbom.db에 남는다(다시 켜도 유지). 처음부터 보려면 그 파일을 지운다.
  mkdir -p "$HOME/.neulbom"
  NEULBOM_DB_PATH="$HOME/.neulbom/neulbom.db" NEULBOM_DEMO_SEED=true ./gradlew -q -Pneulbom.jvmOnly=true :server:run > /tmp/neulbom-server.log 2>&1 &
  SERVER_PID=$!
  # 끝낼 때: Gradle과, Gradle이 띄운 서버(8080 포트)까지 끈다.
  stop_server() {
    kill "$SERVER_PID" 2>/dev/null
    if command -v lsof >/dev/null 2>&1; then
      lsof -ti tcp:8080 -sTCP:LISTEN 2>/dev/null | xargs kill 2>/dev/null
    else
      pkill -f 'com.neulbom.drive.server.MainKt' 2>/dev/null
    fi
  }
  trap stop_server EXIT
  for _ in $(seq 1 180); do
    curl -s -o /dev/null "$URL" && break
    if ! kill -0 "$SERVER_PID" 2>/dev/null; then
      tail -n 20 /tmp/neulbom-server.log
      pause_exit "서버가 켜지지 않았어요. 위 내용을 캡처해서 보내 주세요."
    fi
    sleep 1
  done
  curl -s -o /dev/null "$URL" || pause_exit "서버가 3분 안에 켜지지 않았어요. /tmp/neulbom-server.log 를 보내 주세요."
fi

# --- 3) 화면 열기 ---
say "웹 화면을 열어요: $URL"
open "$URL" 2>/dev/null || xdg-open "$URL" 2>/dev/null
if [ "${APP_OK:-0}" = 1 ]; then
  say "차량 앱을 실행해요."
  "$ADB" shell am start -n com.neulbom.drive.mock/com.neulbom.drive.MainActivity >/dev/null
fi

printf '\n✅ 준비됐어요. 시연을 마치면 이 창에서 엔터를 눌러 서버를 끄세요.\n'
read -r _
