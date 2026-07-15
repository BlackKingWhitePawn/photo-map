# Ветка `init-01-project-bootstrap`

## Цель

Подготовить репозиторий и минимальный Android-проект `Photo Map`, который открывается в Android Studio и собирается командой `./gradlew assembleDebug`.

## Границы ветки

Ветка отвечает только за инициализацию репозитория, Gradle-проекта, базовой структуры приложения и документации.

В этой ветке не реализуются:

* чтение фотографий через MediaStore;
* запрос runtime-разрешений;
* Room-индекс фотографий;
* сканирование EXIF;
* карта и кластеризация;
* сетка галереи;
* полноэкранный просмотр;
* WorkManager-сканирование.

## Задачи

1. Подготовить репозиторий:
   * добавить `.gitignore` для Android, Gradle и IDE-файлов;
   * добавить `local.properties.example` с примером `MAPS_API_KEY=your_api_key`;
   * исключить секреты, локальные настройки и артефакты сборки из Git.

2. Создать Android-проект:
   * добавить Gradle Wrapper;
   * добавить `settings.gradle.kts`;
   * добавить корневой `build.gradle.kts`;
   * добавить модуль `app`;
   * настроить package name `com.example.photomap`;
   * настроить `minSdk = 29`;
   * настроить `versionName = 0.1.0`;
   * настроить начальный `versionCode`.

3. Подключить стек первой версии:
   * Kotlin;
   * Jetpack Compose;
   * Material 3;
   * Navigation Compose;
   * Hilt;
   * Room;
   * WorkManager;
   * Coil;
   * Google Maps Compose;
   * Maps Compose Utils;
   * ExifInterface;
   * Kotlin Coroutines;
   * Flow и StateFlow.

4. Создать минимальное приложение:
   * `PhotoMapApplication`;
   * `MainActivity`;
   * базовую тему Compose;
   * навигационный контейнер;
   * нижнюю навигацию для экранов карты, галереи и настроек;
   * минимальные экраны без бизнес-логики: разрешения, карта, галерея, просмотр фотографии, настройки.

5. Создать структуру пакетов:
   * `core/permissions`;
   * `core/dispatcher`;
   * `core/util`;
   * `data/local`;
   * `data/mapper`;
   * `data/media`;
   * `data/repository`;
   * `data/worker`;
   * `domain/model`;
   * `domain/repository`;
   * `domain/usecase`;
   * `di`;
   * `ui/navigation`;
   * `ui/permissions`;
   * `ui/map`;
   * `ui/gallery`;
   * `ui/viewer`;
   * `ui/settings`;
   * `ui/components`;
   * `ui/theme`.

6. Подготовить Google Maps API key:
   * читать `MAPS_API_KEY` из `local.properties`;
   * передавать ключ в Android Manifest через безопасный Gradle-механизм;
   * не хранить реальный API-ключ в репозитории.

7. Обновить README:
   * описать проект;
   * указать стек;
   * описать требования к окружению;
   * описать настройку `MAPS_API_KEY`;
   * указать команду сборки;
   * перечислить ограничения этой ветки.

## Критерии готовности

Ветка считается готовой, если:

* проект открывается в Android Studio;
* команда `./gradlew assembleDebug` завершается успешно;
* приложение запускается и показывает базовый Compose-интерфейс;
* в проекте есть структура пакетов из ТЗ;
* `MAPS_API_KEY` не захардкожен в исходном коде;
* `branch.md` описывает задание текущей ветки;
* README содержит инструкции для первой сборки;
* в Git не попадают секреты, локальные настройки и тяжёлые артефакты сборки.

## Ожидаемый результат

После завершения ветки следующая ветка сможет начинать реализацию разрешений и чтения MediaStore без дополнительной настройки проекта.
