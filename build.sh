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
mkdir -p .tools
if [[ ! -f .tools/debug.keystore ]]; then
    "$JAVA_HOME/bin/keytool" -genkeypair -keystore .tools/debug.keystore -storepass android \
        -alias AndroidDebugKey -keypass android -keyalg RSA -keysize 2048 \
        -validity 10000 -dname 'CN=Android Debug,O=Android,C=US'
fi
if [[ -x .tools/gradle-8.13/bin/gradle ]]; then
    .tools/gradle-8.13/bin/gradle --no-daemon "$@"
else
    ./gradlew --no-daemon "$@"
fi
