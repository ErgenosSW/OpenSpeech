#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-only
set -euo pipefail
cd "$(dirname "$0")"
signing_dir="${OPENSPEECH_SIGNING_DIR:-${XDG_DATA_HOME:-$HOME/.local/share}/OpenSpeech/signing}"
export OPENSPEECH_STORE_FILE="${OPENSPEECH_STORE_FILE:-$signing_dir/OpenSpeech-release.keystore}"
export OPENSPEECH_KEY_ALIAS="${OPENSPEECH_KEY_ALIAS:-openspeech}"
if [[ -z "${OPENSPEECH_STORE_PASSWORD:-}" ]]; then
    [[ -r "$signing_dir/release-password" ]] || { echo "Missing private release signing password file." >&2; exit 1; }
    OPENSPEECH_STORE_PASSWORD="$(< "$signing_dir/release-password")"
    export OPENSPEECH_STORE_PASSWORD
fi
export OPENSPEECH_KEY_PASSWORD="${OPENSPEECH_KEY_PASSWORD:-$OPENSPEECH_STORE_PASSWORD}"
./build.sh :app:assembleRelease :app:lintRelease "$@"
