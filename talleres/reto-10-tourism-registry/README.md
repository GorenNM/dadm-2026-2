# Reto 10 — Consumiendo Webservices

Taller individual: una app que consume un servicio web del Catálogo de Datos
Colombiano (datos.gov.co). Es una app nueva, en Kotlin con MVVM. Del reto 08
se reutilizó la configuración de Gradle, el tema, los diálogos de tema y
Acerca de, y la forma de armar listas, ViewModels y pruebas.

Lo que pide el enunciado y cómo quedó:

| Enunciado | En la app |
|---|---|
| Una interfaz que envíe **parámetros** al servicio | Pantalla de búsqueda: nombre ("contiene"), departamento, municipio y categoría. Los tres desplegables se llenan **desde el propio servicio** |
| **Mostrar la respuesta** | Lista de resultados (20 por página, "Cargar más") con el total, y una pantalla de detalle por prestador |
| Procesar el JSON con el **objeto JSON del SDK de Android** | `org.json` (`JSONArray` / `JSONObject`), sin Gson ni Moshi |
| Un servicio de datos.gov.co | Registro Nacional de Turismo (RNT), vía su API SODA |

## El dataset

El ejemplo del enunciado (Puntos Posconsumo, de 2016, con enlaces `goo.gl`)
**ya no existe**: la búsqueda "posconsumo" en el catálogo no lo devuelve, y
los enlaces `goo.gl` están muertos. Se escogió otro dataset vivo, con campos
que sirven de parámetros reales.

- **Registro Nacional de Turismo - RNT** (Ministerio de Comercio, Industria y
  Turismo), identificador `thwd-ivmp`.
- URL: `https://www.datos.gov.co/resource/thwd-ivmp.json`
- API SODA (Socrata), **sin token** (hay límite de uso sin token, que para
  esta app sobra).
- Verificado el 2026-11-25: 128.731 prestadores activos, actualizado el
  2026-10-29, un solo corte (`ano=2026`, `periodo=OCTUBRE`).

Campos que usa la app (los nombres son los del servicio):

| Campo | Para qué |
|---|---|
| `codigo_rnt` | Código único del prestador: llave del detalle y desempate del orden |
| `razon_social_establecimiento` | Nombre; es el campo del filtro "contiene" |
| `departamento`, `municipio`, `categoria` | Filtros y valores de los desplegables |
| `sub_categoria`, `tipo_prestador` | Se muestran en la tarjeta y el detalle |
| `direcci_n_comercial_establecimiento` | Dirección (y búsqueda en el mapa) |
| `n_m_tel_fono`, `num_celular` | Llamar |
| `habitaciones`, `camas`, `num_emp1` | Detalle |

Los **correos** (`correo_establecimiento`, `correo_prestador`) existen en el
dataset pero la app **no los lee ni los muestra**: en viviendas turísticas y
guías de turismo son correos de personas naturales.

Cosas del dataset que la app maneja:

- Los valores van en mayúsculas, algunos con tilde (`NARIÑO`, `VIVIENDAS
  TURÍSTICAS`). Hay un departamento `NO DETERMINADO`.
- Los números vienen como texto (`"habitaciones":"11"`).
- Cuando falta un dato dice `"Sin dato"` (teléfonos, a veces direcciones) o
  `0` (habitaciones de una agencia de viajes): no se muestran.
- "Establecimientos de gastronomía" y "…y similares" son dos categorías
  distintas en el servicio; así salen en el desplegable.

## Las consultas SoQL que arma la app

Todas son `GET` a la URL del dataset, con estos parámetros. Aquí se muestran
**sin codificar**; la app los codifica (ver más abajo).

```
# Departamentos / categorías (los desplegables)
$select=departamento&$group=departamento&$order=departamento&$limit=2000

# Municipios del departamento elegido
$select=municipio&$where=departamento = 'BOYACA'&$group=municipio&$order=municipio&$limit=2000

# Total de la búsqueda (respuesta: [{"count":"116"}])
$select=count(*)&$where=departamento = 'BOYACA' AND municipio = 'VILLA DE LEYVA'
  AND contains(upper(razon_social_establecimiento), 'HOTEL')

# Una página de resultados (20, orden estable para paginar)
$where=<el mismo>&$order=razon_social_establecimiento,codigo_rnt&$limit=20&$offset=0

# Detalle de un prestador
$where=codigo_rnt = '24027'&$limit=1
```

- Cada condición se agrega **solo si el campo se usó**; "Todos" no envía
  nada, y sin ningún filtro no hay `$where`. Se combinan con `AND`.
- El **nombre** se compara con `contains(upper(campo), 'TEXTO')`: el texto
  se pasa a mayúsculas porque el dataset está en mayúsculas.
- El **orden** es `razon_social_establecimiento,codigo_rnt`. El nombre no es
  único, así que el código RNT desempata: sin eso, `$offset` podría repetir
  o saltarse filas entre páginas.
- El **total** y la **primera página** son dos pedidos que salen en paralelo.

### Cómo se escapan los literales

Todo texto que viene del usuario (el nombre) o de un desplegable va **entre
comillas simples** y pasa por una función (`SodaQueryBuilder.escapeLiteral`)
que dobla las comillas: `'` → `''`. Así `D'COLOMBIA` es `'D''COLOMBIA'` y un
texto como `x' OR '1'='1` queda como un solo literal
(`'X'' OR ''1''=''1'`), no como una condición más. Los **nombres de campos**
son constantes del código, nunca vienen del usuario.

Una sorpresa verificada contra el servicio: **`contains` no es literal**.
Se comporta como un `LIKE '%texto%'`, donde `%` y `_` son comodines (buscar
`%` devolvía las 128.731 filas). Por eso, en el filtro por nombre, además se
escapan con barra: `\`, `%` y `_` (`escapeLike`), y buscar `1%` trae los 6
prestadores cuyo nombre tiene ese "1%". Probado: `contains(…, '1\%')`
devuelve lo mismo que `like '%1\%%'`.

Después, **la URL se codifica**: `URLEncoder` convierte el espacio en `+`,
que en una consulta Socrata sería un `+` literal, así que se reemplaza por
`%20`. Se codifican los valores (comillas → `%27`, `Ñ` → `%C3%91`, `&` y `=`
del usuario no pueden partir los parámetros); los nombres de parámetro
(`$where`, `$limit`…) son constantes y van tal cual. La lógica de armar el
`$where` es Kotlin puro (`SodaQueryBuilder`), sin Android, y se prueba en la
JVM.

> Un efecto lateral: el servicio (probablemente un firewall) responde **HTTP
> 403** a ciertos patrones que parecen inyección en texto crudo. La app los
> envía escapados y codificados, y el 403, si llegara, se muestra como error
> del servidor, como cualquier otro.

## Pantallas

**Búsqueda** (`SearchActivity`, pantalla inicial)

- Campos: nombre, departamento, municipio y categoría. **El municipio
  depende del departamento**: está deshabilitado ("Elige primero un
  departamento") hasta elegir uno; al cambiar de departamento el municipio se
  limpia y se piden los municipios del nuevo.
- *Buscar* (o la lupa del teclado) lanza la consulta. Arriba, en la barra, el
  total: "1.026 prestadores". Si se busca otra vez mientras hay una búsqueda
  en curso, la anterior se **cancela**.
- Cada tarjeta muestra nombre, categoría · subcategoría, y municipio,
  departamento. Al final de la lista: "Mostrando 20 de 1.026" y *Cargar más*,
  mientras queden. Tocar una tarjeta abre el detalle.
- **"Mostrar consulta enviada"**: una línea plegable con la URL decodificada
  de la última consulta. Es un extra didáctico: se ve el `$where` real.
- En horizontal, los filtros pasan a un panel a la izquierda (con su propio
  scroll) y los resultados a la derecha.
- El menú ⋮ solo tiene Tema y Acerca de (sin submenús, pedido del profesor).

**Detalle** (`DetailActivity`)

- Recibe **solo el `codigo_rnt`** y **vuelve a consultar** el servicio. Por
  eso se reconstruye aunque Android mate el proceso.
- Muestra: nombre, categoría, subcategoría, tipo de prestador, dirección,
  municipio, departamento, teléfono, celular, habitaciones, camas, empleados
  y código RNT. Lo que no trae el servicio no aparece (ni "Sin dato" ni 0).
- **Llamar**: abre el marcador (`ACTION_DIAL`, no pide permisos) con el primer
  número válido (celular, y si no, teléfono). Si no hay ninguno (`Sin dato`,
  `0`, menos de 7 dígitos), el botón ni aparece.
- **Ver en el mapa**: `geo:0,0?q=dirección, municipio, departamento`. Si no
  hay ninguna app de mapas, un Snackbar lo avisa.

## Estados de la pantalla de búsqueda

| Estado | Qué se ve |
|---|---|
| Inicial | La instrucción "Elige los filtros que quieras y toca Buscar" |
| Cargando | Círculo de progreso; el botón Buscar se deshabilita |
| Resultados | Lista + total + pie con *Cargar más* |
| Vacío | "Ningún prestador coincide con esos filtros" + *Limpiar filtros* (limpia y vuelve a buscar) |
| **Sin conexión** | "No hay conexión a internet" + *Reintentar*. Se comprueba con `ConnectivityManager` / `NetworkCapabilities` **antes** de pedir, así no se espera un timeout (requiere `ACCESS_NETWORK_STATE`) |
| Error de red | Tiempo agotado, servidor no encontrado (DNS) u otro, cada uno con su mensaje + *Reintentar* |
| Error del servidor | HTTP ≥ 400: "El servicio respondió con un error (HTTP 400): …" con el `message` del JSON de error de Socrata si vino. *Reintentar* |
| Respuesta inesperada | Un 200 cuyo cuerpo no es el JSON esperado: mensaje propio + *Reintentar* |
| Error al **cargar más** | La lista ya cargada **se conserva**; el pie muestra el error y *Reintentar* |
| Desplegables sin cargar | Línea de error bajo los campos con *Reintentar* (la búsqueda sigue funcionando sin ellos) |

El detalle tiene los mismos errores (con *Reintentar*) y "Ese prestador ya no
está en el registro".

## Clases (MVVM)

```
co.edu.unal.tourismregistry
├── TourismApp                tema guardado + el repositorio
├── ThemePreference, AppDialogs    del reto 08: tema claro/oscuro y Acerca de
├── data/
│   ├── Models                Provider, ProviderFilter, Page, PhoneNumbers
│   ├── ApiResult             ApiResult (Success/Failure) y ApiError (sealed)
│   ├── SodaQueryBuilder      arma el SoQL, escapa y codifica (Kotlin puro)
│   ├── SodaClient            interfaz + HttpSodaClient (HttpURLConnection)
│   ├── RntParser             JSON (org.json) -> modelos
│   ├── ConnectivityChecker   ¿hay red? (ConnectivityManager)
│   └── TourismRepository     interfaz + SodaTourismRepository
└── ui/
    ├── ApiErrorText          el texto de cada error
    ├── search/   SearchActivity, SearchViewModel, ProvidersAdapter, DropdownAdapter
    └── detail/   DetailActivity, DetailViewModel
```

- **Nada de red en el hilo principal**: `HttpSodaClient` trabaja en
  `Dispatchers.IO`. Los ViewModels solo llaman funciones `suspend`.
- **Resultados tipados**: ninguna excepción llega a la UI. Cada fallo es un
  `ApiError` (`NoConnection`, `Network(motivo)`, `Server(código, mensaje)`,
  `InvalidResponse`) y la pantalla decide el texto.
- **`HttpSodaClient`**: `HttpURLConnection` con tiempos máximos (conexión
  10 s, lectura 15 s), `Accept: application/json`, lectura del `errorStream`
  cuando el servicio responde un error, y cierre de streams y `disconnect()`
  siempre (`finally`).
- **Cancelación**: cada búsqueda es un `Job`; al buscar otra vez se cancela.
  `HttpURLConnection` es bloqueante, así que la petición vieja termina sola,
  pero su respuesta se descarta y nunca llega a la pantalla.
- **`SearchViewModel`**: el filtro de los campos y el de la última búsqueda
  van en el `SavedStateHandle`, y cuántos resultados van cargados. Distingue
  "lo que hay en los campos" de "lo que se buscó": *Cargar más* y
  *Reintentar* usan la búsqueda hecha, no lo que se editó después.
- **Los ViewModels reciben un `TourismRepository`** (interfaz). En los tests
  reciben un fake en memoria que filtra como el servicio.

## Rotar y matar el proceso

| Caso | Qué pasa |
|---|---|
| Rotar | El ViewModel sigue vivo: la lista, la página y los filtros se quedan y **no se repite ninguna consulta** |
| Android mata el proceso con resultados en pantalla | Los filtros y cuántos resultados había están en el `SavedStateHandle`. Al volver se repite **una sola consulta** que trae todo lo que había (`$limit` = lo cargado) |
| Matar el proceso en el detalle | El código RNT viaja como extra del `Intent` y vuelve en el `SavedStateHandle`: se vuelve a consultar el servicio |
| Rotar con el panel "consulta enviada" abierto | Sigue abierto |
| Rotar con un error en pantalla | Sigue ahí, con su *Reintentar* |

## Cómo correrlo

**El emulador (o el teléfono) necesita internet.** En un AVD, que el
computador tenga conexión basta.

1. Android Studio → *Open* → `talleres/reto-10-tourism-registry`, esperar el
   *Gradle Sync* y darle *Run 'app'*. No hace falta token ni configuración:
   el dataset es abierto.
2. Para probar sin conexión: activar el modo avión en el emulador.

Tests (no requieren emulador ni internet):

```powershell
cd talleres\reto-10-tourism-registry
.\gradlew.bat testDebugUnitTest
```

La primera vez, Robolectric descarga el jar de Android 17 (~240 MB) a
`~/.m2`.

### Guion de prueba

Las cifras son las del servicio el 2026-11-25; con un corte nuevo pueden
variar un poco.

1. Abrir la app: la instrucción inicial y los campos. Municipio deshabilitado
   con "Elige primero un departamento". Abrir *Departamento*: "Todos" y los
   34 departamentos (los de datos.gov.co, en mayúsculas).
2. *Buscar* sin filtros → "128.731 prestadores", 20 tarjetas y "Mostrando 20
   de 128.731". Tocar *Cargar más* → 40.
3. Tocar "Mostrar consulta enviada": se ve la URL con `$order`, `$limit=20` y
   `$offset=20`. Sin `$where`, porque no hay filtros.
4. *Departamento* = BOYACA → el municipio se habilita y se llena. Elegir
   VILLA DE LEYVA. Escribir "hotel" y *Buscar* → "116 prestadores". La
   consulta enviada muestra el `$where` completo con las tres condiciones.
5. Cambiar el departamento a ANTIOQUIA: el municipio vuelve a "Todos". Elegir
   MEDELLIN y la categoría BARES → *Buscar* → 5 prestadores.
6. *Departamento* = META, municipio VILLAVICENCIO, nombre "llanero" → 8
   resultados. Tocar "HOTEL CATIRE LLANERO": el detalle trae celular, 14
   habitaciones y los botones *Llamar* y *Ver en el mapa*.
7. *Llamar* abre el marcador con el número (no hay permiso). *Ver en el mapa*
   abre el mapa con la dirección; sin app de mapas, un Snackbar lo avisa.
8. Volver. Buscar el nombre "universo traveling" → abrir el prestador (código
   19951): no tiene teléfono ("Sin dato"), así que **no aparece *Llamar***.
9. Escribir "d'" (con apóstrofe) → 41 resultados, sin error: la comilla va
   escapada. Escribir "1%" → 6 (el `%` se busca literal; sin escapar serían
   todos).
10. Escribir "zzzzqx" → "Ningún prestador coincide con esos filtros" y
    *Limpiar filtros*; tocarlo limpia los campos y busca todo.
11. Rotar con resultados cargados (con *Cargar más* ya tocado): la lista y el
    total siguen igual, sin pantalla de carga.
12. Modo avión → *Buscar*: "No hay conexión a internet" con *Reintentar*.
    Desactivar el modo avión → *Reintentar* → resultados.
13. Con resultados en pantalla, modo avión → *Cargar más*: el error sale en el
    pie y las tarjetas ya cargadas se quedan. Quitar el modo avión y
    *Reintentar* agrega la página.
14. Matar el proceso con resultados (app en segundo plano,
    `adb shell am kill co.edu.unal.tourismregistry`) y volver desde
    Recientes: los filtros siguen y los resultados se cargan otra vez.
15. Menú ⋮ → *Tema* y *Acerca de* ("Reto 10 · v10.0").

## Datos del proyecto

| Campo | Valor |
|---|---|
| Paquete | `co.edu.unal.tourismregistry` |
| Activity inicial | `ui.search.SearchActivity` |
| Versión | `versionCode 10`, `versionName "10.0"`; la app se llama "Turismo R10" ("Tourism R10" en inglés) |
| minSdk / targetSdk | 24 / 37 |
| Lenguaje | Kotlin + ViewBinding + ViewModel (MVVM) |
| Red | `HttpURLConnection`, sin dependencias extra |
| JSON | `org.json` del SDK de Android |
| Permisos | `INTERNET`, `ACCESS_NETWORK_STATE` |
| Tests | JUnit 4, kotlinx-coroutines-test, Robolectric 4.17 (solo para tests) |

## Decisiones

- **`HttpURLConnection` en vez de Retrofit/OkHttp/Volley**: el reto es sobre
  consumir un servicio, y con `HttpURLConnection` se ve todo el recorrido
  (URL, timeouts, cabeceras, código de estado, `errorStream`, cierre). Detrás
  de una interfaz (`SodaClient`), así que cambiarlo después no toca el resto.
- **`org.json` del SDK**: lo pide el enunciado. Tiene mañas que el parser
  maneja (`optString` devuelve `"null"` para un JSON null, así que se mira
  `has`/`isNull`).
- **Tests de `org.json` con Robolectric, no con `org.json:json`**: en la JVM
  pura, `org.json` es el stub del `android.jar` y no hace nada. Agregar la
  librería `org.json:json` probaría **otra implementación** (con diferencias
  en `null`, `optString`, números). Robolectric trae la misma que el teléfono.
- **No se muestran correos**: en viviendas turísticas y guías de turismo son
  de personas naturales; para esta app no hacen falta.
- **El detalle vuelve a consultar** en vez de recibir el objeto: sobrevive a
  la muerte del proceso y no hay que pasar un `Parcelable` por el `Intent`.
- **Desplegables desde el servicio con `$group`**, no listas quemadas en el
  código: los valores son los reales del dataset (con sus tildes y su
  `NO DETERMINADO`) y se mantienen solos cuando el dataset cambia.
- **Paginación por `$offset`** con orden estable (`nombre, código`). Es
  simple y suficiente para este dataset; para saltos muy grandes sería más
  eficiente paginar por clave, pero no hace falta.
- **Dos pantallas, sin submenús** (pedido del profesor).
- **Tests**: 92 (ver `toolkit/DECISIONES-talleres-reto-10-tourism-registry.md`
  para el desglose).
