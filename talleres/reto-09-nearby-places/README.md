# Reto 09 — Accediendo al GPS

Taller individual: una app que muestra en un mapa los puntos de interés (hospitales,
sitios turísticos y más) cercanos a la ubicación del usuario, dentro de un radio en
kilómetros que se configura con preferencias. Es una app nueva (no es el triqui ni el
directorio), en Kotlin con MVVM. Del reto 08 se reutilizó la configuración de Gradle, el
tema, los diálogos de tema y Acerca de, y la forma de armar ViewModels y de manejar los
insets.

Lo que pide el enunciado y cómo quedó:

| Enunciado | En la app |
|---|---|
| Poner en un mapa los puntos de interés cercanos a la posición actual (latitud y longitud, o dirección) | Un mapa de OpenStreetMap (osmdroid) con un pin por cada lugar encontrado. **Al abrir busca alrededor de la UNAL Bogotá** (decisión del usuario, ver *Decisiones*); la posición del GPS se usa con el botón *mi ubicación*, y también se puede buscar alrededor de cualquier punto con una pulsación larga. El centro se muestra arriba como dirección (con el `Geocoder`) o, si no se consigue, como coordenadas |
| Tipos de lugar: hospital, lugar turístico, etc. | Seis categorías con chips de selección múltiple: hospitales y clínicas, farmacias, turismo, restaurantes, hoteles y policía. Por defecto, hospitales y turismo |
| El radio (km) se configura con **preferencias** | Menú ⋮ → *Radio de búsqueda*: una pantalla de ajustes con un `SeekBarPreference` de 1 a 20 km (2 por defecto). Al volver, si el radio cambió, se busca de nuevo |
| Cada punto muestra su **nombre** | El nombre está siempre visible bajo el pin; al tocarlo se abre un globo con nombre, categoría, distancia y dirección |
| API de Google u OpenStreetMap (osmdroid) | osmdroid para el mapa, sin API key ni facturación |

## Cómo funciona la búsqueda

1. Al abrir la app se busca **de inmediato alrededor de la UNAL Bogotá** (4.6370, -74.0830).
   No se usa el GPS ni se pide el permiso al abrir. La posición del usuario se pide
   (**una** posición, sin seguimiento continuo) solo al tocar *mi ubicación*.
2. Con ese centro se hace **una consulta por categoría marcada** a
   [Photon](https://photon.komoot.io) (buscador de datos de OpenStreetMap), en paralelo y
   en un hilo de IO:

   ```
   GET https://photon.komoot.io/reverse?lat=4.637000&lon=-74.083000&radius=2&limit=50
       &osm_tag=amenity%3Ahospital&osm_tag=amenity%3Aclinic
   ```

   - `radius` va en km. Varios `osm_tag` en una misma consulta se combinan con **OR**
     (hospital *o* clínica).
   - La respuesta es un GeoJSON `FeatureCollection`. Cada lugar trae `name`, `osm_key`,
     `osm_value`, `osm_type` + `osm_id`, calle y ciudad (opcionales) y
     `geometry.coordinates = [longitud, latitud]`.
   - **Photon devuelve como máximo 50 resultados por consulta** (con `limit=100`
     sigue devolviendo 50), ordenados por distancia. Por eso se consulta por categoría:
     el tope aplica a cada una y no a todas juntas.
   - Se descartan los lugares **sin nombre**: no habría qué mostrar.
3. En el cliente se vuelve a **filtrar por radio** con la fórmula del haversine (el borde
   cuenta), se quitan duplicados entre categorías y se ordena por distancia.
4. Si una categoría devolvió 50, el mensaje de abajo lo dice: *"Solo se muestran los 50
   más cercanos de: Hospitales y clínicas."* Subir el radio no ayuda en ese caso; conviene
   bajarlo.
5. Mientras el centro sea el de la UNAL por defecto, el mensaje agrega la pista:
   *"Mostrando la UNAL Bogotá. Toca Mi ubicación para buscar donde estás."* Desaparece
   cuando el centro es la posición del usuario o un punto tocado.
6. Si **alguna** de las consultas falla (sin red, 5xx, JSON ilegible), toda la búsqueda
   se marca como error con el botón *Reintentar*, que repite la búsqueda en el mismo
   centro. Es más honesto que mostrar un mapa con categorías faltantes sin avisar.

**Cuándo se busca** (nada es automático más allá de esto):

| Disparador | Qué pasa |
|---|---|
| Abrir la app | Busca en la UNAL Bogotá, sin GPS ni permiso |
| Botón de *mi ubicación* | Pide el permiso si falta, se ubica y busca ahí; descarta el punto elegido a mano |
| Marcar o desmarcar un chip | Busca de nuevo en el mismo centro |
| Cambiar el radio en ajustes | Busca de nuevo en el mismo centro |
| **Pulsación larga** en el mapa | Busca alrededor de ese punto (con o sin permiso) |

El mapa se encuadra para que el círculo del radio quepa, cuando cambia el centro o el
radio (no al activar un chip, para no interrumpir a quien esté explorando).

## Clases (MVVM)

```
co.edu.unal.nearbyplaces
├── NearbyPlacesApp            tema guardado + las dependencias (ubicación, lugares, dirección, radio)
├── ThemePreference, AppDialogs    del reto 08: tema claro/oscuro y Acerca de
├── data/
│   ├── GeoPoint, GeoBounds        coordenadas propias: la lógica no depende de osmdroid
│   ├── GeoMath                    haversine, filtro por radio + orden, rectángulo del radio
│   ├── Poi, PoiCategory           el lugar, y las categorías con sus tags de OSM y su color
│   ├── PoiRepository              interfaz: lugares de una categoría cerca de un punto
│   ├── PhotonPoiRepository        la implementación sobre Photon (IO, tope de 50)
│   ├── PhotonUrlBuilder           arma la URL (tags repetidos y codificados)
│   ├── PhotonParser               GeoJSON → Poi (org.json)
│   ├── HttpGet                    un GET con User-Agent propio y timeouts (HttpURLConnection)
│   ├── LocationProvider           interfaz: una posición (Found / Disabled / NoFix)
│   ├── AndroidLocationProvider    LocationManager + getCurrentLocation, timeout y última conocida
│   ├── AddressRepository          interfaz: dirección de un punto
│   ├── GeocoderAddressRepository  Geocoder (API 33+ asíncrono; antes en IO)
│   └── RadiusSource               interfaz pequeña para leer el radio, y su versión con preferencias
└── ui/
    ├── map/        MapActivity (launcher), MapViewModel, PoiMarkerFactory
    └── settings/   SettingsActivity, SettingsFragment (PreferenceFragmentCompat)
```

`MapViewModel` recibe las cuatro interfaces (ubicación, lugares, dirección, radio). En los
tests se le pasan fakes. Su estado (`MapUiState`) trae el `MapStatus`, el centro, la
dirección, el radio, las categorías, los lugares y las categorías que llegaron al tope,
y el origen del centro (`CenterSource`: `DEFAULT` = la UNAL al abrir, `USER` = el GPS,
`PICKED` = pulsación larga). Los estados son: pidiendo permiso, buscando ubicación,
ubicación apagada, sin señal, cargando lugares, sin categorías, resultado (con cuántos),
vacío y error de red.

- Nada de red ni de GPS corre en el hilo principal: la red va en `Dispatchers.IO`, el
  `LocationManager` entrega en el hilo principal pero de forma asíncrona (callback), y el
  `Geocoder` en IO o con su listener.
- `PoiMarkerFactory` dibuja el pin con el color de la categoría y el nombre debajo
  **al vuelo** en un `Drawable`, sin crear un bitmap por lugar (con 300 lugares serían
  decenas de MB). El nombre largo se recorta con «…» y lleva un halo blanco para leerse
  sobre cualquier parte del mapa.
- El ciclo de vida del `MapView` sigue el de la Activity: `onResume`, `onPause` y
  `onDetach` en `onDestroy`.

## Permisos

Se declaran `INTERNET`, `ACCESS_FINE_LOCATION` y `ACCESS_COARSE_LOCATION`. **El permiso de
ubicación no se pide al abrir**: solo al tocar *mi ubicación*, con `RequestMultiplePermissions`.

| Caso | Qué pasa |
|---|---|
| Abrir la app (con o sin permiso) | Busca en la UNAL Bogotá y muestra los pines. No hay diálogo ni aviso de permiso |
| Tocar *mi ubicación* por primera vez | Diálogo del sistema (el usuario puede elegir ubicación precisa o aproximada). Con solo la aproximada se usa únicamente el proveedor de red |
| Aceptado | Se ubica y busca en esa posición |
| Negado una vez | Aviso *Para buscar donde estás, permite el acceso a tu ubicación…* con el botón *Permitir ubicación*. Al tocarlo se muestra primero una explicación (*rationale*) y luego el diálogo del sistema. El mapa se queda donde estaba |
| Negado con «no volver a preguntar» | El botón pasa a ser *Abrir ajustes de la app* (el sistema ya no muestra el diálogo) |
| Se concede desde los ajustes y se vuelve | Al volver a primer plano se detecta el permiso y se busca en la posición del usuario |
| **Sin permiso** | La app sigue funcionando: busca en la UNAL y la **pulsación larga** busca alrededor del punto tocado |
| Ubicación del sistema apagada | Mensaje claro con el botón *Ajustes de ubicación*. Al encenderla y volver a la app, se busca solo (si sigue apagada, el mensaje queda igual) |
| GPS sin señal (15 s) | Se intenta con la última posición conocida; si tampoco hay, *No se pudo obtener tu ubicación* con *Reintentar* |

Sobre la ubicación: se usa `LocationManagerCompat.getCurrentLocation` con el GPS y, si no
contesta a tiempo, con la red; el tiempo total (15 s) se reparte entre los proveedores
disponibles. Si ninguno responde, se usa `getLastKnownLocation`. No hace falta Play
Services. El error de señal no se relanza solo: tiene *Reintentar*.

## Rotar y matar el proceso

| Caso | Qué pasa |
|---|---|
| Rotar | El `MapViewModel` se conserva: **no** se repite la consulta y los pines siguen. El mapa se vuelve a encuadrar sobre el radio (el zoom y el paneo manuales de antes de rotar no se conservan) |
| Android mata el proceso | En el `SavedStateHandle` quedan las categorías marcadas, el punto de la pulsación larga (si lo había) y si ya se pidió el permiso. Al volver se busca de nuevo (los resultados no se guardan): en el punto guardado si lo había; si no, **en la UNAL**, aunque antes se hubiera usado el GPS |
| Volver de ajustes | Si el radio cambió, se busca de nuevo; si no, no se hace nada |

## Cómo correrlo

1. Android Studio → *Open* → `talleres/reto-09-nearby-places`, esperar el *Gradle Sync* y
   darle *Run 'app'*. No hay que configurar nada: **no usa API keys**.
2. **El emulador necesita internet** (los tiles del mapa y Photon). Si el mapa sale gris,
   revisa la red del emulador.
3. La app abre en la UNAL Bogotá, así que **no hace falta fijar nada** para ver
   resultados. Para probar el botón *mi ubicación* en el emulador, fija su ubicación
   (sin hacerlo, el emulador usa Mountain View):
   - Por interfaz: *Running Devices* → ⋮ → *Extended Controls* → *Location* → escribir
     `4.6370, -74.0830` → *Set location* (en el emulador suelto: ⋯ → *Extended controls*).
   - Por comando: `adb emu geo fix -74.0830 4.6370` (**longitud primero**, luego latitud).
4. Para ese botón, la ubicación del sistema debe estar activa (*Settings → Location*).

Tests (no requieren emulador):

```powershell
cd talleres\reto-09-nearby-places
.\gradlew.bat testDebugUnitTest
```

Los tests del parser y del repositorio usan Robolectric solo por el `org.json` real de
Android (en la JVM pelada `org.json` es un stub que no hace nada). La primera vez,
Robolectric descarga el jar de Android 17 (~240 MB) a `~/.m2`.

### Guion de prueba en el emulador

1. Abrir la app: el mapa se centra en la UNAL con un punto azul y aparecen **pines en la
   UNAL sin ningún diálogo de permiso**. Pines rojos (hospitales y clínicas) y morados
   (turismo), cada uno con su nombre debajo. Abajo: «N lugares a menos de 2 km. Mostrando
   la UNAL Bogotá. Toca Mi ubicación para buscar donde estás.»
2. Fijar la ubicación del emulador (`adb emu geo fix -74.0830 4.6370`). Tocar *mi
   ubicación* (círculo con mira): aparece el diálogo de permiso → elegir *Mientras la app
   está en uso* → busca en la posición del emulador y la pista de la UNAL desaparece.
   Fijando otra ubicación y tocando de nuevo, el centro se mueve ahí.
3. Tocar un pin: se abre el globo con nombre, categoría, distancia y dirección.
4. Marcar *Farmacias*: aparecen pines verdes. Desmarcar *Turismo*: se van los morados.
5. Desmarcar todo: *Elige al menos una categoría* y el mapa queda sin pines. Volver a
   marcar *Hospitales*.
6. Menú ⋮ → *Radio de búsqueda* → mover la barra a 8 → volver atrás: el mapa se vuelve a
   encuadrar y hay más lugares. Probar 1 km: menos.
7. Con un radio grande (20 km) y *Restaurantes* marcado: aparece *Solo se muestran los 50
   más cercanos de: Restaurantes.*
8. **Pulsación larga** sobre otro punto del mapa: el punto azul se mueve ahí y se busca
   alrededor. La pista de la UNAL ya no aparece.
9. Rotar el emulador: los pines siguen y **no** hay una nueva consulta.
10. Apagar la red del emulador (modo avión) y marcar un chip: *No se pudieron cargar los
    lugares* con *Reintentar*. Prenderla y tocar *Reintentar*: busca en el mismo centro.
11. Negar el permiso: en ajustes de la app, ubicación → *No permitir*, y tocar *mi
    ubicación*: aparece el aviso con *Permitir ubicación* (explica y vuelve a pedir; si se
    negó para siempre, pasa a *Abrir ajustes de la app*). Mientras tanto la app sigue
    mostrando pines y la pulsación larga sigue buscando.
12. Apagar la ubicación del sistema (ajustes rápidos) y tocar *mi ubicación*: *La
    ubicación está apagada…* con *Ajustes de ubicación*. Encenderla y volver a la app: se
    busca sola.
13. Matar el proceso con una pulsación larga hecha (app en segundo plano,
    `adb shell am kill co.edu.unal.nearbyplaces`) y volver desde Recientes: vuelve a
    buscar en ese mismo punto y con las mismas categorías. Sin pulsación larga (por
    ejemplo, tras usar *mi ubicación*), vuelve a abrir en la UNAL.
14. Menú ⋮ → *Tema* y *Acerca de*: abren sus diálogos.

## Datos del proyecto

| Campo | Valor |
|---|---|
| Paquete | `co.edu.unal.nearbyplaces` |
| Activity inicial | `ui.map.MapActivity` |
| Versión | `versionCode 9`, `versionName "9.0"`; la app se llama «Cerca de mí R9» («Nearby R9» en inglés) |
| minSdk / targetSdk | 24 / 37 |
| Lenguaje | Kotlin + ViewBinding + ViewModel (MVVM) |
| Mapa | osmdroid 6.1.20 (OpenStreetMap) |
| Lugares | Photon (`photon.komoot.io/reverse`) |
| Ubicación | `LocationManager` (sin Play Services) |
| Preferencias | `androidx.preference:preference-ktx` 1.2.1 |
| Tests | JUnit 4, kotlinx-coroutines-test, Robolectric 4.17 (solo para tests) |

## Decisiones

- **Arranca en la UNAL Bogotá** (decisión del usuario): al abrir se busca alrededor de la
  UNAL (4.6370, -74.0830) sin esperar al GPS; la posición actual se usa con el botón *mi
  ubicación*. Se aparta un poco del enunciado («a partir de las coordenadas GPS…»), que el
  usuario aceptó a propósito. El porqué: en el emulador la ubicación por defecto es
  Mountain View, así que con GPS al abrir la app mostraría un mapa vacío o irrelevante;
  así siempre se ven resultados útiles, no hay diálogo de permiso al abrir y el flujo del
  GPS (permiso, rationale, ubicación apagada, sin señal) queda como una acción explícita.
- **osmdroid y no Google Maps**: el enunciado acepta los dos. Google Maps exige una API
  key y una cuenta de facturación; osmdroid funciona sin nada de eso y el repo es
  público. Se configura el `User-Agent` con el `applicationId` (la política de tiles de
  OpenStreetMap bloquea los clientes anónimos), el caché de tiles va a carpetas de la app
  (no pide permiso de almacenamiento) y la atribución «© OpenStreetMap contributors» está
  siempre visible.
- **Photon y no Overpass**: Overpass es la API «oficial» de consultas de OSM, pero los
  servidores públicos fallan seguido: el 25 de noviembre de 2026, 3 de 4 respondieron
  502 o agotaron los 30 s. Photon contestó en menos de 1 s y devuelve GeoJSON ya listo,
  con `radius` en km, `osm_tag` y orden por distancia. El costo es el tope de 50
  resultados por consulta, que se maneja consultando por categoría y avisando cuando se
  llega. La capa de datos está detrás de `PoiRepository`: cambiar a Overpass u otra
  fuente no toca el ViewModel.
- **`LocationManager` y no Fused Location (Play Services)**: no agrega una dependencia
  grande, funciona en emuladores sin Google Play y alcanza para una sola posición.
  `getCurrentLocation` trae una posición nueva (la última conocida puede ser de hace
  horas) y queda de respaldo `getLastKnownLocation`.
- **Una consulta por categoría**, en vez de una sola con todos los tags: con una sola, las
  50 plazas del tope se las quedaría la categoría más abundante (restaurantes) y las
  demás desaparecerían.
- **Sin refresco automático**: buscar cada vez que el GPS se mueve gastaría red, parpadearía
  el mapa y haría más probable toparse con el límite de uso de Photon. El usuario decide:
  *mi ubicación*, cambiar chips o radio, o pulsación larga.
- **Pulsación larga con o sin permiso**: así la app sirve aunque se niegue la ubicación, y
  de paso permite explorar otros lugares. Se eligió pulsación larga y no un toque simple
  para no chocar con tocar un pin.
- **Una pantalla más los ajustes, sin submenús** (pedido del profesor): el mapa con sus
  chips y su botón, y los ajustes del radio. El menú ⋮ solo tiene Radio, Tema y Acerca de.
- **Etiqueta dibujada al vuelo**, no un bitmap por pin: ver *Clases*.
- **Tests**: 64.
  - **Kotlin puro**: 9 de `GeoMathTest` (haversine con valores conocidos —un grado de
    longitud en el ecuador, polo-ecuador, antípodas—, filtro por radio con el borde,
    orden, rectángulo del radio) y 6 de `PhotonUrlBuilderTest` (tags repetidos y
    codificados, radio, límite, sin `lang`, decimales con punto en cualquier regional).
  - **Con Robolectric (`org.json` real)**: 11 de `PhotonParserTest` con una respuesta
    real recortada (con un `way`, un lugar sin `name`, uno con `name: null` y uno en
    blanco) y 6 de `PhotonPoiRepositoryTest` (URL pedida, tope de 50, 49, el tope cuenta lo
    que mandó Photon y no lo que tenía nombre, fallo HTTP, cuerpo ilegible).
  - **`MapViewModel` con fakes (32)**: abrir en la UNAL sin GPS ni permiso (con y sin
    permiso concedido), la pista solo con el centro por defecto, *mi ubicación* con y sin
    permiso (pide, y al conceder se ubica), permiso negado y no volver a pedirlo,
    pulsación larga sin permiso, ubicación apagada (y volver tras encenderla), sin señal y
    reintento, GPS lento, sin red y reintento, vacío, tope de 50, filtro por radio en el
    borde, duplicados entre categorías, cambio de radio, cambio de categorías, nada
    marcado, cambios mientras se ubica, pulsación larga que cancela la anterior, *mi
    ubicación*, rotación sin repetir consultas, muerte del proceso (con punto guardado, y sin él: abre
    en la UNAL aunque se hubiera usado el GPS) y dirección.
