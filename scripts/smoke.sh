#!/usr/bin/env bash
set -euo pipefail
mkdir -p smoke-results
adb shell wm size 720x1280
adb shell wm density 240
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell settings put system accelerometer_rotation 0
adb shell settings put system user_rotation 1
adb logcat -c
adb shell am instrument -w com.nightflow.game.test/com.nightflow.game.SmokeRunner | tee smoke-results/instrumentation.txt
adb logcat -d -s NightFlow NightFlowSmoke AndroidRuntime > smoke-results/logcat.txt
adb pull /sdcard/Android/data/com.nightflow.game/files/smoke smoke-results/screenshots || true
python3 - <<'PY'
from pathlib import Path
import base64
for preview in sorted(Path("smoke-results/screenshots").rglob("*.jpg")):
    print("NF_PREVIEW=" + preview.stem + ":" + base64.b64encode(preview.read_bytes()).decode())
text = Path("smoke-results/instrumentation.txt").read_text()
if "smoke=PASS" not in text:
    raise SystemExit("Android launch/control/rendering smoke test did not pass")
print("Android smoke test passed")
PY
