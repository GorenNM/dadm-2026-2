# Reto 07 — Jugando Triqui Online

Taller individual: extender el triqui de los retos anteriores para que dos
jugadores jueguen cada uno en su teléfono. La base es el reto 06 (orientación
y guardado de estado), en Kotlin con MVVM.

Lo que pide el enunciado y cómo quedó:

| Enunciado | En la app |
|---|---|
| En un juego hay 2 jugadores | Quien crea el juego es X; quien se une es O |
| Cualquiera de los jugadores puede crear un juego | Botón "Crear juego" en la pantalla inicial |
| Al inicio se muestra la lista de juegos disponibles | La pantalla inicial es la lista (`LobbyActivity`), se actualiza sola |
| Un jugador se une a un juego disponible o crea uno nuevo | Tocar un juego de la lista, o "Crear juego" |
| Solo después de unirse se puede jugar | El creador ve "Esperando a que alguien se una…" y el tablero no responde hasta entonces |
| Un servicio en la nube media los juegos | Firebase Realtime Database + Firebase Authentication (anónima) |

El triqui del reto 06 sigue entero detrás de "Jugar sin conexión": vs
Android, 2 jugadores en el mismo teléfono, dificultad, marcador persistente,
sonido, tema y landscape.

## Cómo funciona

Cada juego es un nodo `/games/{id}` en Realtime Database:

```
status    "waiting" | "playing" | "finished" | "abandoned"
xUid/xName, oUid/oName   quién es X (creó el juego) y quién es O (se unió)
board     "X-O--X---"    9 casillas, '-' = vacía
turn      "X" | "O"      quién mueve
starter   "X" | "O"      quién empezó: la revancha la empieza el otro
winner    0 sigue, 1 empate, 2 ganó X, 3 ganó O
xWins, oWins, ties       marcador de este juego (lo ven los dos)
xOnline, oOnline         presencia (Firebase la pone en false si alguien se desconecta)
createdAt                hora del servidor
```

- **Sincronización**: cada teléfono escucha el nodo de su juego. Cuando
  cambia, el tablero se redibuja y suena la ficha nueva, sea propia o del
  rival.
- **Jugadas, unirse, revancha y salir** van en **transacciones**. Firebase
  aplica la función sobre la versión más nueva del nodo; si otro teléfono
  escribió en el medio, la vuelve a correr. La función es
  `OnlineRules` (Kotlin puro, sin Firebase) y valida el estado, el turno y
  la casilla, aplica la jugada y calcula el ganador con el mismo
  `checkForWinner` del triqui local.
- **Identidad**: autenticación anónima de Firebase. No se pide registro y el
  uid queda guardado en el teléfono, así que después de matar la app uno
  sigue siendo el mismo jugador. Además se elige un **apodo**, que es lo que
  los demás ven en la lista ("Juego de Fabio").
- **Reglas de seguridad** (`database.rules.json`): solo usuarios con sesión.
  Uno solo puede crear juegos a su nombre, solo escriben los dos jugadores, y
  solo puede unirse alguien cuando el juego espera rival. Además se valida la
  forma de los datos.

## Clases (MVVM)

```
co.edu.unal.tictactoeonline
├── TicTacToeGame, BoardView, BoardGeometry, SoundEffects,     del reto 06, sin cambios
│   ThemePreference, TicTacToeApp, TicTacToeViewModel
├── AndroidTicTacToeActivity   modo sin conexión; ya no es la pantalla inicial
├── GamePreferences            + PlayerStore: el apodo
├── AppDialogs                 diálogos de tema y Acerca de (los usan las dos pantallas)
└── online/
    ├── OnlineGame             modelo + OnlineRules (reglas puras) + conversión a/desde Firebase
    ├── GameRepository         interfaz del servicio en la nube
    ├── FirebaseGameRepository implementación: transacciones, onDisconnect, presencia
    ├── LobbyViewModel         lista de juegos, crear, unirse, apodo
    ├── LobbyActivity          pantalla inicial (RecyclerView + MaterialCardView)
    ├── GamesAdapter
    ├── OnlineGameViewModel    la partida en línea
    └── OnlineGameActivity     tablero, turno, marcador, revancha, salir
```

Los ViewModels reciben un `GameRepository` (una interfaz). En los tests
reciben un fake en memoria, que simula dos o tres jugadores contra el mismo
"servidor" y aplica las mismas `OnlineRules` que la transacción de Firebase.

## Casos borde

| Caso | Qué pasa |
|---|---|
| Rotar en plena partida | El ViewModel y la escucha siguen vivos; no se pierde nada |
| Android mata el proceso | El id del juego está en el `SavedStateHandle`; al volver se escucha otra vez el mismo juego, con el mismo uid |
| El rival pierde la conexión | Se muestra "Tu rival perdió la conexión…". La partida no se cierra por un corte breve; se puede esperar o salir |
| El rival sale (Back, flecha o "Salir") | A mitad de partida se pide confirmación. El juego queda abandonado y el otro ve "X abandonó la partida" |
| Dos se unen al mismo juego a la vez | La transacción deja entrar a uno solo; el otro ve "Alguien más se unió primero" |
| Los dos piden revancha a la vez | La partida se reinicia una sola vez |
| El creador se va antes de que alguien se una | El juego se borra (al salir, o solo con `onDisconnect` si se cae) |
| Juegos abandonados | El último en salir borra el juego. La lista solo muestra juegos que esperan rival y oculta los de más de una hora |

Lo que no se cubre: sin Cloud Functions (piden plan pago) no hay limpieza
del lado del servidor. Si una app se cae a mitad de partida y el otro
jugador tampoco sale, el nodo queda en la base, aunque no aparece en la
lista. Las reglas validan quién escribe y la forma de los datos, pero no que
la jugada sea legal: un cliente modificado podría hacer trampa. Para un
taller alcanza; en producción se validaría en el servidor.

## Configurar Firebase (una vez)

`app/google-services.json` **no está en el repo**: trae la API key del
proyecto de Firebase y el repo es público. Cada uno usa su propio proyecto:

1. Entrar a <https://console.firebase.google.com> → *Crear proyecto* (Google
   Analytics no hace falta).
2. *Authentication → Comenzar → Método de acceso → Anónimo →
   Habilitar*.
3. *Realtime Database → Crear base de datos*. Elegir ubicación,
   empezar en **modo bloqueado**, ir a la pestaña *Reglas*, pegar el contenido
   de `database.rules.json` y *Publicar*.
4. *Configuración del proyecto → Tus apps → Agregar app → Android*, con el
   paquete `co.edu.unal.tictactoeonline` (el SHA-1 no hace falta). Descargar
   `google-services.json` y copiarlo a `app/`. Hacerlo **después** del paso 3:
   así el archivo trae la URL de la base de datos.

Sin `google-services.json` el proyecto no compila (el plugin
`com.google.gms.google-services` lo exige).

La API key de Firebase no es una contraseña: identifica al proyecto, y lo
que protege los datos son las reglas. Igual queda fuera del repo, porque
GitHub la marca como secreto expuesto. Opcional: en Google Cloud Console se
puede restringir la key a apps Android con este paquete.

## Cómo correrlo

1. Configurar Firebase (sección anterior).
2. Android Studio → *Open* → `talleres/reto-07-online-play`, esperar el
   *Gradle Sync* y darle *Run 'app'*.
3. Para jugar hacen falta dos dispositivos:
   - **Emulador + teléfono** (recomendado): el teléfono con depuración USB
     activada, elegirlo en el selector de dispositivos y darle *Run* en cada
     uno.
   - **Dos emuladores**: en *Device Manager*, *Duplicate* sobre el AVD que ya
     existe y arrancar los dos. Usar imágenes "Google APIs" o "Google Play".

Cada instalación es un jugador distinto (su propio uid anónimo).

Tests (no requieren emulador ni Firebase):

```powershell
cd talleres\reto-07-online-play
.\gradlew.bat testDebugUnitTest
```

### Guion de prueba con dos dispositivos

1. A: elegir apodo → *Crear juego*. Queda esperando.
2. B: el juego de A aparece solo en la lista → tocarlo. Empieza A (X).
3. Jugar una partida completa: cada jugada aparece y suena en el otro
   teléfono. Las jugadas fuera de turno se ignoran.
4. Al terminar, *Revancha* en cualquiera de los dos: empieza el otro y el
   marcador se mantiene.
5. Rotar en plena partida: sigue igual.
6. Matar el proceso de B con la app en segundo plano
   (`adb -s <emulador> shell am kill co.edu.unal.tictactoeonline`) y volver
   desde Recientes: B sigue en la misma partida.
7. Cortar la red de B (modo avión): A ve "Tu rival perdió la conexión…"
   (Firebase tarda hasta ~1 min en detectar un corte abrupto). Al volver la
   red, el aviso desaparece.
8. B → *Salir*: A ve que B abandonó. A → *Salir*: el juego se borra.

### Para jugar con el profesor

El profesor no puede compilar desde el repo y jugar contra uno: con su
propio `google-services.json` caería en otra base de datos. Hay que pasarle
el APK: *Build → Generate App Bundles or APKs → Generate APKs* (queda en
`app/build/outputs/apk/debug/app-debug.apk`). Lo instala, elige un apodo y
se une al juego que uno creó, o al revés.

## Datos del proyecto

| Campo | Valor |
|---|---|
| Paquete | `co.edu.unal.tictactoeonline` |
| Activity inicial | `online.LobbyActivity` |
| Versión | `versionCode 7`, `versionName "7.0"`; la app se llama "Triqui R7" |
| minSdk / targetSdk | 24 / 37 |
| Lenguaje | Kotlin + ViewBinding + ViewModel (MVVM) |
| Nube | Firebase BoM 34.19.0: `firebase-database`, `firebase-auth` |

Paquete distinto al de los retos anteriores para que las apps puedan
instalarse a la vez sin pisarse.

## Decisiones

- **Realtime Database en vez de Firestore**: tiene `onDisconnect()`, que
  permite enterarse de que el rival se fue sin código de servidor, y
  transacciones sobre un nodo para las carreras. Alcanza el plan gratis.
- **Auth anónima en vez de un id inventado**: las reglas de seguridad
  necesitan un `auth.uid` que el servidor pueda verificar. El inicio de
  sesión con Google queda para el reto 12.
- **Las reglas del juego no dependen de Firebase** (`OnlineRules`): se
  testean en JVM (13 tests), y el fake de los tests usa exactamente las
  mismas que la app.
- **Marcador en línea aparte del local**: vive en el nodo del juego, lo ven
  los dos y no se mezcla con el marcador vs Android del modo sin conexión.
- **Pocas pantallas, sin submenús** (pedido del profesor): lista de juegos
  y partida. Las opciones poco usadas (apodo, tema, Acerca de, sonido) van
  en el menú ⋮.
- **Tests**: 32 nuevos (13 de `OnlineRulesTest`, 11 de
  `OnlineGameViewModelTest` y 8 de `LobbyViewModelTest`). Se suman a los 38
  del reto 06, que siguen pasando. Simulan dos jugadores, jugadas fuera de
  turno, doble toque, victoria y revancha simultánea, abandono, juego
  borrado, desconexión del rival, muerte del proceso y dos que se unen a la
  vez.
