# Происхождение материалов

- В версии 1.1 аватарка и MP3 для страницы автора предоставлены пользователем и встроены без изменения. Это не заявление о лицензии на их публичное распространение. Интерфейс в стиле iOS нарисован кодом приложения: шрифты, иконки и ресурсы Apple не используются.

- Solo Tilt APK и GLSL-ресурсы загружены 19.09.2026 с https://solotilt.com/downloads/solo-tilt-android.apk для анализа запрошенного пользователем эффекта.
- Файлы `quad.glsl`, `fold.glsl`, `blur.glsl` перенесены из ресурсов APK без изменения. В исходном fragment shader указано: fixed-observer ray projection, adapted from DuoLikeAnimation (MIT).
- `DuoLikeAnimation-LICENSE.txt` сохранён из APK без изменения. Copyright (c) 2026 Elijah Semyonov, MIT. Публичный исходный проект: https://github.com/elijah-semyonov/DuoLikeAnimation.
- Наличие лицензии DuoLikeAnimation внутри APK не устанавливает лицензию на весь Solo Tilt, его торговое имя, иллюстрации, серверный код или все возможные доработки шейдеров. Для личного исследования сохранено происхождение ресурсов; право на публичное распространение всего оригинала здесь не утверждается.
- Java-код в `app/src/main/java/dev/solotilt/personal` — новая реализация по результатам анализа. Образцы изображений процедурные, изображения из оригинального APK в собственное приложение не включались.
- `analysis/decompiled` — автоматически полученные исследовательские материалы оригинального APK, а не код, объявленный нами под MIT.
- Репозиторий `upstream` — отдельно загруженная публичная веб-реализация https://github.com/soloiaros/iphone-solo. Она не использована как исходный код Android-приложения и не включена в архив результата.

В собственной сборке нет ключей, сертификата подписи или backend-частей оригинального приложения. Ключ подписи персональной сборки создан локально.

## Spatial Scene (карта глубины), с версии 2.1

- `app/src/main/assets/models/depth_anything_v2_small_int8.onnx` — **Depth Anything V2 Small**, int8-экспорт ONNX из https://huggingface.co/onnx-community/depth-anything-v2-small (файл `onnx/model_int8.onnx`, SHA-256 `01aa7a23de3f4a0ee1a2bb9997e6918104c85a9f95dea46d27b9b3fb0c6b9001`, загружен 16.10.2026), лицензия Apache-2.0. Исходная модель: Depth Anything V2, Lihe Yang и др., https://github.com/DepthAnything/Depth-Anything-V2 (вариант Small распространяется под Apache-2.0; варианты Base/Large — под CC-BY-NC-4.0 и в приложение не входят).
- **ONNX Runtime** (`com.microsoft.onnxruntime:onnxruntime-android:1.30.0`), © Microsoft Corporation, лицензия MIT, подключается через Gradle.
