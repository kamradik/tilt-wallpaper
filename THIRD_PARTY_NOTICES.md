# Происхождение материалов

- В версии 1.1 аватарка и MP3 для страницы автора предоставлены пользователем и встроены без изменения. Это не заявление о лицензии на их публичное распространение. Интерфейс в стиле iOS нарисован кодом приложения: шрифты, иконки и ресурсы Apple не используются (шрифты приложения — Inter и Unbounded, см. ниже).

- Solo Tilt APK и GLSL-ресурсы загружены 19.09.2026 с https://solotilt.com/downloads/solo-tilt-android.apk для анализа запрошенного пользователем эффекта.
- Файлы `quad.glsl`, `fold.glsl`, `blur.glsl` перенесены из ресурсов APK без изменения. В исходном fragment shader указано: fixed-observer ray projection, adapted from DuoLikeAnimation (MIT).
- `DuoLikeAnimation-LICENSE.txt` сохранён из APK без изменения. Copyright (c) 2026 Elijah Semyonov, MIT. Публичный исходный проект: https://github.com/elijah-semyonov/DuoLikeAnimation.
- Наличие лицензии DuoLikeAnimation внутри APK не устанавливает лицензию на весь Solo Tilt, его торговое имя, иллюстрации, серверный код или все возможные доработки шейдеров. Для личного исследования сохранено происхождение ресурсов; право на публичное распространение всего оригинала здесь не утверждается.
- Java-код в `app/src/main/java/dev/solotilt/personal` — новая реализация по результатам анализа. Образцы изображений процедурные, изображения из оригинального APK в собственное приложение не включались.
- `analysis/decompiled` — автоматически полученные исследовательские материалы оригинального APK, а не код, объявленный нами под MIT.
- Репозиторий `upstream` — отдельно загруженная публичная веб-реализация https://github.com/soloiaros/iphone-solo. Она не использована как исходный код Android-приложения и не включена в архив результата.

В собственной сборке нет ключей, сертификата подписи или backend-частей оригинального приложения. Ключ подписи персональной сборки создан локально.

## Шрифты, с версии 2.6

- `app/src/main/assets/fonts/Inter.ttf` — **Inter** (вариативный, оси wght и opsz), Copyright 2020 The Inter Project Authors, https://github.com/rsms/inter, лицензия SIL Open Font License 1.1 (`third_party/fonts/OFL-Inter.txt`). Файл `ofl/inter/Inter[opsz,wght].ttf` из https://github.com/google/fonts, git blob `047c92f6e2212473dc436020afed689527076d44`, SHA-256 `29160a80ff49ddcab2c97711247e08b1fab27a484a329ce8b813d820dc559031`, загружен 16.10.2026.
- `app/src/main/assets/fonts/Unbounded.ttf` — **Unbounded** (вариативный, ось wght), Copyright 2022 The Unbounded Project Authors, https://github.com/googlefonts/unbounded, лицензия SIL Open Font License 1.1 (`third_party/fonts/OFL-Unbounded.txt`). Файл `ofl/unbounded/Unbounded[wght].ttf` из https://github.com/google/fonts, git blob `e3c064ddfa3acdd3af2e0f06cd2f2288cc4fcae3`, SHA-256 `323b511be380c8d474ef030686b71aedde501f8d9cd46da558b7c40454372c3f`, загружен 16.10.2026.
- Файлы шрифтов встроены без изменений; зарезервированные имена шрифтов не используются для изменённых версий.

## Spatial Scene (карта глубины), с версии 2.1

- `app/src/main/assets/models/depth_anything_v2_small_int8.onnx` — **Depth Anything V2 Small**, int8-экспорт ONNX из https://huggingface.co/onnx-community/depth-anything-v2-small (файл `onnx/model_int8.onnx`, SHA-256 `01aa7a23de3f4a0ee1a2bb9997e6918104c85a9f95dea46d27b9b3fb0c6b9001`, загружен 16.10.2026), лицензия Apache-2.0. Исходная модель: Depth Anything V2, Lihe Yang и др., https://github.com/DepthAnything/Depth-Anything-V2 (вариант Small распространяется под Apache-2.0; варианты Base/Large — под CC-BY-NC-4.0 и в приложение не входят).
- **ONNX Runtime** (`com.microsoft.onnxruntime:onnxruntime-android:1.30.0`), © Microsoft Corporation, лицензия MIT, подключается через Gradle.
- `app/src/main/assets/models/u2netp.onnx` — **U²-Net small (u2netp)**, Xuebin Qin и др., https://github.com/xuebinqin/U-2-Net, лицензия Apache-2.0. ONNX-экспорт из релиза проекта rembg (MIT), https://github.com/danielgatis/rembg/releases/download/v0.0.0/u2netp.onnx, SHA-256 `309c8469258dda742793dce0ebea8e6dd393174f89934733ecc8b14c76f4ddd8`, загружен 16.10.2026. Используется для маски главного объекта.
- **ZXing core** (`com.google.zxing:core:3.5.4`), лицензия Apache-2.0, подключается через Gradle. Используется для QR-кодов образов.
- `app/src/main/assets/models/migan_pipeline_v2.onnx` — **MI-GAN** (Sargsyan и др., Picsart AI Research, «MI-GAN: A Simple Baseline for Image Inpainting on Mobile Devices», ICCV 2023), https://github.com/Picsart-AI-Research/MI-GAN, лицензия MIT. ONNX-конвейер авторов с https://huggingface.co/andraniksargsyan/migan (файл `migan_pipeline_v2.onnx`, SHA-256 `6f1f3530a1a2324b19752018ce756088b07973cda8d7d890034ace5c8a48c40b`, загружен 16.10.2026). Используется для дорисовки фона за объектом.
