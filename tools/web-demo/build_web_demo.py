#!/usr/bin/env python3
"""늘봄 Drive 읽기 전용 웹 시연 만들기.

시연 서버(NEULBOM_DEMO_SEED=true, 시연 제안 켜짐)에서 웹 화면이 읽는 응답을 한 번 받아 demo-data.js에 담고,
기존 웹 파일(server/src/main/resources/web)을 그대로 복사한 뒤 demo-shim.js만 앞에 끼운다.
결과 폴더는 서버 없이 index.html을 바로 열거나 정적 호스팅으로 볼 수 있다.

사용: python3 tools/web-demo/build_web_demo.py --server http://localhost:8080 --out docs/web-demo
표준 라이브러리만 쓴다.
"""
import argparse
import datetime as dt
import json
import pathlib
import shutil
import sys
import urllib.request

ROOT = pathlib.Path(__file__).resolve().parents[2]
WEB = ROOT / "server/src/main/resources/web"
HERE = pathlib.Path(__file__).resolve().parent
COPY = ["index.html", "app.css", "app.js", "render.js", "pattern.js", "pattern3d.js", "img"]
FILES = ["habits.json", "pattern-points.json", "pattern-map.json"]


def get(server, path):
    with urllib.request.urlopen(server.rstrip("/") + path, timeout=20) as r:
        return json.loads(r.read().decode("utf-8"))


def capture(server, year):
    data = {}
    paths = ["/v1/signal-support", "/v1/reports/me/checks", "/v1/settings/me", "/v1/share-links"]
    paths += [f"/v1/reports/me/overview?weeks={w}" for w in (4, 8, 12)]
    paths += [f"/v1/reports/me/journey?months={m}" for m in (6, 12)]
    paths += [f"/v1/reports/me/driving?period={y}-{m:02d}" for y in (year - 1, year) for m in range(1, 13)]
    first = get(server, "/v1/demo/suggestions?scenario=R01")
    ids = [s["id"] for s in first.get("scenarios", [])] or ["R01"]
    paths += [f"/v1/demo/suggestions?scenario={i}" for i in ids]
    for p in paths:
        data[p] = get(server, p)
    data["__driving_empty"] = get(server, "/v1/reports/me/driving?period=2000-01")
    for f in FILES:
        data["/" + f] = json.loads((WEB / f).read_text(encoding="utf-8"))
    return data


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--server", default="http://localhost:8080")
    ap.add_argument("--out", default=str(ROOT / "docs/web-demo"))
    a = ap.parse_args()
    out = pathlib.Path(a.out)
    today = dt.date.today()
    data = capture(a.server, today.year)
    data["__captured_at"] = today.isoformat()

    if out.exists():
        shutil.rmtree(out)
    out.mkdir(parents=True)
    for name in COPY:
        src = WEB / name
        (shutil.copytree if src.is_dir() else shutil.copy2)(src, out / name)
    shutil.copy2(HERE / "demo-shim.js", out / "demo-shim.js")
    shutil.copy2(HERE / "demo-shim.css", out / "demo-shim.css")
    (out / "demo-data.js").write_text(
        "// 시연 서버에서 받은 모의 기록·합성 데이터(실제 사용자 기록 아님).\n"
        "window.NEULBOM_DEMO_DATA = " + json.dumps(data, ensure_ascii=False) + ";\n",
        encoding="utf-8",
    )
    html = (out / "index.html").read_text(encoding="utf-8")
    html = html.replace("<title>늘봄 Drive 나의 드라이빙</title>", "<title>늘봄 Drive 웹 시연 (읽기 전용)</title>")
    html = html.replace('<link rel="stylesheet" href="app.css">',
                        '<link rel="stylesheet" href="app.css">\n<link rel="stylesheet" href="demo-shim.css">')
    html = html.replace('<script src="render.js"></script>',
                        '<script src="demo-data.js"></script>\n<script src="demo-shim.js"></script>\n<script src="render.js"></script>')
    if "demo-shim.js" not in html:
        sys.exit("index.html에 스크립트를 끼우지 못했어요(구조가 바뀌었는지 확인).")
    (out / "index.html").write_text(html, encoding="utf-8")
    print(f"web demo → {out}  ({len(data) - 1} responses)")


if __name__ == "__main__":
    main()
