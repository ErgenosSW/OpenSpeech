# Third-party components

The original OpenSpeech application code is licensed under GPL-3.0-only. Third-party code, build tools and voice models retain their own licenses. This source repository does not contain the voice weights, language data or sherpa-onnx Android AAR; `scripts/prepare-assets.py` downloads pinned artifacts and verifies SHA-256 checksums.

- **sherpa-onnx 1.12.20**, Apache-2.0: https://github.com/k2-fsa/sherpa-onnx/tree/v1.12.20
- **ONNX Runtime**, MIT, included in the downloaded upstream AAR: https://github.com/microsoft/onnxruntime
- **eSpeak NG**, GPL-3.0-or-later, used by the phonemizer and its language data: https://github.com/espeak-ng/espeak-ng
- **AndroidX Core 1.15.0**, Apache-2.0, resolved by Gradle: https://android.googlesource.com/platform/frameworks/support/
- **Gradle wrapper**, Apache-2.0: https://github.com/gradle/gradle/tree/v8.13.0
- **Darkman voice export**: https://github.com/k2-fsa/sherpa-onnx/releases/tag/tts-models

The exact downloaded Darkman export's model card states that its dataset is CC0 and that it was fine-tuned from `en_US-lessac-medium`. The bundled Lessac card links to the Lessac dataset terms. These model and dataset terms are separate from the GPL license of OpenSpeech. The newer Darkman model card describes a different training lineage and should not be substituted for the older export's card.

Relevant license texts and model cards are preserved in `licenses/`. Model cards and speech-component license texts are also bundled as app assets. The dependency archive and model hashes are recorded in `dependencies.lock.json`.

Public distribution of compiled APKs must satisfy the applicable third-party licenses, including the source requirements for included GPL components. This repository publishes the application sources; it does not relicense third-party artifacts or the voice model as GPL.
