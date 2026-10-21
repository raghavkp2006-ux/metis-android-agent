#!/usr/bin/env bash
set -euo pipefail

serial=emulator-5554
sdk="${ANDROID_HOME:?ANDROID_HOME is required}"
app=app/build/outputs/apk/capabilityTest/app-capabilityTest.apk
test_apk=app/build/outputs/apk/androidTest/capabilityTest/app-capabilityTest-androidTest.apk
proof=app/build/reports/capability-testing/emulator-proof
suite=app/build/reports/capability-testing/emulator-suite
logs=app/build/reports/androidTests/emulator-logs
mkdir -p "$logs"
logcat_pid=

capture_diagnostics() {
    local status=$?
    trap - EXIT
    if [[ -n "$logcat_pid" ]]; then
        kill "$logcat_pid" 2>/dev/null || true
        wait "$logcat_pid" 2>/dev/null || true
    fi
    adb -s "$serial" logcat -b crash -d > "$logs/logcat-crash.txt" 2>&1 || true
    grep -Ei 'LocalAgentOrchestrator|RequestViewModel|AgentOrchestrator|REMINDER_PROPOSAL_DIAGNOSTIC' \
        "$logs/logcat-full.txt" > "$logs/orchestrator.txt" || true
    exit "$status"
}
trap capture_diagnostics EXIT

python tools/capability_testing/emulator_ready.py --sdk "$sdk" --serial "$serial"
python tools/capability_testing/harness.py proof --sdk "$sdk" --app "$app" --test "$test_apk" \
    --serial "$serial" --output "$proof"

# The proof reboots the emulator. Start the stream after that reboot and before the suite.
adb -s "$serial" logcat -b all -v threadtime > "$logs/logcat-full.txt" 2>&1 &
logcat_pid=$!
python tools/capability_testing/harness.py suite --sdk "$sdk" --app "$app" --test "$test_apk" \
    --serial "$serial" --proof "$proof/result.json" --output "$suite"
