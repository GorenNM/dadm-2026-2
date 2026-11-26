# Reto 11 — Integrando IA generativa

Taller individual: un chatbot que conversa con un modelo de IA generativa de
la nube. Es una app nueva (no es el triqui ni el directorio), en Kotlin con
MVVM. Del reto 08 se reutilizó la configuración de Gradle, el tema, los
diálogos de tema y Acerca de, y la forma de armar listas y ViewModels. De la
configuración de Firebase del reto 07 se reutilizó el plugin, el BoM y dejar
`google-services.json` fuera de git.

El modelo es **Gemini**, llamado con **Firebase AI Logic** (backend *Gemini
Developer API*, plan gratuito). La app no lleva ninguna API key de Gemini.

Lo que pide el enunciado y cómo quedó:

| Enunciado | En la app |
|---|---|
| Integrarse con un modelo de IA generativa | Gemini (`gemini-3.8-flash`) a través del SDK `firebase-ai` |
| Chatbot, generación de texto o de imágenes | **Chatbot**: una pantalla con la conversación, respuesta en streaming y botón de detener |
| Prompt predefinido o construido con el usuario | Las dos: el usuario escribe su mensaje, o toca un *chip* de prompt sugerido en la conversación vacía |
| Modelo en la nube o libre | Nube (Gemini, vía Firebase) |

## Configurar Firebase AI Logic (una vez)

`app/google-services.json` **no está en el repo**: trae la API key del
proyecto de Firebase y el repo es público (`app/.gitignore` y
`.git/info/exclude` lo ignoran). Cada uno usa su propio proyecto:

1. Entrar a <https://console.firebase.google.com> y abrir un proyecto (se
   puede reutilizar el del reto 07) o *Crear proyecto* (Analytics no hace
   falta).
2. *Build → AI Logic → Comenzar*. Elegir **Gemini Developer API** (no el de
   Vertex/Agent Platform: ese exige plan de pago) y dejar que la consola
   habilite las APIs y cree la clave de Gemini. Esa clave la guarda Firebase,
   no la app.
3. *Configuración del proyecto → Tus apps → Agregar app → Android*, con el
   paquete `co.edu.unal.aichat` (el SHA-1 no hace falta). Descargar
   `google-services.json` y copiarlo a `app/`.
4. Seguir con la sección **App Check** de más abajo: sin ella el modelo
   rechaza las llamadas si el enforcement está activo.

Sin `google-services.json` el proyecto **sí compila** (el plugin
`com.google.gms.google-services` solo se aplica si el archivo existe), y la
app abre mostrando "Falta configurar Firebase" con el campo de texto
deshabilitado. Después de agregar el archivo hay que volver a compilar.

## Nota de seguridad

- **No hay API key de Gemini en el APK**: está en Firebase y la app solo
  habla con Firebase AI Logic. Tampoco hay ninguna key en el código, en
  `BuildConfig`, en `local.properties` ni en los tests.
- `google-services.json` sí trae una key de Firebase. No es secreta (identifica
  el proyecto, igual que en el reto 07) y por eso queda fuera de git, porque
  GitHub la marca como secreto expuesto. Conviene restringirla en Google
  Cloud Console a apps Android con este paquete.
- **App Check** es lo que impide que otra app (o un script) use tu proyecto
  para llamar al modelo y gastar tu cuota: cada llamada lleva un token que
  prueba que viene de esta app. Ya está implementado (ver más abajo).
- Si se usara una key de Gemini directa dentro del APK (SDK `generativeai`
  viejo, o la key en `BuildConfig`), se podría extraer del APK. Para un taller
  es aceptable, pero **no es seguro**: lo correcto es un backend o proxy que
  guarde la key y llame al modelo.

## App Check (token de depuración)

El enforcement de App Check está activo para Firebase AI Logic, así que cada
llamada al modelo tiene que llevar un token. `firebase-ai` lo adjunta sola
(`AppCheckHeaderProvider`); la app solo instala el proveedor en
`ChatApp.onCreate`, antes de la primera llamada y solo si Firebase está
configurado:

- **Debug** (`src/debug/.../AppCheckSetup.kt`): `DebugAppCheckProviderFactory`.
  Genera un token propio de cada instalación.
- **Release** (`src/release/.../AppCheckSetup.kt`): `PlayIntegrityAppCheckProviderFactory`.
  La librería de debug no entra al release.

Cómo registrar el token de un build de depuración:

1. Correr un build debug en el emulador o teléfono y abrir la app.
2. En Logcat buscar `Enter this debug secret into the allow list` (etiqueta
   `DebugAppCheckProvider`): al final del mensaje viene el token (un UUID).
3. En la consola: *App Check → Apps → Chat IA R11 → ⋮ → Administrar tokens de
   depuración → Agregar*, y pegar el token.
4. Volver a la app y tocar *Reintentar*.

Cosas a tener en cuenta:

- El token **cambia** al borrar los datos de la app, al reinstalarla y en cada
  emulador nuevo: hay que registrar el nuevo.
- El token es **secreto**: cualquiera que lo tenga se salta App Check. No va
  en el código, ni en `BuildConfig`, ni en `local.properties`, ni en git, ni
  en capturas de Logcat que se compartan.
- Si App Check rechaza la llamada, la app muestra "Esta app no pasó la
  verificación de App Check. Si es un build de depuración, registra su token".
- En **release** se usa Play Integrity, que requiere la app publicada en Google
  Play y el proveedor activado en la consola (*App Check → Apps → Play
  Integrity*). Un release instalado a mano no pasa.
- Opcional, no activado: `Firebase.ai(..., useLimitedUseAppCheckTokens = true)`
  más la protección contra repetición (*replay protection*) en la consola.

## Modelo

`ChatConfig.MODEL_NAME = "gemini-3.8-flash"`. Es el modelo Gemini "Flash"
estable (GA) más nuevo que la documentación de Firebase AI Logic
(<https://firebase.google.com/docs/ai-logic/models>) lista para el Gemini
Developer API, consultada el 2026-11-25: sin etiqueta *preview* ni fecha de
retiro. Los `gemini-2.5-*` aparecen con redirección desde el 2026-12-01 y los
`gemini-3.5`/`3.7` ya se redirigen a uno más nuevo.

Para cambiarlo basta con editar esa constante (`data/ChatConfig.kt`) por otro
ID de la lista. La instrucción de sistema (asistente amable, responde en el
idioma del usuario, conciso) está en la misma clase.

## Clases (MVVM)

```
co.edu.unal.aichat
├── ChatApp                  tema guardado + App Check + el cliente (null si Firebase no está configurado)
├── ThemePreference, AppDialogs    del reto 08: tema claro/oscuro y Acerca de
├── data/
│   ├── ChatClient           interfaz: stream(historial): Flow<String>
│   ├── FirebaseChatClient   la única clase que conoce firebase-ai, y el mapeo excepciones → ChatError
│   ├── ChatMessage          mensaje (id, rol, texto, estado) + qué parte del historial viaja al modelo
│   ├── ChatError            tipos de error propios + ChatException
│   ├── ChatConfig           modelo, instrucción de sistema, topes
│   └── ChatHistoryCodec     historial <-> String para el SavedStateHandle
└── ui/
    ├── ChatActivity         la pantalla: burbujas, chips, aviso de error, menú ⋮
    ├── ChatViewModel        la conversación: enviar, detener, reintentar, nueva conversación
    ├── MessagesAdapter      ListAdapter + DiffUtil, burbujas del usuario y del modelo
    └── ChatErrorLabels      ChatError → texto
```

(Por variante de build: `AppCheckSetup` en `src/debug` y en `src/release`.)

`ChatViewModel` recibe un `ChatClient?` (una interfaz; `null` = Firebase sin
configurar, y la pantalla lo explica). En los tests recibe un
`FakeChatClient` programable. La interfaz nunca ve una excepción del SDK:
`FirebaseChatClient` las convierte en `ChatException(ChatError)`.

## Streaming, historial y errores

- **Cliente sin estado**: no se usa el objeto `Chat` del SDK. Cada envío manda
  el **historial completo** (últimos 40 mensajes con texto, empezando por uno
  del usuario) a `generateContentStream`, y los fragmentos que llegan se van
  agregando a la burbuja del modelo.
- **Detener** cancela la corrutina y deja lo recibido marcado "(detenido)". Si
  todavía no había llegado nada, la burbuja vacía desaparece.
- **Errores** (cada uno con su mensaje y el botón *Reintentar*, que reenvía el
  último mensaje del usuario y descarta lo que haya llegado de la respuesta
  fallida):

  | `ChatError` | Cuándo |
  |---|---|
  | `NETWORK` | Sin conexión o timeout (`IOException`, `RequestTimeoutException`) |
  | `QUOTA` | `QuotaExceededException`, o 429 / "quota" en el mensaje |
  | `CONFIGURATION` | `InvalidAPIKeyException`, `APINotConfiguredException`, `ServiceDisabledException`, `PermissionMissingException`, o 403 |
  | `APP_CHECK` | Mensaje de App Check en la excepción (el SDK no tiene una propia: llega como `ServerException`) |
  | `BLOCKED` | `PromptBlockedException`, `ContentBlockedException`, `ResponseStoppedException` (seguridad) |
  | `LOCATION` | `UnsupportedUserLocationException`, `InvalidLocationException` |
  | `INTERRUPTED` | La respuesta se cortó porque el proceso murió (lo pone el ViewModel al restaurar) |
  | `UNKNOWN` | Cualquier otra, o una respuesta vacía |

  Una respuesta que termina por `MAX_TOKENS` no es error: se muestra lo que hay.
- **Copiar**: pulsación larga sobre una burbuja la copia al portapapeles.
- **Nueva conversación** (⋮): pide confirmación y borra todo.

## Rotar y matar el proceso

| Caso | Qué pasa |
|---|---|
| Rotar, incluso a mitad de una respuesta | El ViewModel sigue con la petición: la respuesta continúa llegando en la pantalla nueva |
| Android mata el proceso | La conversación está en el `SavedStateHandle` (texto simple, últimos 40 mensajes y como mucho 60 000 caracteres) y vuelve tal cual |
| El proceso muere a mitad de una respuesta | Al volver no queda una burbuja "cargando": lo recibido queda como "(interrumpido)" y sale el aviso con *Reintentar*. Si no había llegado nada, la burbuja vacía no vuelve |
| Rotar con el diálogo de "nueva conversación" abierto | Se cierra (no es parte del estado) |

## Cómo correrlo

1. Configurar Firebase AI Logic y App Check (secciones anteriores).
2. Android Studio → *Open* → `talleres/reto-11-ai-chat`, esperar el *Gradle
   Sync* y darle *Run 'app'*.

Tests (no requieren emulador ni Firebase ni red; no llaman al modelo):

```powershell
cd talleres\reto-11-ai-chat
.\gradlew.bat testDebugUnitTest
```

La primera vez, Robolectric descarga el jar de Android 17 (~240 MB) a
`~/.m2`.

### Guion de prueba

1. **Sin configurar**: compilar sin `app/google-services.json`. Abre la app:
   "Falta configurar Firebase…", sin chips y con el campo deshabilitado. No se cae.
2. Agregar `google-services.json` y compilar otra vez. Aparecen los 4 chips de
   prompts sugeridos.
3. Si es la primera vez en este emulador: buscar el token de depuración en
   Logcat y registrarlo (sección App Check). Sin eso, el primer mensaje da
   "Esta app no pasó la verificación de App Check": registrar y *Reintentar*.
4. Tocar "Explícame qué es MVVM en Android": aparece como burbuja del usuario
   (derecha), "Pensando…" y la respuesta llega por fragmentos (izquierda).
5. Escribir un mensaje de seguimiento ("¿y cómo se testea?"): la respuesta usa
   el contexto de la conversación (el historial completo viaja).
6. Escribir un mensaje largo ("Escribe un cuento de 500 palabras") y tocar
   **Detener** (el botón de enviar cambia a stop) a mitad: queda lo recibido
   con "(detenido)" y se puede seguir escribiendo.
7. Rotar el emulador a mitad de otra respuesta: la conversación sigue y la
   respuesta continúa llegando.
8. **Modo avión** y enviar un mensaje: "No hay conexión…" con *Reintentar*.
   Quitar el modo avión y tocar *Reintentar*: llega la respuesta, sin duplicar
   el mensaje.
9. Matar el proceso con la app en segundo plano (`adb shell am kill
   co.edu.unal.aichat`) y volver desde Recientes: la conversación sigue ahí.
   Hacerlo en medio de una respuesta: queda "(interrumpido)" con *Reintentar*.
10. Mantener pulsada una burbuja: "Mensaje copiado" (pegarlo en otra app).
11. ⋮ → **Nueva conversación** → *Cancelar* no borra; *Empezar* sí, y vuelven
    los chips. Con la conversación vacía, la opción está deshabilitada.
12. ⋮ → Tema y Acerca de (muestra "Reto 11 · v11.0").
13. Un mensaje que el filtro de seguridad bloquee, o agotar la cuota del plan
    gratuito, muestran sus mensajes propios (no hace falta provocarlos: están
    probados con el cliente falso).

## Datos del proyecto

| Campo | Valor |
|---|---|
| Paquete | `co.edu.unal.aichat` |
| Activity inicial | `ui.ChatActivity` |
| Versión | `versionCode 11`, `versionName "11.0"`; la app se llama "Chat IA R11" / "AI Chat R11" |
| minSdk / targetSdk | 24 / 37 |
| Lenguaje | Kotlin + ViewBinding + ViewModel (MVVM) |
| Modelo | Gemini `gemini-3.8-flash`, Firebase AI Logic (Gemini Developer API) |
| Nube | Firebase BoM 35.2.0: `firebase-ai`, `firebase-appcheck-debug` (debug), `firebase-appcheck-playintegrity` (release). Plugin `google-services` 4.6.0 |
| Tests | JUnit 4, kotlinx-coroutines-test, Robolectric 4.17 (solo un test de `ChatApp`) |

## Decisiones

- **Gemini vía Firebase AI Logic** y no la API de Gemini directa: así la key
  de Gemini no viaja en el APK, y App Check la protege.
- **Cliente sin estado**: se manda el historial completo en cada envío. Es más
  verboso que `startChat()`, pero el historial vive en un solo lugar (el
  ViewModel), sobrevive a la muerte del proceso y se puede probar que viaja.
- **Texto plano** en las burbujas: el modelo responde con Markdown (`**`, `-`),
  pero no se agregó una librería para renderizarlo.
- **Historial recortado** a 40 mensajes (y 60 000 caracteres al guardar): lo
  que se ve en pantalla no se recorta, solo lo que viaja al modelo y lo que
  se guarda.
- **Formato propio para guardar el historial** (separadores de control) en
  vez de `org.json`, que en los tests de la JVM no existe.
- **Plugin `google-services` condicional**: se aplica solo si existe el JSON,
  para que el proyecto compile (y los tests corran) sin configurar Firebase.
- **Una pantalla, sin submenús** (pedido del profesor): el ⋮ solo tiene
  Nueva conversación, Tema y Acerca de.
- **App Check por source set** (debug/release) con un objeto de la misma
  firma, para que el release no dependa de la librería de debug.
