#!/usr/bin/env bash
# Runs the UI smoke tests on the CI emulator and writes what happened into the job summary, which the public run page
# shows without signing in (raw logs and artifacts need a GitHub login).
set -u
./gradlew connectedDebugAndroidTest --stacktrace > ui-tests.log 2>&1; code=$?
adb logcat -d -v brief > logcat.txt 2>/dev/null || true
summary=${GITHUB_STEP_SUMMARY:-/dev/stdout}
{
  echo "## UI smoke tests: $([ $code -eq 0 ] && echo passed || echo "failed (exit $code)")"
  echo '### Gradle'; echo '```'; grep -E "FAILED|Tests? (run|on)|Error|Exception|BUILD|No tests|INSTRUMENTATION|Process crashed" ui-tests.log | head -40; echo '```'
  for f in app/build/outputs/androidTest-results/connected/debug/*.xml app/build/outputs/androidTest-results/connected/*.xml; do
    [ -f "$f" ] || continue; echo "### $(basename "$f")"; echo '```'; grep -E "<testcase|<failure|at dev\.solotilt|Exception|Error" "$f" | head -60; echo '```'
  done
  echo '### Crashes and app errors in logcat'; echo '```'
  grep -E "AndroidRuntime|FATAL|SoloRenderer|SoloVideo|SoloThumbs|TestRunner|SmokeRunner|dev\.solotilt" logcat.txt | grep -v "^D/" | tail -60
  echo '```'
  echo "### Screenshots pulled"; ls -1 build/screenshots 2>/dev/null || echo "none"
} >> "$summary"
exit $code
