# Third-party components

OpenSpeech application code and the Android adapters are GPL-3.0-only. The bundled voice, upstream source files and libraries retain the separate licenses listed below. OpenSpeech is free to use, modify and redistribute under the applicable licenses. There are no subscriptions, paid APIs or runtime cloud services.

## Components included in the APK

| Component | Exact input | License and distribution materials |
| --- | --- | --- |
| Polish male voice `male_voxcpm_2` | Matcha-TTS-PL-Edge-v2, revision `8ac4956ca61f91732e99965a8645215f0a8bd383`; deterministic fp32 encoder, four-step decoder, fp32 HiFi-GAN V2 vocoder | CC BY-SA 4.0 for released weights; MODEL_CARD, LICENSE and full ATTRIBUTION bundled under assets/licenses |
| eSpeak NG phonemizer | 1.52.0, source revision `4870adfa25b1a32b4361592f1be8a40337c58d6c` | GPL-3.0-or-later; complete vendored sources, data inputs and build scripts under third_party/espeak-ng |
| eSpeak Polish/English language data | Compiled from the same vendored source | GPL terms and Unicode notices retained; generated locally by scripts/build-phoneme-data.py |
| ucd-tools | Included in the pinned eSpeak NG source | GPL-3.0-or-later and included Unicode data terms; source COPYING and COPYING.UCD preserved |
| ONNX Runtime Android | Maven `com.microsoft.onnxruntime:onnxruntime-android:1.23.2` | MIT and upstream ThirdPartyNotices.txt for v1.23.2 bundled in APK; runtime and pinned CPU dependency source archives included in corresponding-source package |
| AndroidX, Kotlin standard library, coroutines and runtime annotations | Exact versions and artifact hashes in app/gradle.lockfile, gradle/verification-metadata.xml and docs/audit/gradle-runtime-inventory.json | Apache-2.0 declarations and embedded notices retained; metadata-only modules are distinguished from packaged code |

Darkman, Lessac, sherpa-onnx and ZONOS3 are **not** dependencies of this build. Their historical investigation documents are not bundled as runtime license statements.

## Voice rights and attribution

The selected model's author explicitly releases the model under CC BY-SA 4.0, including the vocoder adaptation. This is a model license statement, not merely a CC0 label on training recordings. The synthetic male voice was designed using VoxCPM2; it is not a clone of a named reader. The acoustic model descends from Matcha-TTS-PL-Pretrain, which has a VCTK warm start; the published attribution describes the training chain, datasets, readers and directors. No Darkman/Lessac base is used by the selected model.

Preserve the complete model LICENSE and ATTRIBUTION when redistributing the APK or the weights. Credit Machinekind, the upstream Matcha-TTS and HiFi-GAN authors, and the credited data sources. Adaptations of the weights remain subject to their ShareAlike terms. OpenSpeech does not relicense the unmodified weights as GPL or public domain.

The Kotlin inference/frontend adapters are adaptations of the published inference procedure and symbol/cleaner definitions. Changes include Android integration, a Polish-only frontend, bounded memory, cancellation and resource verification. Original upstream code and notices are retained under third_party/matcha-edge. OpenSpeech's contributions to these adapters are GPL-3.0-only, using the one-way CC BY-SA 4.0 to GPLv3 compatibility declared by Creative Commons: https://creativecommons.org/compatible-licenses/

Decimal/punctuation behavior also follows phonemizer 3.6.0 (GPL-3.0-or-later; Mathieu Bernard). The relevant reference source and notices are preserved under third_party/phonemizer-reference. No Python interpreter, phonemizer Python package, wordfreq corpus or remote service is included in the APK.

The model author notes that their entire modified training fork is not published. The released weights and inference code are available under the licenses above; this source package does not claim that the original training run can be reproduced in full.

## Corresponding source and reproducible inputs

OpenSpeech.zip must contain this source tree, including third_party/espeak-ng, its dictionary/phoneme source inputs, the Android JNI adapter and all build scripts. It also includes the pinned ONNX Runtime source archive under third_party/source-archives, with a provenance record explaining removal of unused test models, datasets and media. Runtime/build source and dependency pins are retained. Required build dependencies are pinned or recorded; downloaded model artifacts are checked against dependencies.lock.json.

Native sources and source archives are covered by licenses/native-sources.json. A changed source, unexpected file, altered model or mismatching resource hash is rejected before compilation. The source ZIP must accompany the exact APK it describes. Checksums identify both artifacts.

## Build-only tools

Gradle 8.13, Android Gradle Plugin 8.9.2, Kotlin Gradle Plugin 2.1.20, Android SDK 35 / Build Tools 35.0.0, NDK 28.2.13676358 and CMake 3.22.1 are build tools. They are not automatically components of the APK. OpenSpeech's native phonemizer is C-only; no libc++ shared runtime is needed by it. Inspect the final APK inventory rather than assuming that all build tools are redistributed.

## Primary sources

- https://huggingface.co/machinekind/Matcha-TTS-PL-Edge-v2/tree/8ac4956ca61f91732e99965a8645215f0a8bd383
- https://huggingface.co/machinekind/Matcha-TTS-PL-Pretrain
- https://github.com/espeak-ng/espeak-ng/tree/4870adfa25b1a32b4361592f1be8a40337c58d6c
- https://github.com/microsoft/onnxruntime/tree/v1.23.2
- https://creativecommons.org/licenses/by-sa/4.0/
- https://creativecommons.org/compatible-licenses/
