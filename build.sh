#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-only
set -euo pipefail
cd "$(dirname "$0")"
export JAVA_HOME="${JAVA_HOME:-$PWD/.tools/jdk}"
export ANDROID_HOME="${ANDROID_HOME:-$PWD/.tools/android-sdk}"
export ANDROID_USER_HOME="$PWD/.tools/android-user"
export GRADLE_USER_HOME="$PWD/.tools/gradle-cache"
export PATH="$JAVA_HOME/bin:$PATH"
if [[ $# -eq 0 ]]; then set -- :app:assembleDebug :app:lintDebug; fi
release_requested=false
debug_requested=false
for task in "$@"; do
    if [[ "$task" == *Debug* || "$task" == *debug ]]; then debug_requested=true; fi
    if [[ "$task" == *Release* || "$task" == *release || "$task" == "build" || "$task" == ":app:build" || "$task" == "assemble" || "$task" == ":app:assemble" ]]; then
        release_requested=true
    fi
done
if [[ "$release_requested" == true ]]; then
    python3 scripts/check-release.py
    for setting in OPENSPEECH_STORE_FILE OPENSPEECH_STORE_PASSWORD OPENSPEECH_KEY_ALIAS OPENSPEECH_KEY_PASSWORD; do
        if [[ -z "${!setting:-}" ]]; then echo "Missing release signing setting: $setting" >&2; exit 1; fi
    done
fi
mkdir -p .tools
if [[ "$debug_requested" == true && "$release_requested" == false && ! -f .tools/debug.keystore ]]; then
    "$JAVA_HOME/bin/keytool" -genkeypair -keystore .tools/debug.keystore -storepass android \
        -alias AndroidDebugKey -keypass android -keyalg RSA -keysize 2048 \
        -validity 10000 -dname 'CN=Android Debug,O=Android,C=US'
fi
if [[ -x .tools/gradle-8.13/bin/gradle ]]; then
    .tools/gradle-8.13/bin/gradle --no-daemon "$@"
else
    ./gradlew --no-daemon "$@"
fi
