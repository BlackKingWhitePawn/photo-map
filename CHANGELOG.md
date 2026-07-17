# Changelog

Все значимые изменения проекта фиксируются в этом файле.

Формат основан на Keep a Changelog, версии следуют SemVer.

## [Unreleased]

Изменений пока нет.

## [0.11.0] - 2026-07-18

### Added

* Added a new `home` start screen with a vertically scrolling personal geography dashboard.
* Added a compact trip heatmap preview at the top of the home screen using the existing MapLibre heatmap layer and ready heat cells.
* Added a featured trips carousel with cover photos, date ranges, and trip photo counts.
* Added popular places cards derived from already indexed local photos with coordinates.
* Added an all-places screen and a place detail screen with mini-galleries grouped by day.
* Added an "On this day in the past" home block with a dedicated map preview and photo thumbnails.
* Added reusable `MiniPhotoGallery` and `MiniPhotoThumbnail` components for shared mini-gallery UI.

### Changed

* App navigation now starts at the home screen; the full photo map remains available as a separate route.
* Permission success navigation now returns to the home screen instead of opening the full map directly.
* Android app metadata is now `versionName=0.11.0`, `versionCode=17`.

## [0.10.0] - 2026-07-18

### Added

* Added a two-level trip heatmap built from detected trips and destinations instead of raw photo counts.
* Added local trip heatmap tables in `photo_index.db` for aggregated heat cells, per-trip contributions, and heatmap metadata.
* Added H3-backed heat aggregation at resolutions 4, 5, 6, 7, 8, and 9 with a deterministic local fallback grid.
* Added trip heatmap intensity based on trip count, days spent, active days, and photo session count.
* Added background `TripHeatmapWorker` scheduling through WorkManager after media permissions are available.
* Added GeoJSON mapping for ready heat cells and a MapLibre `HeatmapLayer` rendered below photo clusters and trip markers.
* Added visible heatmap diagnostics to the map debug panel and settings diagnostics.

### Changed

* Main map and trip map now request ready heatmap cells for the current viewport and zoom instead of calculating heat data during rendering.
* Opening the trip map from the normal map now preserves the current map center and zoom instead of resetting the camera.
* Android app metadata is now `versionName=0.10.0`, `versionCode=16`.

### Fixed

* Trip heatmap layer is now attached to both the main photo map and the trip map, so the heatmap remains visible behind trip markers.

## [0.9.0] - 2026-07-18

### Added

* Added semantic trip segmentation from locally indexed geotagged photos.
* Added local trip storage in `photo_index.db` with trip markers, photo links, and destinations.
* Added stored trip place names in `photo_index.db`, resolved during trip segmentation.
* Added a `Поездки` FAB on the main map.
* Added a separate dark MapLibre trip map using `TRIP_MAP_STYLE_URL`, defaulting to OpenFreeMap Dark.
* Added trip markers rendered as photo thumbnails with `+N` photo count badges.
* Added a trip detail route with platform geocoder place names in the title and trip dates in the subtitle.
* Added a skeleton header state while the trip place name is being resolved.
* Added a smoothed dark-map route line from the first chronological trip photo to the last.
* Added clickable photo thumbnails on route points that scroll the trip gallery to the selected photo.
* Added read-only opening of trip grid photos in the default Android gallery.
* Added a grid gallery of the selected trip photos below the trip route map.
* Added a long right-edge trip timeline scrubber that snaps to trip points and shows semi-transparent trip labels beside the marks.
* Added a collapsed `...` entry button for the trip timeline scrubber.
* Changed trip timeline dragging to wheel-like scrolling with minimal edge padding.
* Highlighted the active trip timeline point with accent color and a border; single-trip timelines render as a point without a line.
* Added a matching right-edge photo scrubber on the trip detail screen for jumping between photos inside a trip.
* Added a trip detail centering action that resets the route map zoom to the whole trip.
* Added Navigation Compose and moved app navigation to `NavHost`.

### Changed

* Bumped Android app metadata to `versionName=0.9.0`, `versionCode=15`.

### Fixed

* Refreshed the main map viewport after scan, camera fit, and returning from trip screens so normal photo markers appear without moving the map.
* Preserved the selected trip map zoom and center when returning from a trip detail screen.
* Blocked accidental overlay taps for a short time after map gestures so pinch/drag/zoom does not open trips or clusters.
* Fixed severe map lag caused by updating Compose state on every camera movement while blocking accidental taps.

## [0.8.2] - 2026-07-17

### Fixed

* Fixed singleton stored clusters so a photo with `photoCount=1` is exposed to the map as a single photo marker instead of a cluster.
* Fixed duplicated single-photo rendering where the same item could appear as both a cluster and a photo marker.
* Fixed the visible marker artifact by clearing the duplicate MapLibre marker sources and rendering map markers through the Compose overlay only.

## [0.8.1] - 2026-07-17

### Added

* Added date picker access from the date range labels for precise start and end date selection.
* Added month and year tick marks behind the map date range slider.

### Changed

* The date range slider now zooms its visible scale around the selected range and expands back toward the full date span when dragged to the edges.
* Date range slider handles now snap to nearby month, year, and boundary ticks.

### Fixed

* The map no longer shows the "Нет фотографий с геопозицией" empty state when an active date filter simply hides all visible photos.

## [0.8.0] - 2026-07-17

### Added

* Added a date range filter on the map with a second FAB and a two-sided range slider.
* The date FAB shows the selected range as `dd.MM.yy - dd.MM.yy`.
* The date slider shows the active drag range as `dd MMMM yyyy`.

### Changed

* Map cluster rebuilding now runs as a cancellable asynchronous operation when the date filter, cluster density, or indexed photo set changes.
* Visible viewport cluster loading is cancellable so stale map movement queries cannot overwrite newer results.
* Screen-level marker compaction now runs off the main thread and cooperatively cancels during rapid map movement.
* Cluster density changes are committed after the slider is released, matching the new date filter behavior.

## [0.7.2] - 2026-07-17

### Fixed

* Fixed clusters whose photos share the same geotag so tapping them opens the mini gallery instead of endlessly zooming.
* Fixed overlay markers during map movement: marker positions now reproject while the camera moves, but cluster composition is recalculated only after camera idle.
* Fixed map lag and visual clutter by compacting nearby dense marker groups into larger screen clusters.
* Fixed single-photo map markers so square thumbnails are no longer clipped by a circular marker shape.
* Fixed EXIF scanning so newly indexed geotagged photos appear on the map during scanning, and cancelled EXIF scans resume from the last saved mark when started again.
* Changed the first gallery permission grant to start GPS lookup in EXIF immediately instead of waiting for a separate deep scan action.

## [0.7.0] - 2026-07-16

### Added

* Added explicit permission request flow handling for missing photo and media-location access.
* Added a mini gallery for large map clusters with horizontal scrolling.
* Added an animated density FAB that shows the current cluster density percent and expands into a full-width slider.

### Changed

* Removed the map title and back button from the map screen.
* Moved map settings and centering actions into compact icon-only controls in the top bar.
* Colored the top safe area with the map top bar surface and padded controls for status bars and display cutouts.
* Replaced custom zoom/settings assets with standard Android drawable icons.
* Removed the scan action from settings.
* Changed visible map cluster tuning so density affects runtime clustering and distance limits.
* The map debug panel now defaults to hidden for new installs.

### Fixed

* Fixed cluster gallery taps so tapping another cluster refreshes an already-open mini gallery.
* Fixed cluster counts by building visible clusters from real photo IDs when available.
* Fixed mini gallery controls by replacing the close button with an icon and removing zoom/load-more actions.
* Kept Java compile compatibility at 1.8 so AGP does not require the `androidJdkImage`/`jlink` transform in this project setup.

## [0.6.0] - 2026-07-16

### Added

* Added a Compose overlay for map markers so clusters and photo thumbnails stay visible above the MapLibre style.
* Added thumbnail rendering for overlay cluster markers and single-photo markers.
* Added a map debug panel with the count of visible clusters and their coordinates.
* Added a settings toggle to show or hide the map debug panel, persisted in local settings.
* Added unit-test coverage for marker render rules and debug information formatting.

### Fixed

* Fixed invisible map clusters and thumbnails by rendering marker UI independently of MapLibre style layers.
* Fixed overlay marker positions so they are recalculated while the camera moves, instead of waiting for camera idle.
* Fixed single-photo stored items so they render as photo markers instead of clusters.
* Moved cluster counts into a small badge so they do not cover the thumbnail.
* Added direct overlay marker taps: clusters zoom the map, and single photos open the preview panel.

## [0.5.1] - 2026-07-15

### Исправлено

* Центр групповой миниатюры на карте теперь считается по среднему арифметическому координат фотографий группы, а не по центру сеточной ячейки.
* Распад групповых миниатюр на одиночные фотографии проверяет реальные экранные границы `MapView`, чтобы фотографии не пропадали за краем клиентского окна.
* Одиночные фотографии на карте отображаются как фото-миниатюры без подписи `+N фото`.
* Клик по групповой миниатюре масштабирует карту так, чтобы все фотографии этой группы попали в экран.
* Клик по одиночной фотографии открывает нижнюю галерею с превью.

### Изменено

* Heatmap-маркеры сделаны шире фото-миниатюр.
* Размер фото-миниатюр увеличивается при приближении карты.
* Кнопки управления картой перенесены в верхнюю панель; стартовый экран также получил верхнюю панель.
* Локальное правило Codex обновлено: build/compile checks не запускать без явного запроса пользователя.

## [0.5.0] - 2026-07-15

### Добавлено

* Добавлено heatmap-отображение фотографий на карте вместо чистых точек.
* Добавлены маркеры с миниатюрой фотографии для групп, достигших настраиваемого порога.
* Добавлен экран настроек с изменением порога появления heatmap-миниатюр.
* Добавлены пауза, продолжение и отмена активного индексирования.
* Добавлена кнопка отзыва доступа через системные настройки приложения.
* Добавлена иконка настроек для кнопок перехода в настройки.

### Изменено

* Карта остается доступной во время активного индексирования и наполняется уже обработанными batch-результатами.
* Основная кнопка доступа меняет состояние между `Предоставить доступ` и `Отозвать доступ`.
* Навигация хранит локальный стек экранов и возвращает пользователя на предыдущий экран по системному событию `назад`.
* В локальный workflow Codex добавлено обязательное правило запускать compile check после патчей.

### Ограничения

* Compile check в sandbox не дошел до Kotlin-компиляции: Gradle не смог разрешить `org.gradle.toolchains.foojay-resolver-convention:1.0.0` без сетевого доступа к plugin repositories.

## [0.4.0] - 2026-07-15

### Добавлено

* Добавлен локальный SQLite-индекс обработанных фотографий.
* Добавлено сохранение прогресса глубокого EXIF-сканирования между запусками.
* Добавлена статистика GPS-индекса на экране доступа.

### Изменено

* Повторный deep scan переиспользует уже обработанные неизменённые фотографии.
* Переименование фотографии обновляет metadata в индексе без удаления оригинала.
* Записи удалённых из MediaStore фотографий удаляются только из локального индекса.
* Прогресс сканирования обновляется по времени и с процентом, а не шагом 500.

## [0.3.1] - 2026-07-15

### Добавлено

* Добавлен отдельный ручной поиск GPS-координат в EXIF для больших галерей.
* Добавлен прогресс сканирования MediaStore/EXIF с пакетным обновлением.
* Добавлены групповые маркеры карты с отображением количества фотографий.

### Изменено

* Автоматический скан после запуска больше не читает EXIF всех фотографий.
* Карта после первичного центрирования строит маркеры только для видимой области.
* Количество фотографий с координатами хранится в `UiState`, чтобы Compose не пересчитывал 40k элементов при перерисовке.
* Убрано Logcat-логирование каждой фотографии во время сканирования.

## [0.3.0] - 2026-07-15

### Добавлено

* Добавлен первый экран карты на MapLibre без Google Maps API key.
* Добавлено read-only чтение GPS-координат из MediaStore/EXIF.
* Добавлено отображение фотографий с валидными координатами на карте.
* Добавлена базовая группировка близких точек на карте.
* Добавлена настройка `MAP_STYLE_URL` через `local.properties`.
* По умолчанию подключён OpenFreeMap Liberty style без регистрации, API key и billing.

### Изменено

* Требование Google Maps заменено на MapLibre, чтобы убрать зависимость от Google Play Services и Google Maps billing.
* Слои границ стран скрываются после загрузки стиля карты.

### Безопасность

* Удалены транзитивные runtime-разрешения `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION` и `ACCESS_WIFI_STATE`, подтянутые MapLibre SDK.

## [0.2.0] - 2026-07-15

### Добавлено

* Добавлены runtime-разрешения для чтения фотографий на Android 10-14+.
* Добавлен запрос `ACCESS_MEDIA_LOCATION` для будущего чтения исходных геоданных.
* Добавлен MediaStore reader для чтения изображений через `ContentResolver`.
* Добавлена доменная модель `DevicePhoto`.
* Добавлен экран доступа к фотографиям с состоянием разрешений, повторным сканированием и переходом в настройки.
* Добавлен `PhotoAccessViewModel` со StateFlow-состоянием.

### Изменено

* `MainActivity` теперь запускает экран доступа к фотографиям вместо шаблонного Compose-экрана.

### Безопасность

* Добавлен `SECURITY.md` со строгим запретом удаления, изменения, перемещения, перезаписи и отправки оригинальных фотографий пользователя.
* Проверено, что приложение не запрашивает write/manage-разрешения для внешнего хранилища.

## [0.1.0] - 2026-07-15

### Добавлено

* Подготовлены правила веток, коммитов, версионирования и релизов.
* Добавлено правило `branch.md` для задания каждой рабочей ветки.
* Добавлены правила хранения APK/AAB вне Git.
* Инициализирован Android-проект `Photo Map`.
* Добавлена базовая Gradle-конфигурация.
* Добавлен модуль `app` с package name `com.example.photomap`.
* Добавлен минимальный Compose-интерфейс.

### Изменено

* Уточнён `.gitignore` для Android-сборок, бинарных артефактов, signing-файлов, логов и IDE-файлов.

### Ограничения

* Это bootstrap-релиз без MediaStore, Room, карты, галереи и фонового сканирования.
* Текущий signed APK нужно пересобрать после обновления `versionName` до `0.1.0`, чтобы внутренняя версия APK совпала с релизным тегом.
