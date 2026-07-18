# Техническое задание: интерактивная тепловая карта фотографий

## 1. Контекст

Ты работаешь в существующем Android-приложении локальной фотогалереи.

Приложение:

* читает фотографии через `MediaStore`;
* хранит локальный индекс в Room;
* получает координаты из EXIF;
* отображает карту через MapLibre;
* поддерживает временную фильтрацию фотографий;
* не отправляет фотографии и координаты на сервер.

Не создавать новый проект и не переписывать существующую карту целиком.

Перед реализацией:

1. Изучить текущую архитектуру карты.
2. Найти существующий `MapView` или Compose-компонент карты.
3. Найти текущие `GeoJsonSource` и MapLibre layers.
4. Найти DAO для фотографий с координатами.
5. Найти текущий экран просмотра фотографии.
6. Определить, используется Compose или XML.
7. Найти существующую нижнюю панель карты.
8. Найти текущий механизм фильтрации по дате.
9. Проверить установленную версию MapLibre.
10. Составить список изменяемых и новых файлов.

MapLibre предоставляет отдельный `HeatmapLayer`, а `GeoJsonSource` позволяет передавать и обновлять `FeatureCollection`. Для выбранной фотографии следует использовать отдельный `SymbolLayer`. ([MapLibre][1])

---

# 2. Цель

Реализовать интерактивную heatmap фотографий, визуально и функционально похожую на карту Google Photos.

Основные требования:

1. При изменении масштаба и положения карты heatmap пересчитывается по фотографиям текущей видимой области.
2. Цвета не должны просто постепенно становиться слабее при увеличении zoom.
3. После остановки камеры интенсивность нормализуется заново относительно текущего viewport.
4. При нажатии на heatmap открывается нижняя мини-галерея фотографий выбранной области.
5. На карте появляется маркер точной координаты текущей фотографии.
6. При горизонтальном перелистывании галереи маркер перемещается к координате новой фотографии.
7. Смена фотографии не должна запускать перерасчёт heatmap.

---

# 3. Общая архитектура

```text
Room с фотографиями
        |
        v
ViewportHeatmapRepository
        |
        v
Camera viewport + zoom + фильтр дат
        |
        v
Получение точек текущей области
        |
        v
Экранная агрегация
        |
        v
Локальная нормализация интенсивности
        |
        v
GeoJsonSource
        |
        v
HeatmapLayer
```

Интерактивное выделение:

```text
Нажатие на heatmap
        |
        v
Координата и экранный радиус нажатия
        |
        v
Поиск фотографий рядом
        |
        +-> нижняя мини-галерея
        |
        +-> выбранная фотография
                |
                v
        selected-photo-source
                |
                v
        SymbolLayer с маркером
```

---

# 4. Структура MapLibre layers

Создать следующие источники:

```text
photo-heatmap-source
selected-photo-source
```

Создать следующие слои:

```text
photo-heatmap-layer
selected-photo-halo-layer
selected-photo-symbol-layer
```

Порядок:

```text
Базовая карта
photo-heatmap-layer
selected-photo-halo-layer
selected-photo-symbol-layer
Подписи и элементы управления
```

`selected-photo-symbol-layer` должен находиться выше heatmap.

---

# 5. Модель данных фотографии

Использовать существующую сущность. При необходимости добавить UI-модель:

```kotlin
data class MapPhoto(
    val id: Long,
    val contentUri: String,
    val thumbnailUri: String?,
    val latitude: Double,
    val longitude: Double,
    val takenAt: Long?,
    val width: Int?,
    val height: Int?
)
```

Heatmap должна учитывать только фотографии:

* с корректными координатами;
* с `isAvailable = true`;
* попадающие в активный временной фильтр;
* не имеющие координат `(0, 0)`;
* не скрытые пользователем.

---

# 6. Состояние экрана

```kotlin
data class PhotoHeatmapUiState(
    val heatmapStatus: HeatmapStatus,
    val visiblePhotoCount: Int,
    val selectedPhotos: List<MapPhoto>,
    val selectedPhotoIndex: Int?,
    val selectedPhoto: MapPhoto?,
    val galleryTotalCount: Int,
    val activeDateFilter: DateRange?,
    val error: String?
)
```

```kotlin
enum class HeatmapStatus {
    IDLE,
    MOVING_CAMERA,
    CALCULATING,
    READY,
    EMPTY,
    ERROR
}
```

---

# 7. Поведение при изменении камеры

## 7.1. Начало движения

При начале пользовательского перемещения карты:

```text
1. Не удалять текущую heatmap.
2. Не запускать расчёт на каждом кадре движения.
3. Пометить состояние MOVING_CAMERA.
4. Закрыть выбранную галерею и скрыть маркер.
```

Закрывать галерею только при движении камеры, вызванном жестом пользователя.

Не закрывать её при внутренних анимациях, связанных с выбранной фотографией.

## 7.2. Остановка камеры

Использовать `OnCameraIdleListener`. Этот callback вызывается после завершения движения камеры. ([MapLibre][2])

После остановки:

```text
1. Подождать debounce 150-250 мс.
2. Получить zoom.
3. Получить видимую область.
4. Увеличить границы на технологический margin.
5. Запросить фотографии или агрегированные heat-ячейки.
6. Выполнить локальную нормализацию.
7. Обновить GeoJsonSource.
8. Только после готовности заменить старую heatmap.
```

Получать видимые границы через `Projection.getVisibleRegion()`. MapLibre также позволяет переводить координаты между экранными пикселями и широтой/долготой через `toScreenLocation()` и `fromScreenLocation()`. ([MapLibre][3])

---

# 8. Viewport request

Создать модель:

```kotlin
data class HeatmapViewportRequest(
    val requestId: Long,
    val zoom: Double,
    val bounds: GeoBounds,
    val viewportWidthPx: Int,
    val viewportHeightPx: Int,
    val density: Float,
    val dateRange: DateRange?
)
```

Границы запроса должны быть немного шире видимой карты:

```text
viewport bounds + 10-15%
```

Это предотвращает обрезание heatmap по краям.

Если карта имеет наклон, использовать реальные углы `VisibleRegion`, а bounding box использовать только как первый фильтр.

---

# 9. Отмена устаревших расчётов

Если пользователь продолжил двигать карту до окончания расчёта:

* предыдущий расчёт отменяется;
* старый результат не применяется;
* запускается обработка нового viewport;
* в UI не должно быть мерцания.

Использовать:

```text
Flow.mapLatest
```

или:

```text
Job.cancel()
```

или проверку `requestId`.

Пример:

```kotlin
if (result.requestId != currentRequestId) {
    return
}
```

---

# 10. Получение фотографий из Room

Добавить пространственный запрос:

```kotlin
@Query(
    """
    SELECT *
    FROM photos
    WHERE isAvailable = 1
      AND latitude BETWEEN :south AND :north
      AND longitude BETWEEN :west AND :east
      AND (:fromDate IS NULL OR dateTaken >= :fromDate)
      AND (:toDate IS NULL OR dateTaken <= :toDate)
    """
)
suspend fun getPhotosForHeatmap(
    south: Double,
    north: Double,
    west: Double,
    east: Double,
    fromDate: Long?,
    toDate: Long?
): List<PhotoEntity>
```

Добавить индексы:

```text
latitude
longitude
dateTaken
isAvailable
```

Для большой библиотеки рассмотреть SQLite RTree или заранее рассчитанные spatial cells.

---

# 11. Уровни детализации

Не передавать 100 000 необработанных точек в MapLibre на низком zoom.

Использовать гибридный подход:

|  Zoom | Источник                                     |
| ----: | -------------------------------------------- |
|   0-6 | крупные предварительно рассчитанные ячейки   |
|  6-10 | средние ячейки                               |
| 10-13 | мелкие ячейки                                |
|   13+ | отдельные фотографии или очень мелкие ячейки |

Можно использовать:

* H3;
* Web Mercator tiles;
* собственную сетку;
* экранные bins.

Предварительная агрегация отвечает только за производительность.

**Финальный вес каждой ячейки всё равно пересчитывается относительно текущего viewport.**

---

# 12. Главный алгоритм heatmap

## 12.1. Проблема стандартного поведения

Нельзя использовать только один глобальный вес, рассчитанный для всей библиотеки.

Пример:

```text
Екатеринбург - 8 000 фотографий
Казань - 100 фотографий
```

На масштабе страны Екатеринбург должен быть сильнее.

Но после приближения к Казани её локальные районы должны снова иметь полноценный диапазон цветов, а не оставаться едва заметными из-за глобального максимума Екатеринбурга.

## 12.2. Правильное поведение

При каждом `camera idle`:

```text
1. Выбрать точки внутри текущего viewport.
2. Агрегировать их в текущем экранном масштабе.
3. Найти локальное распределение количества фотографий.
4. Рассчитать новые веса.
5. Передать новые веса в HeatmapLayer.
```

---

# 13. Агрегация в экранной системе

Размер ячейки задавать в dp, а не в географических градусах.

Начальное значение:

```text
24 dp
```

Пример адаптации:

| Zoom | Cell size |
| ---: | --------: |
|  0-5 |     36 dp |
|  5-9 |     30 dp |
| 9-13 |     24 dp |
|  13+ |     18 dp |

Алгоритм:

```text
1. Перевести координаты фотографий в глобальные Web Mercator pixels.
2. Разделить пиксели на grid cells.
3. Фотографии с одинаковым cell key объединить.
4. Для ячейки вычислить:
   - photoCount;
   - среднюю или взвешенную координату;
   - временной диапазон;
   - cellId.
```

Модель:

```kotlin
data class HeatCell(
    val id: String,
    val latitude: Double,
    val longitude: Double,
    val photoCount: Int,
    val minTakenAt: Long?,
    val maxTakenAt: Long?
)
```

Не выполнять массовый вызов MapLibre `toScreenLocation()` для десятков тысяч точек на UI thread.

Проекцию Web Mercator выполнить самостоятельно в background dispatcher.

---

# 14. Локальная нормализация цветов

Использовать логарифмическую нормализацию, чтобы одна область с огромным числом фотографий не подавляла остальные.

```text
localWeight =
    log(1 + cellPhotoCount)
    /
    log(1 + localReferenceCount)
```

`localReferenceCount` не должен быть простым максимумом.

Использовать:

```text
95-й процентиль количества фотографий в видимых ячейках
```

Это защищает от единичного выброса.

```kotlin
val localReference = percentile(
    values = cells.map { it.photoCount },
    percentile = 0.95
).coerceAtLeast(1)
```

```kotlin
val localWeight =
    ln(1.0 + cell.photoCount) /
    ln(1.0 + localReference)
```

Ограничить:

```text
0.05-1.0
```

## Гибрид с абсолютной плотностью

Чтобы одна фотография не становилась красной только потому, что она единственная на экране:

```text
finalWeight =
    0.8 × localWeight
    +
    0.2 × absoluteWeightForZoom
```

Где `absoluteWeightForZoom` сравнивает количество фотографий с ориентиром текущего zoom.

Начальные ориентиры:

|  Zoom | Плотная ячейка |
| ----: | -------------: |
|   0-5 |       500 фото |
|   5-9 |       150 фото |
|  9-12 |        40 фото |
| 12-15 |        10 фото |
|   15+ |         3 фото |

Значения должны находиться в конфигурации, а не быть разбросаны по коду.

---

# 15. GeoJSON heatmap

Каждая ячейка превращается в Point Feature:

```kotlin
Feature.fromGeometry(
    Point.fromLngLat(
        cell.longitude,
        cell.latitude
    )
).apply {
    addStringProperty("cell_id", cell.id)
    addNumberProperty("photo_count", cell.photoCount)
    addNumberProperty("weight", finalWeight)
}
```

Обновлять существующий `GeoJsonSource`, а не удалять и создавать source/layer заново.

---

# 16. Настройка HeatmapLayer

Пример логики:

```kotlin
val heatmapLayer = HeatmapLayer(
    "photo-heatmap-layer",
    "photo-heatmap-source"
).withProperties(
    heatmapWeight(get("weight")),

    heatmapRadius(
        interpolate(
            linear(),
            zoom(),
            stop(3, 26),
            stop(8, 30),
            stop(13, 34),
            stop(17, 38)
        )
    ),

    heatmapIntensity(1.0f),
    heatmapOpacity(0.82f),

    heatmapColor(
        interpolate(
            linear(),
            heatmapDensity(),
            stop(0.0, rgba(70, 30, 180, 0.0)),
            stop(0.12, rgba(110, 50, 210, 0.75)),
            stop(0.35, rgba(20, 180, 210, 0.85)),
            stop(0.58, rgba(80, 200, 110, 0.90)),
            stop(0.78, rgba(240, 190, 40, 0.95)),
            stop(1.0, rgba(210, 55, 145, 1.0))
        )
    )
)
```

Конкретный синтаксис адаптировать под установленную версию MapLibre.

Цвет `heatmap-color` в MapLibre рассчитывается по `heatmap-density`, а слой поддерживает отдельные свойства веса, интенсивности, радиуса и прозрачности. ([MapLibre][4])

---

# 17. Поведение во время пересчёта

Не очищать heatmap сразу после остановки камеры.

Правильное поведение:

```text
Старая heatmap остаётся
-> идёт расчёт
-> готов новый FeatureCollection
-> source обновляется
```

Допускается:

* плавный crossfade 100-200 мс;
* маленький индикатор загрузки;
* отсутствие индикатора при расчёте меньше 300 мс.

Не показывать пустую карту между состояниями.

---

# 18. Нажатие на heatmap

## 18.1. Событие

При нажатии получить:

```text
screenX
screenY
tapLatLng
currentZoom
currentViewport
```

## 18.2. Радиус выбора

Использовать экранный радиус:

```text
48-64 dp
```

Радиус должен оставаться одинаковым визуально на разных zoom.

Алгоритм:

```text
1. Получить экранную координату нажатия.
2. Рассчитать координаты точек:
   tapX - radius
   tapX + radius
   tapY - radius
   tapY + radius.
3. Преобразовать их в географические координаты.
4. Выполнить предварительный Room-запрос по bounding box.
5. Для результатов вычислить точное расстояние в экранных пикселях.
6. Оставить фотографии внутри selection radius.
```

MapLibre `Projection` поддерживает перевод экранной точки в географическую и обратно. ([MapLibre][5])

## 18.3. Если фотографий нет

Последовательно:

```text
48 dp
-> 64 dp
-> 80 dp
```

Максимальный радиус:

```text
80 dp
```

Если фотографий всё равно нет:

* не открывать галерею;
* не показывать пустую панель;
* сохранить текущую карту без изменений.

---

# 19. Какие фотографии попадают в мини-галерею

В галерею попадают только фотографии:

* в пределах экранного радиуса нажатия;
* соответствующие текущему временному фильтру;
* доступные через MediaStore;
* имеющие координаты.

Максимальное число предварительно загруженных элементов:

```text
300
```

Если найдено больше:

* показывать реальное общее количество;
* загружать страницы лениво;
* не декодировать все миниатюры сразу.

Сортировка:

```text
takenAt DESC
```

Первая выбранная фотография:

```text
фотография с минимальным экранным расстоянием до нажатия
```

Pager открывается на индексе этой фотографии.

---

# 20. Нижняя мини-галерея

Использовать:

```text
BottomSheet
или
закреплённую нижнюю панель
```

Пример структуры:

```text
┌────────────────────────────────┐
│  ━━━━━                         │
│  84 фото · июль 2024           │
│                                │
│ [фото] [выбранное фото] [фото] │
│                                │
│  12 июля 2024 · 18:43          │
└────────────────────────────────┘
```

Содержимое:

* drag handle;
* количество найденных фотографий;
* диапазон дат;
* горизонтальный pager;
* дата текущей фотографии;
* кнопка закрытия;
* действие открытия полноэкранной фотографии.

Для Compose использовать `HorizontalPager`. `PagerState` хранит текущую страницу, что позволяет синхронизировать выбранную фотографию с маркером карты. ([Android Developers][6])

---

# 21. Синхронизация pager и карты

Наблюдать за выбранной страницей:

```kotlin
LaunchedEffect(pagerState) {
    snapshotFlow { pagerState.settledPage }
        .distinctUntilChanged()
        .collect { page ->
            viewModel.selectPhoto(page)
        }
}
```

Если установленная версия Compose не содержит `settledPage`, использовать `currentPage` с debounce.

При изменении страницы:

```text
1. Получить фотографию по индексу.
2. Обновить selectedPhoto в ViewModel.
3. Обновить selected-photo-source.
4. Переместить маркер.
5. Не пересчитывать heatmap.
6. Не запрашивать заново список фотографий.
```

---

# 22. Маркер выбранной фотографии

## 22.1. Source

`selected-photo-source` содержит ровно одну точку:

```kotlin
FeatureCollection.fromFeature(
    Feature.fromGeometry(
        Point.fromLngLat(
            photo.longitude,
            photo.latitude
        )
    )
)
```

## 22.2. Внешний вид

Маркер должен визуально отличаться от heatmap.

Рекомендуемый вариант:

* круглый или каплевидный маркер;
* иконка фотографии или камеры;
* белая рамка;
* фиолетовый или бирюзовый фон;
* небольшой halo;
* размер 36-44 dp.

MapLibre позволяет добавить custom sprite в style и использовать его в `SymbolLayer`. ([MapLibre][7])

## 22.3. Halo

Под маркером добавить CircleLayer:

```text
радиус 22-28 dp
белая или полупрозрачная обводка
```

Это делает маркер читаемым поверх яркой heatmap.

---

# 23. Перемещение маркера при свайпе

При смене фотографии маркер должен не просто исчезать и появляться, а коротко перемещаться к новой координате.

Продолжительность:

```text
150-220 мс
```

Алгоритм:

```text
old latitude/longitude
-> интерполяция
-> new latitude/longitude
```

Использовать `ValueAnimator` или coroutine animation.

Требования:

* одновременно анимируется только один маркер;
* предыдущая анимация отменяется при быстром свайпе;
* heatmap source не обновляется;
* камера не двигается автоматически;
* при выключенных системных анимациях маркер меняется мгновенно.

Если расстояние между фотографиями очень большое, допускается мгновенное перемещение без промежуточной географической линии.

---

# 24. Поведение камеры при перелистывании

По умолчанию камера остаётся неподвижной.

Это важно, чтобы при каждом свайпе:

* не запускался `camera idle`;
* не пересчитывалась heatmap;
* карта не дёргалась;
* пользователь не терял контекст выбранной области.

Если новая точка оказалась вне безопасной видимой области:

```text
не двигать камеру автоматически в MVP
```

Позже можно добавить настройку:

```text
Следовать по карте за фотографиями
```

---

# 25. Открытие полноэкранной фотографии

При нажатии на текущую карточку:

```text
PhotoViewerScreen(
    photoIds = selectedGalleryPhotoIds,
    initialPhotoId = selectedPhotoId
)
```

Не передавать:

* Bitmap;
* полноразмерные изображения;
* `FeatureCollection`;
* весь объект MapLibre.

Передавать стабильные IDs или ID временной selection session.

После возврата:

* восстановить выбранную страницу;
* восстановить маркер;
* сохранить положение камеры;
* не пересчитывать heatmap без изменения viewport.

---

# 26. Selection session

Для большой выборки создать временную сессию:

```kotlin
data class HeatmapSelectionSession(
    val id: String,
    val tapLatitude: Double,
    val tapLongitude: Double,
    val radiusPx: Float,
    val totalCount: Int,
    val photoIds: List<Long>,
    val initialPhotoId: Long
)
```

Сессию хранить во ViewModel или repository cache.

Не сохранять временную выборку в постоянную Room-таблицу без необходимости.

---

# 27. Изменение фильтра дат

При изменении периода:

```text
1. Очистить selection.
2. Закрыть галерею.
3. Скрыть маркер.
4. Запустить новый viewport calculation.
5. Учитывать только фотографии нового периода.
6. Обновить количество фотографий и отображаемый диапазон дат.
```

Примеры:

```text
Все фотографии
2016-2026

Последний год
июль 2025 - июль 2026

Конкретная поездка
10-19 июля 2026
```

---

# 28. Сохранение состояния

При повороте экрана и возврате из Photo Viewer сохранять:

* camera position;
* zoom;
* bearing;
* активный фильтр дат;
* открытую selection session;
* выбранный photo ID;
* индекс pager;
* состояние bottom sheet.

Не сохранять bitmap и MapLibre objects в `SavedStateHandle`.

---

# 29. Состояния ошибок

## Нет фотографий в viewport

```text
Heatmap не отображается
visiblePhotoCount = 0
```

Не показывать глобальную ошибку.

## Ошибка Room

Показать ненавязчивое сообщение:

```text
Не удалось обновить карту
```

Старую heatmap оставить на экране.

## Фотография удалена во время просмотра

* удалить её из pager;
* выбрать соседнюю;
* обновить маркер;
* если список стал пустым, закрыть галерею.

## Thumbnail недоступен

Показать placeholder, не закрывать галерею.

---

# 30. Производительность

Обязательные требования:

* не рассчитывать heatmap на каждом событии `camera move`;
* запускать расчёт только после `camera idle`;
* использовать debounce;
* отменять устаревшие вычисления;
* выполнять агрегацию вне main thread;
* не передавать все фотографии страны на высоком zoom;
* использовать предварительные spatial cells на низком zoom;
* не пересоздавать MapLibre layers;
* обновлять только GeoJSON source;
* не декодировать оригиналы для галереи;
* не обновлять heatmap при свайпе pager;
* не двигать камеру при каждом свайпе;
* не сохранять список photo IDs внутри каждого GeoJSON Feature;
* использовать пагинацию для большой выборки.

`MapLibreMap` и изменения style должны выполняться на UI thread, поэтому тяжёлая агрегация должна завершаться до обновления source. ([MapLibre][8])

---

# 31. Рекомендуемые классы

```text
map/heatmap/PhotoHeatmapController.kt
map/heatmap/ViewportHeatmapRepository.kt
map/heatmap/ViewportHeatmapCalculator.kt
map/heatmap/HeatmapNormalizer.kt
map/heatmap/HeatmapSelectionRepository.kt
map/heatmap/SelectedPhotoMarkerController.kt
map/heatmap/PhotoHeatmapUiState.kt
map/heatmap/PhotoHeatmapViewModel.kt
map/heatmap/ui/HeatmapGallerySheet.kt
map/heatmap/ui/HeatmapPhotoPager.kt
```

Адаптировать под существующую структуру проекта.

---

# 32. Интерфейсы

```kotlin
interface ViewportHeatmapRepository {

    suspend fun calculateHeatmap(
        request: HeatmapViewportRequest
    ): HeatmapResult
}
```

```kotlin
interface HeatmapSelectionRepository {

    suspend fun selectPhotosNearPoint(
        request: HeatmapSelectionRequest
    ): HeatmapSelectionSession?
}
```

```kotlin
data class HeatmapSelectionRequest(
    val latitude: Double,
    val longitude: Double,
    val screenX: Float,
    val screenY: Float,
    val radiusPx: Float,
    val zoom: Double,
    val viewport: GeoBounds,
    val dateRange: DateRange?
)
```

---

# 33. События ViewModel

```kotlin
sealed interface PhotoHeatmapEvent {

    data class CameraIdle(
        val viewport: HeatmapViewportRequest
    ) : PhotoHeatmapEvent

    data class HeatmapTapped(
        val screenX: Float,
        val screenY: Float
    ) : PhotoHeatmapEvent

    data class GalleryPageChanged(
        val photoId: Long
    ) : PhotoHeatmapEvent

    data object CloseGallery : PhotoHeatmapEvent

    data class DateFilterChanged(
        val range: DateRange?
    ) : PhotoHeatmapEvent
}
```

---

# 34. Unit-тесты

## Heatmap normalization

Проверить:

1. Пустой список возвращает пустую heatmap.
2. Одна ячейка не приводит к делению на ноль.
3. Сильный выброс не обесцвечивает остальные ячейки.
4. Вес находится в диапазоне `0-1`.
5. При приближении веса пересчитываются по новому viewport.
6. При одинаковом viewport результат детерминирован.
7. Фотографии вне viewport не влияют на цвета.
8. Временной фильтр влияет на веса.
9. Одна фотография не получает максимальный красный цвет только из-за отсутствия других точек.
10. P95 рассчитывается корректно.

## Selection

Проверить:

1. Выбираются фотографии внутри экранного радиуса.
2. Фотографии вне радиуса не выбираются.
3. Радиус увеличивается при пустом результате.
4. Применяется текущий фильтр дат.
5. Первая фотография является ближайшей к нажатию.
6. Галерея не создаётся при пустом результате.
7. Недоступные фотографии исключаются.

## Marker

Проверить:

1. Выбор фотографии обновляет маркер.
2. Смена страницы не пересчитывает heatmap.
3. Закрытие галереи скрывает маркер.
4. Удаление выбранного фото выбирает соседнее.
5. Быстрые свайпы отменяют предыдущую анимацию.

---

# 35. UI-тесты

Проверить:

1. Карта отображает heatmap.
2. После zoom heatmap пересчитывается.
3. Цвета локальной области становятся выразительными после приближения.
4. Во время движения карта не мерцает.
5. Нажатие на плотную область открывает галерею.
6. На карте появляется маркер выбранной фотографии.
7. Свайп галереи изменяет фотографию.
8. Маркер перемещается к новой координате.
9. Карта не меняет zoom при свайпе.
10. Heatmap не пересчитывается при свайпе.
11. Закрытие галереи скрывает маркер.
12. Движение карты жестом закрывает галерею.
13. Нажатие на фотографию открывает полноэкранный viewer.
14. После возврата восстанавливается выбранная фотография.

---

# 36. Ручные сценарии

## Сценарий 1 - масштаб страны

На карте 10 000 фотографий.

Ожидается:

* видны основные регионы активности;
* крупнейшие области окрашены ярче;
* карта работает без ANR.

## Сценарий 2 - приближение к городу

Пользователь приближает Екатеринбург.

Ожидается:

* после остановки камеры веса рассчитываются заново;
* разные районы получают полный диапазон цветов;
* heatmap не становится бледной из-за глобального максимума страны.

## Сценарий 3 - приближение к центру

Ожидается:

* появляются отдельные локальные зоны активности;
* плотные улицы и объекты выделяются;
* радиус визуально остаётся читаемым.

## Сценарий 4 - нажатие

Ожидается:

* открывается галерея фотографий рядом с нажатием;
* выбирается ближайшая фотография;
* появляется точный маркер.

## Сценарий 5 - перелистывание

Ожидается:

* фотография в pager меняется;
* подпись даты меняется;
* маркер перемещается;
* карта и heatmap остаются неподвижными.

---

# 37. Критерии приёмки

Задача считается выполненной, когда:

* heatmap отображает фотографии текущего временного фильтра;
* данные выбираются по текущему viewport;
* после zoom выполняется новый расчёт;
* фотографии вне viewport не влияют на локальную интенсивность;
* используется локальная нормализация;
* цвета не ослабевают только из-за глобальной плотности всей библиотеки;
* на низком zoom не передаются все сырые фотографии;
* устаревшие расчёты отменяются;
* старая heatmap остаётся до готовности новой;
* нажатие на heatmap открывает мини-галерею;
* в галерею попадают фотографии рядом с точкой нажатия;
* появляется маркер точной координаты выбранной фотографии;
* свайп галереи перемещает маркер;
* свайп галереи не двигает камеру;
* свайп галереи не запускает новый heatmap calculation;
* удалённые фотографии обрабатываются без падения;
* экран сохраняет своё состояние;
* добавлены unit- и UI-тесты;
* существующий экран карты не сломан.

---

# 38. Проверка после реализации

Запустить:

```text
unit tests
UI tests
Android lint
debug build
```

Пример:

```powershell
.\gradlew test
.\gradlew lint
.\gradlew assembleDebug
```

Если используются отдельные модули, адаптировать команды.

---

# 39. Итоговый отчёт Codex

После реализации предоставить:

1. Краткое описание архитектуры.
2. Список созданных файлов.
3. Список изменённых файлов.
4. Описание viewport-пересчёта.
5. Описание локальной нормализации.
6. Описание уровней агрегации.
7. Описание обработки camera idle.
8. Описание выбора фотографий по нажатию.
9. Описание мини-галереи.
10. Описание синхронизации pager и маркера.
11. Результаты тестов.
12. Результаты lint.
13. Результат сборки.
14. Известные ограничения.
15. Инструкцию ручной проверки.

Не ограничиваться сообщением «готово».

[1]: https://maplibre.org/maplibre-native/android/api/-map-libre%20-native%20-android/org.maplibre.android.style.layers/-heatmap-layer/index.html?utm_source=chatgpt.com "HeatmapLayer"
[2]: https://maplibre.org/maplibre-native/android/api/-map-libre%20-native%20-android/org.maplibre.android.maps/-map-libre-map/-on-camera-idle-listener/index.html?utm_source=chatgpt.com "OnCameraIdleListener"
[3]: https://maplibre.org/maplibre-native/android/api/-map-libre%20-native%20-android/org.maplibre.android.maps/-projection/get-visible-region.html?utm_source=chatgpt.com "getVisibleRegion"
[4]: https://maplibre.org/maplibre-style-spec/layers/?utm_source=chatgpt.com "Layers - MapLibre Style Spec"
[5]: https://maplibre.org/maplibre-native/android/api/-map-libre%20-native%20-android/org.maplibre.android.maps/-projection/index.html?utm_source=chatgpt.com "Projection"
[6]: https://developer.android.com/develop/ui/compose/layouts/pager?utm_source=chatgpt.com "Pager in Compose"
[7]: https://maplibre.org/maplibre-native/android/examples/styling/custom-sprite/?utm_source=chatgpt.com "Add Custom Sprite - MapLibre Android Examples"
[8]: https://maplibre.org/maplibre-native/android/api/-map-libre%20-native%20-android/org.maplibre.android.maps/-map-libre-map/index.html?utm_source=chatgpt.com "MapLibreMap"
