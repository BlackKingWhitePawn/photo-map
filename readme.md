# Photo Map

Нативное Android-приложение для локальной галереи фотографий с будущим отображением снимков на карте.

Проект разрабатывается по этапам. Текущая ветка `feature-03-map-screen` добавляет карту MapLibre и отображение фотографий с GPS-координатами.

## Текущее состояние

Реализовано:

* Android-проект с Kotlin и Jetpack Compose;
* Material 3 тема;
* runtime-запрос разрешений на чтение фотографий;
* поддержка `READ_EXTERNAL_STORAGE` для Android 10-12;
* поддержка `READ_MEDIA_IMAGES` для Android 13+;
* поддержка частичного доступа `READ_MEDIA_VISUAL_USER_SELECTED` для Android 14+;
* запрос `ACCESS_MEDIA_LOCATION`;
* чтение изображений через `MediaStore.Images.Media`;
* формирование URI через `ContentUris.withAppendedId`;
* вывод найденных фотографий в Logcat с тегом `PhotoMapMediaStore`;
* минимальный экран на русском с количеством найденных фотографий;
* read-only чтение GPS-координат из MediaStore/EXIF;
* первый экран карты на MapLibre без Google Maps API key;
* отображение фотографий с координатами на карте;
* базовая группировка близких точек.

Пока не реализовано:

* Room-индекс;
* галерея с миниатюрами;
* полноэкранный просмотр;
* WorkManager-фоновое сканирование.

## Стек

* Kotlin;
* Jetpack Compose;
* Material 3;
* Android MediaStore;
* ExifInterface;
* MapLibre Android SDK;
* Kotlin Coroutines;
* Flow и StateFlow;
* ViewModel.

Следующие этапы добавят Room, Coil, WorkManager и Hilt. MapLibre с OpenFreeMap не требует Google Maps API key, Google Play Services, регистрации или billing, но онлайн-карта требует интернет и доступный style/tile URL.

## Требования

* Android Studio с поддержкой Gradle Kotlin DSL;
* Android SDK для compileSdk проекта;
* устройство или эмулятор с Android 10+.

## Запуск

Открой проект в Android Studio и запусти конфигурацию `app`.

Команда сборки:

```bash
./gradlew assembleDebug
```

На Windows:

```powershell
.\gradlew.bat assembleDebug
```

## Разрешения

Приложение запрашивает:

* `READ_EXTERNAL_STORAGE` на Android 10-12;
* `READ_MEDIA_IMAGES` на Android 13+;
* `READ_MEDIA_VISUAL_USER_SELECTED` на Android 14+;
* `ACCESS_MEDIA_LOCATION` для будущего чтения исходных геоданных из медиа.

Если доступ к фотографиям не выдан, приложение не должно падать. После отказа доступ можно запросить повторно или открыть системные настройки приложения.

## MediaStore

Текущая реализация читает:

* MediaStore ID;
* URI;
* имя файла;
* MIME-тип;
* дату добавления;
* дату изменения;
* дату съёмки;
* ширину;
* высоту;
* размер файла;
* ориентацию.

Абсолютные пути файлов не используются как идентификаторы. Основной идентификатор - MediaStore ID.

## Карта

Карта работает через MapLibre Android SDK.

По умолчанию используется OpenFreeMap Liberty style:

```properties
MAP_STYLE_URL=https://tiles.openfreemap.org/styles/liberty
```

При необходимости URL стиля можно переопределить в `local.properties`. Если позже используется платный tile provider с ключом, этот ключ нельзя добавлять в Git.

## Конфиденциальность

Все данные остаются на устройстве. Приложение не отправляет фотографии, координаты, EXIF или идентификаторы файлов на сервер.

## Релизы

APK и AAB не хранятся в Git. Правила релизов описаны в [release.md](release.md).

## Документы веток

Задание текущей ветки описано в [branch.md](branch.md).
