Тут нужен не обычный DBSCAN/HDBSCAN, а семантическое сопоставление фотографий с географическими объектами.

Главная идея:

Место определяется не центром группы фотографий, а стабильным ID реального географического объекта.

Поэтому:

все фотографии внутри Екатеринбурга относятся к одному месту Екатеринбург;
фотографии внутри Верх-Исетского района одновременно относятся к Екатеринбургу и Верх-Исетскому району;
фотография в парке может одновременно относиться к городу, району и парку;
несколько объектов могут пересекаться;
один объект может входить сразу в несколько более крупных объектов.

Это должна быть не строгая древовидная структура, а ориентированный граф географических объектов.

1. Три сущности

Нужно разделить:

Географический объект

Реальный объект из картографического справочника:

data class GeoObject(
    val id: String,
    val source: GeoSource,
    val sourceObjectId: String,
    val name: String,
    val type: GeoObjectType,
    val geometry: GeoGeometry?,
    val centerLatitude: Double,
    val centerLongitude: Double,
    val importance: Double
)

Примеры:

osm:relation:79379 - Екатеринбург
osm:relation:123456 - Верх-Исетский район
osm:way:456789 - парк
osm:node:987654 - кофейня

Одинаковый объект определяется по source + sourceObjectId, а не по названию.

Два разных объекта с названием «Центральный парк» должны оставаться разными местами.

Связь фотографии с объектом
data class PhotoGeoObjectLink(
    val photoId: String,
    val geoObjectId: String,
    val matchType: GeoMatchType,
    val confidence: Double
)

Одна фотография может иметь много связей:

Фотография 123
├── Россия
├── Свердловская область
├── Екатеринбург
├── Верх-Исетский район
├── Юго-Западный лесопарк
└── конкретный музей
Пользовательское место

Это накопленная статистика пользователя по одному географическому объекту:

data class UserPlace(
    val geoObjectId: String,
    val photoCount: Int,
    val sessionCount: Int,
    val visitCount: Int,
    val activeDayCount: Int,
    val activeMonthCount: Int,
    val tripCount: Int,
    val firstVisitedAt: Long,
    val lastVisitedAt: Long,
    val score: Double,
    val coverPhotoId: String?
)

Екатеринбург создаётся один раз, после чего все подходящие фотографии увеличивают его статистику.

2. Какие географические данные нужны

Нужен локальный каталог объектов двух классов.

Территориальные объекты
страна;
регион;
муниципалитет;
город;
административный район;
микрорайон;
квартал;
населённый пункт.

В OpenStreetMap административные территории обычно представлены полигонами или мультиполигонами с boundary=administrative и admin_level. OSM также предупреждает, что административные границы могут пересекаться и не всегда имеют единственного родителя.

У Overture Maps есть отдельные сущности division и division_area: от страны и региона до района, neighborhood и microhood, причём division_area содержит Polygon или MultiPolygon.

Именованные объекты
парк;
лес;
озеро;
гора;
пляж;
университет;
торговый центр;
музей;
стадион;
вокзал;
аэропорт;
достопримечательность;
кафе;
конкретное здание.

Overture Places, например, содержит реальные организации, учреждения, достопримечательности и другие именованные сущности с ID, названием и категорией.

Для первой версии лучше выбрать один источник объектов. Иначе понадобится отдельная процедура объединения одинаковых объектов из OSM, Overture и других справочников.

3. Основной алгоритм

Алгоритм называется примерно так:

Hierarchical multi-label spatial object matching

По-русски:

Иерархическое многозначное сопоставление координат с географическими объектами.

Этап 1. Выбор кандидатов

Для каждой фотографии:

latitude
longitude
takenAt

Находим географические объекты, bounding box которых пересекается с координатой фотографии.

Не нужно проверять точку против каждого полигона города. Используется пространственный индекс:

R-tree;
STRtree;
SQLite RTree.
val candidates = spatialIndex.query(
    Envelope(
        photo.longitude,
        photo.longitude,
        photo.latitude,
        photo.latitude
    )
)

JTS предоставляет STRtree для двухмерного пространственного индексирования. Сам индекс используется как быстрый предварительный фильтр, после которого всё равно нужна точная проверка геометрии.

Этап 2. Точная проверка попадания в полигон

Для каждого кандидата проверяется:

покрывает ли полигон координату фотографии

Логика должна считать точку на границе частью объекта:

fun matches(
    point: Coordinate,
    objectGeometry: Geometry
): Boolean {
    return PointLocator().intersects(point, objectGeometry)
}

PointLocator.intersects() возвращает true, если точка находится внутри геометрии или на её границе. Для большого количества точек можно использовать IndexedPointInAreaLocator.

В терминах пространственных баз это соответствует ST_Covers, который, в отличие от некоторых вариантов contains, учитывает также границу полигона.

Этап 3. Сохранение всех подходящих объектов

Нельзя выбирать только самый маленький или ближайший объект.

Для координаты сохраняются все допустимые совпадения:

val matches = candidates.filter { objectCandidate ->
    objectCandidate.geometry?.covers(photo.point) == true
}

Пример:

Фото на Плотинке
├── Екатеринбург
├── Ленинский район
├── Исторический сквер
└── Плотинка

В этом и заключается главное отличие от обычной кластеризации.

4. Иерархия должна быть графом

Не стоит хранить только одно поле:

parentPlaceId: String?

У объекта потенциально может быть несколько родителей.

Правильнее:

data class GeoObjectRelation(
    val parentId: String,
    val childId: String,
    val relationType: GeoRelationType
)

Типы связей:

enum class GeoRelationType {
    ADMINISTRATIVE_PARENT,
    CONTAINS,
    LOCATED_IN,
    OVERLAPS,
    PART_OF
}

Пример:

Екатеринбург
└── Верх-Исетский район

Верх-Исетский район
└── парк

Екатеринбург
└── парк

Парк может быть непосредственно связан и с городом, и с районом.

Почему не обычное дерево

Реальная география содержит:

пересекающиеся административные территории;
природные зоны, пересекающие несколько районов;
университетские кампусы внутри районов;
парки, находящиеся в нескольких муниципальных образованиях;
неофициальные микрорайоны поверх официальных административных районов.

Поэтому структура:

DAG - directed acyclic graph

лучше обычного дерева.

5. Объекты без полигонов

Не у каждого объекта есть полноценный полигон.

Например, кафе может быть представлено точкой.

Тогда используется поиск ближайшего именованного объекта, но с разными радиусами по категориям.

Тип	Максимальное расстояние
Кафе, магазин, небольшое здание	30-50 м
Музей, торговый центр	50-100 м
Вокзал	100-200 м
Аэропорт	500-1500 м
Гора, природный объект	500-3000 м
Город без доступной границы	индивидуальный радиус

Формула уверенности:

confidence = 1 - distance / maximumDistance

Например:

fun proximityConfidence(
    distanceMeters: Double,
    maximumDistanceMeters: Double
): Double {
    return (
        1.0 - distanceMeters / maximumDistanceMeters
    ).coerceIn(0.0, 1.0)
}

Приоритет сопоставления:

1. Точное попадание в полигон объекта.
2. Попадание в полигон здания.
3. Ближайший точечный POI с высокой уверенностью.
4. Только административные объекты.
5. Синтетическое место, если ничего не найдено.
6. Как не создавать тысячи бессмысленных мест

Если сохранять каждый объект OSM, фотография может получить связи с:

жилой зоной;
почтовым индексом;
парковкой;
дорогой;
земельным участком;
технической территорией;
несколькими служебными boundary.

Поэтому нужен whitelist.

Административные объекты
country
region
county
city
town
village
borough
district
neighborhood
microhood
Значимые площади
park
forest
nature_reserve
lake
beach
university
campus
cemetery
industrial_site
historic_area
Значимые POI
museum
gallery
attraction
monument
stadium
mall
airport
station
cafe
restaurant
hotel
Не создавать пользовательские места из
landuse=residential
postcode
parking_space
power
utility
technical boundary
unnamed building
unnamed road segment
7. Разделение по смысловым уровням

Все найденные объекты стоит разложить по фасетам:

enum class PlaceFacet {
    COUNTRY,
    REGION,
    CITY,
    DISTRICT,
    NEIGHBORHOOD,
    NATURAL_AREA,
    LANDMARK,
    VENUE,
    BUILDING,
    TRANSPORT
}

Внутри одного фасета можно выбирать наиболее конкретный объект.

Например:

CITY:
Екатеринбург

DISTRICT:
Верх-Исетский район

NATURAL_AREA:
Шарташский лесопарк

VENUE:
конкретное кафе

При этом объекты из разных фасетов не конкурируют друг с другом.

8. Как формируется пользовательское место

Сам географический объект существует независимо от фотографий.

Пользовательское место создаётся после первого совпадения:

фотография попала в GeoObject
-> создаётся или обновляется UserPlace
fun updateUserPlace(
    geoObjectId: String,
    photos: List<Photo>
): UserPlace

Но статус «популярное место» рассчитывается отдельно.

Нельзя считать только фотографии

Сто фотографий за пять минут не должны означать сто посещений.

Сначала фотографии группируются в фотосессии:

разрыв по времени <= 2 часа
и
фотографии относятся к одному объекту

Затем фотосессии группируются в посещения.

Пример начальных временных разрывов:

Тип объекта	Новый визит после разрыва
Кафе, музей, магазин	4 часа
Парк, микрорайон	12 часов
Район	24 часа
Город	36 часов
Регион	72 часа

Это стартовые эвристики, которые нужно проверять на реальных данных.

9. Оценка популярности

Для каждого объекта:

photoCount
sessionCount
visitCount
activeDays
activeMonths
tripCount
lastVisitedAt

Пример формулы:

placeScore =
    3.0 * log(1 + visitCount)
    + 2.0 * log(1 + activeDays)
    + 1.5 * log(1 + activeMonths)
    + 1.0 * log(1 + tripCount)
    + 0.3 * log(1 + photoCount)

Количество фотографий имеет небольшой вес.

Пример:

500 фотографий за один день

не должно автоматически обгонять:

20 отдельных посещений в течение двух лет
Создание места и популярность - разные вещи
UserPlace существует:
если есть хотя бы одна фотография

Популярное место:
если score превышает порог

Например:

popular =
    visitCount >= 2
    OR activeDays >= 3
    OR tripCount >= 2

Для достопримечательности можно разрешить одно значимое посещение:

одна поездка
+
не меньше 3 фотосессий
10. Как избежать дублирования в блоке «Популярные места»

В базе одновременно могут быть:

Екатеринбург - 10 000 фотографий
Верх-Исетский район - 4 000 фотографий
Исторический сквер - 800 фотографий

Это правильно.

Но если показать их подряд на главной, карточки будут ощущаться как дубли.

Поэтому хранение и отображение нужно разделить.

В базе

Сохранять все три объекта.

На главной

Использовать hierarchy-aware ranking.

Алгоритм:

Отсортировать места по placeScore.
Выбрать первое.
Для оставшихся снижать рейтинг, если они являются родителем или потомком уже выбранного места.
Снижать рейтинг, если наборы фотографий почти полностью совпадают.
Продолжать до получения нужного количества карточек.
displayScore =
    placeScore
    * hierarchyPenalty
    * photoOverlapPenalty

Пример:

Екатеринбург выбран первым

Верх-Исетский район:
score уменьшается на 30%

Любимая кофейня:
score не уменьшается

Шарташ:
score не уменьшается

Для сравнения наборов фотографий:

overlap =
    intersection(photoIdsA, photoIdsB)
    /
    min(sizeA, sizeB)

Если:

overlap > 0.85

одну из карточек стоит понизить в выдаче.

11. Синтетические места

Не все интересные локации есть в географическом справочнике.

Например:

заброшенный объект;
неизвестная шахта;
лесная стоянка;
точка на берегу;
неразмеченное здание.

Только для таких фотографий используется обычная кластеризация:

Фотографии без подходящего GeoObject
-> DBSCAN/HDBSCAN
-> синтетические места

Пример:

data class SyntheticPlace(
    val id: String,
    val centerLatitude: Double,
    val centerLongitude: Double,
    val radiusMeters: Double,
    val generatedName: String
)

Название:

Место рядом с Режом
Неизвестное место
Локация в 12 км от Екатеринбурга

Получается гибрид:

Известные географические объекты
-> polygon/POI matching

Неизвестные территории
-> пространственная кластеризация

Это гораздо лучше, чем кластеризовать все фотографии только по расстоянию.

12. Схема алгоритма целиком
1. Прочитать фотографию и её EXIF.

2. Получить координаты.

3. Найти через пространственный индекс
   географические объекты рядом с точкой.

4. Проверить точное попадание в полигоны.

5. Найти ближайшие точечные POI
   по радиусам конкретных категорий.

6. Отфильтровать технические
   и незначимые объекты.

7. Сохранить все релевантные совпадения:
   город, район, парк, venue и так далее.

8. Объединить совпадения по стабильному ID объекта.

9. Обновить UserPlace для каждого объекта.

10. Пересчитать фотосессии и посещения
    только для затронутых объектов.

11. Обновить placeScore.

12. Для фотографий без объекта
    запустить fallback-кластеризацию.

13. Сохранить всё в Room.

14. Главная страница читает готовые места
    и не выполняет географический анализ.
13. Структура Room
@Entity(tableName = "geo_objects")
data class GeoObjectEntity(
    @PrimaryKey
    val id: String,
    val source: String,
    val sourceObjectId: String,
    val name: String,
    val type: String,
    val facet: String,
    val geometryData: ByteArray?,
    val centerLatitude: Double,
    val centerLongitude: Double,
    val minLatitude: Double,
    val maxLatitude: Double,
    val minLongitude: Double,
    val maxLongitude: Double,
    val importance: Double,
    val dataVersion: Int
)
@Entity(
    tableName = "geo_object_relations",
    primaryKeys = ["parentId", "childId", "relationType"]
)
data class GeoObjectRelationEntity(
    val parentId: String,
    val childId: String,
    val relationType: String
)
@Entity(
    tableName = "photo_geo_object_links",
    primaryKeys = ["photoId", "geoObjectId"]
)
data class PhotoGeoObjectLinkEntity(
    val photoId: String,
    val geoObjectId: String,
    val matchType: String,
    val confidence: Double
)
@Entity(tableName = "user_places")
data class UserPlaceEntity(
    @PrimaryKey
    val geoObjectId: String,
    val photoCount: Int,
    val sessionCount: Int,
    val visitCount: Int,
    val activeDayCount: Int,
    val activeMonthCount: Int,
    val tripCount: Int,
    val firstVisitedAt: Long?,
    val lastVisitedAt: Long?,
    val score: Double,
    val coverPhotoId: String?,
    val updatedAt: Long
)
14. Фоновая обработка
GeoDataImportWorker
    загружает или обновляет каталог объектов

PhotoPlaceMatchingWorker
    сопоставляет новые фотографии с объектами

PlaceVisitAggregationWorker
    строит посещения

PlaceScoreWorker
    обновляет популярность

При добавлении одной фотографии пересчитываются только:

её связи;
связанные географические объекты;
посещения этих объектов;
их рейтинги.

Не нужно повторно анализировать всю библиотеку.

15. Почему одного Nominatim недостаточно

Обычный reverse geocoding Nominatim ищет ближайший подходящий OSM-объект и возвращает один результат с адресными составляющими. Он не предназначен для выдачи полного множества пересекающихся объектов для каждой фотографии.

Кроме того, публичный сервер Nominatim ограничивает тяжёлое использование и не рекомендует регулярный массовый reverse geocoding из приложений. Для полного набора объектов официальная политика предлагает использовать выгрузки OSM или собственный сервис.

Поэтому для локального приложения лучше:

компактная локальная база геообъектов
+
пространственный индекс
+
point-in-polygon
+
локальный кэш результатов
Итоговый вариант

Лучший алгоритм для вашей задачи:

Каталог реальных географических объектов
+
стабильные ID объектов
+
R-tree для поиска кандидатов
+
point-in-polygon для полигонов
+
поиск ближайшего POI для точечных объектов
+
многозначная принадлежность фотографии
+
граф пересекающихся и вложенных объектов
+
агрегация посещений по каждому объекту
+
HDBSCAN только для неизвестных мест

Ключевой принцип:

Фотографии не формируют известное место. Они только подтверждают посещение уже существующего географического объекта. Кластеризация создаёт место лишь тогда, когда подходящего именованного объекта в справочнике нет


Главное правило:

Не все найденные геообъекты являются пользовательскими местами.

Сырой объект можно сохранить для отладки, но карточку места создавать только после семантической фильтрации.

Правильный конвейер
Координата фотографии
-> найденные геообъекты
-> нормализация типа
-> allowlist допустимых типов
-> проверка названия
-> оценка качества
-> объединение по OSM ID
-> UserPlace
1. Не используй display_name как название места

У Nominatim:

display_name - составная строка адреса;
category и type - основной OSM-тег объекта;
addressdetails может содержать не только реальные объекты, но и дополнительные или искусственно сформированные элементы адреса.

То есть нельзя делать так:

place.name = result.displayName

Нужно сохранять отдельно:

data class RawGeoObject(
    val osmType: String,
    val osmId: Long,
    val name: String?,
    val category: String?,
    val type: String?,
    val placeRank: Int?,
    val addressRank: Int?,
    val adminLevel: Int?,
    val displayName: String?
)

Уникальный ключ:

val geoObjectId = "${osmType}:${osmId}"
2. Используй allowlist, а не только blacklist

Blacklist никогда не будет полным. Сегодня появится «городская территория 200B», завтра - «зона 45-Г», «жилой массив 12» или «участок А7».

Поэтому сначала разрешай только известные полезные типы.

Административные места

Разрешить:

place=city
place=town
place=village
place=hamlet
place=suburb
place=quarter
place=neighbourhood

OSM использует place=* для значимых населённых пунктов и именованных частей поселений. suburb обозначает крупную именованную часть города, а neighbourhood - более локальный именованный район.

С осторожностью:

place=locality
place=isolated_dwelling
place=block

place=locality обозначает именованное место, которое может быть незаселённым и не иметь чёткой географической сущности. Поэтому его лучше по умолчанию не показывать в популярных местах или давать ему низкий приоритет.

Административные границы

Разрешить:

boundary=administrative

но только если:

есть name;
есть поддерживаемый admin_level;
уровень удалось преобразовать в понятный тип;
объект является территориальной сущностью, а не отдельной линией границы.

admin_level описывает уровень объекта в административной иерархии, но его точное значение зависит от страны, поэтому нельзя глобально считать, например, что admin_level=8 всегда означает городской район.

Для России можно создать отдельную конфигурацию:

val russianAdminLevels = mapOf(
    2 to PlaceFacet.COUNTRY,
    4 to PlaceFacet.REGION,
    6 to PlaceFacet.DISTRICT,
    8 to PlaceFacet.CITY_OR_MUNICIPALITY,
    9 to PlaceFacet.CITY_DISTRICT,
    10 to PlaceFacet.NEIGHBOURHOOD
)

Это стартовая эвристика. На практике обязательно сопоставляй её ещё и с place, boundary, designation и родительскими объектами.

Значимые объекты

Разрешить именованные объекты:

leisure=park
leisure=garden
boundary=protected_area
tourism=attraction
tourism=museum
tourism=gallery
tourism=viewpoint
natural=peak
natural=beach
natural=water
amenity=university
amenity=theatre
amenity=cinema
amenity=library
amenity=cafe
amenity=restaurant
railway=station
aeroway=aerodrome
historic=*

Но только если есть нормальное собственное имя.

3. Запрети технические категории

По умолчанию не создавать UserPlace из:

landuse=*
highway=*
building=*
addr=*
postal_code=*
parking=*
power=*
utility=*
route=*
boundary=statistical
boundary=census

Особенно:

landuse=residential
landuse=commercial
landuse=industrial

landuse=residential обозначает участок земли с преимущественно жилым использованием, а не именованный пользовательский район. Поэтому он может участвовать в анализе территории, но не должен становиться карточкой места.

Пример:

private val rejectedCategories = setOf(
    "landuse",
    "highway",
    "building",
    "power",
    "utility",
    "route"
)
4. Добавь проверку качества названия

Проверять название отдельно недостаточно - фильтр должен учитывать и тип объекта.

Например, «Квартал 95» может быть настоящим районом, а «территория 200B» - технической зоной.

data class NameQuality(
    val isValid: Boolean,
    val score: Double,
    val reasons: List<String>
)

Пример:

fun evaluateName(
    name: String?,
    category: String?,
    type: String?
): NameQuality {
    val value = name
        ?.trim()
        ?.replace(Regex("\\s+"), " ")
        ?: return NameQuality(false, 0.0, listOf("missing_name"))

    if (value.length !in 2..100) {
        return NameQuality(false, 0.0, listOf("invalid_length"))
    }

    val letters = value.count { it.isLetter() }
    val digits = value.count { it.isDigit() }

    if (letters == 0) {
        return NameQuality(false, 0.0, listOf("no_letters"))
    }

    if (digits > letters * 2) {
        return NameQuality(false, 0.0, listOf("mostly_digits"))
    }

    val technicalCategory =
        category in setOf("landuse", "building", "boundary")

    val genericTechnicalName = Regex(
        pattern = """(?iu)^(городская\s+территория|территория|участок|зона|сектор|объект)\s*[-№A-ZА-Я0-9]+$"""
    ).matches(value)

    if (technicalCategory && genericTechnicalName) {
        return NameQuality(
            false,
            0.0,
            listOf("technical_generated_name")
        )
    }

    return NameQuality(
        true,
        1.0,
        emptyList()
    )
}

Для твоего примера:

name = "городская территория 200B"
category = "landuse" или технический boundary

Результат:

technical_generated_name
-> не создавать UserPlace
5. Введи нормализованный тип места

Не передавай сырые OSM-теги напрямую в UI.

enum class PlaceFacet {
    COUNTRY,
    REGION,
    CITY,
    DISTRICT,
    NEIGHBOURHOOD,
    NATURAL_AREA,
    LANDMARK,
    VENUE,
    TRANSPORT,
    UNKNOWN,
    TECHNICAL
}

Нормализация:

fun classifyPlace(
    category: String?,
    type: String?,
    adminLevel: Int?
): PlaceFacet {
    if (category == "landuse") {
        return PlaceFacet.TECHNICAL
    }

    if (category == "place") {
        return when (type) {
            "city", "town", "village", "hamlet" ->
                PlaceFacet.CITY

            "suburb", "borough", "quarter" ->
                PlaceFacet.DISTRICT

            "neighbourhood" ->
                PlaceFacet.NEIGHBOURHOOD

            else ->
                PlaceFacet.UNKNOWN
        }
    }

    if (category == "boundary" && type == "administrative") {
        return classifyAdministrativeLevel(adminLevel)
    }

    if (category == "leisure" && type in setOf("park", "garden")) {
        return PlaceFacet.NATURAL_AREA
    }

    if (category == "tourism") {
        return PlaceFacet.LANDMARK
    }

    if (category == "amenity") {
        return PlaceFacet.VENUE
    }

    return PlaceFacet.UNKNOWN
}

В пользовательские места пропускать только:

private val visibleFacets = setOf(
    PlaceFacet.CITY,
    PlaceFacet.DISTRICT,
    PlaceFacet.NEIGHBOURHOOD,
    PlaceFacet.NATURAL_AREA,
    PlaceFacet.LANDMARK,
    PlaceFacet.VENUE,
    PlaceFacet.TRANSPORT
)
6. Добавь итоговый quality score

Не делай решение только бинарным. Сначала посчитай оценку.

qualityScore =
    typeScore
    + nameScore
    + geometryScore
    + metadataScore
    - technicalPenalty

Пример весов:

Признак	Балл
place=city	1.0
place=suburb	0.9
place=neighbourhood	0.85
административный район	0.9
именованный парк	0.8
именованный музей	0.8
именованное кафе	0.65
place=locality	0.3
landuse=residential	0
техническое название	-1
нет собственного имени	-1
есть Wikidata или Wikipedia	+0.1
есть полигон	+0.05
fun calculateGeoObjectQuality(
    objectData: RawGeoObject
): Double {
    val facet = classifyPlace(
        objectData.category,
        objectData.type,
        objectData.adminLevel
    )

    var score = when (facet) {
        PlaceFacet.CITY -> 1.0
        PlaceFacet.DISTRICT -> 0.9
        PlaceFacet.NEIGHBOURHOOD -> 0.85
        PlaceFacet.NATURAL_AREA -> 0.8
        PlaceFacet.LANDMARK -> 0.8
        PlaceFacet.VENUE -> 0.65
        PlaceFacet.TRANSPORT -> 0.7
        PlaceFacet.COUNTRY,
        PlaceFacet.REGION -> 0.75
        PlaceFacet.UNKNOWN -> 0.25
        PlaceFacet.TECHNICAL -> 0.0
    }

    val nameQuality = evaluateName(
        objectData.name,
        objectData.category,
        objectData.type
    )

    if (!nameQuality.isValid) {
        return 0.0
    }

    score *= nameQuality.score

    return score.coerceIn(0.0, 1.0)
}

Порог создания места:

private const val MIN_PLACE_QUALITY = 0.60
7. Раздели хранение и отображение

Полезно хранить сырой результат, даже если он мусорный:

raw_geo_objects

Но не добавлять его в:

user_places

Структура:

RawGeoObject
├── status = ACCEPTED
├── status = REJECTED_TECHNICAL
├── status = REJECTED_NAME
└── status = UNKNOWN

Так ты сможешь открыть debug-экран и увидеть:

«городская территория 200B»
Отклонено:
- category=landuse
- техническое название
- qualityScore=0.0

Это намного удобнее, чем просто удалять объект без объяснения.

8. Конфигурация для первой версии
data class PlaceFilterConfig(
    val acceptedPlaceTypes: Set<String>,
    val acceptedCategories: Set<String>,
    val rejectedCategories: Set<String>,
    val lowPriorityTypes: Set<String>,
    val minimumQuality: Double
)

val defaultPlaceFilterConfig = PlaceFilterConfig(
    acceptedPlaceTypes = setOf(
        "city",
        "town",
        "village",
        "hamlet",
        "suburb",
        "borough",
        "quarter",
        "neighbourhood",
        "park",
        "garden",
        "museum",
        "attraction",
        "viewpoint",
        "station",
        "aerodrome"
    ),
    acceptedCategories = setOf(
        "place",
        "boundary",
        "leisure",
        "tourism",
        "amenity",
        "natural",
        "historic",
        "railway",
        "aeroway"
    ),
    rejectedCategories = setOf(
        "landuse",
        "building",
        "highway",
        "power",
        "utility",
        "route"
    ),
    lowPriorityTypes = setOf(
        "locality",
        "isolated_dwelling",
        "block"
    ),
    minimumQuality = 0.60
)
9. Итоговое правило
fun shouldCreateUserPlace(
    objectData: RawGeoObject
): Boolean {
    if (objectData.category in defaultPlaceFilterConfig.rejectedCategories) {
        return false
    }

    val facet = classifyPlace(
        objectData.category,
        objectData.type,
        objectData.adminLevel
    )

    if (facet !in visibleFacets) {
        return false
    }

    val quality = calculateGeoObjectQuality(objectData)

    return quality >= defaultPlaceFilterConfig.minimumQuality
}

Для «городская территория 200B»:

category = landuse или технический boundary
facet = TECHNICAL
quality = 0
shouldCreateUserPlace = false

Самое важное изменение - не создавать место из любого имени, которое вернул геокодер. Место должно одновременно иметь допустимый семантический тип, качественное название и достаточный qualityScore.