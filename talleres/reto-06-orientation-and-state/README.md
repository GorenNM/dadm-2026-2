# Reto 06 — Cambio de orientación y guardado de estado

Taller individual: tutorial "Changing the Orientation and Saving State" del
curso, sobre la base del Triqui del reto 05 (gráficos y sonidos), portado a
Kotlin.

La app ahora funciona en landscape con un layout propio, no pierde la partida
al rotar la pantalla y recuerda el marcador y las preferencias entre
reinicios. En vez de guardar el estado a mano en la Activity con
`onSaveInstanceState`, como hace el enunciado, se usó un **`ViewModel`**
(MVVM). El resultado es el mismo y el código queda más ordenado.

## Qué se hizo

- **`res/layout-land/activity_main.xml`** (nuevo): tablero a la izquierda;
  modo de juego, turno, mensaje, marcador y "Nuevo juego" a la derecha (en un
  `ScrollView` por si la pantalla es baja). Tiene los mismos ids que el layout
  vertical, así ViewBinding genera una sola clase para los dos.
  `res/values-land/dimens.xml` achica el tablero a 270dp, como pide el
  enunciado.
- **`TicTacToeViewModel.kt`** (nuevo): estado y reglas de la partida, fuera
  de la Activity. Expone un `StateFlow<GameUiState>` con lo que hay que
  dibujar y un `Flow<Move>` de eventos de una sola vez (sonido y animación de
  cada ficha). La jugada de Android pasó de `Handler.postDelayed` a una
  corrutina en `viewModelScope`.
- **`GamePreferences.kt`** (nuevo): `SharedPreferences` (`ttt_prefs`) detrás
  de una interfaz `GameStore`, para poder testear el ViewModel sin Android.
  Guarda el marcador, la dificultad, el modo de juego y si el sonido está
  silenciado.
- **`TicTacToeGame.kt`**: se agregaron `getBoardState()` y `setBoardState()`,
  que pide el enunciado. Las dos copian el array, así nadie de afuera queda
  enlazado al tablero interno.
- **`BoardView.kt`**: ya no conoce `TicTacToeGame`. Dibuja la lista que le
  pasan (`setBoard`), anima la ficha nueva (`animateMove`) y se mide siempre
  cuadrado, para que en landscape entre aunque el alto disponible sea menor
  que 270dp.
- **`AndroidTicTacToeActivity.kt`**: solo UI. Observa el estado del
  ViewModel con `repeatOnLifecycle`, arma los textos y le pasa los toques.
  Los insets ahora incluyen `displayCutout`, porque en landscape el notch
  queda de costado.
- **Menú**: "Reset Scores" nuevo, con ícono (`ic_reset_scores`, Material
  Icons, Apache 2.0). Se sacó "Quit" con su diálogo, como pide el enunciado.
- **Tests**: `TicTacToeViewModelTest.kt` es nuevo (14 casos). Simula la muerte
  del proceso y revisa que se recuperen el tablero y el turno, y que Android
  no mueva dos veces. También revisa que la jugada pendiente de Android no se
  pierda (Extra Challenge 2) y que el marcador, la dificultad, el modo y el
  sonido sobrevivan a reiniciar la app. `TicTacToeGameTest.kt` suma 4 tests
  de `getBoardState()` y `setBoardState()`.

## Dónde vive cada dato

| Dato | Rotar | Android mata el proceso en 2º plano | Cerrar la app (Back) |
|---|---|---|---|
| Tablero, turno, fin de partida, quién empieza | `ViewModel` | `SavedStateHandle` | se pierde (partida nueva) |
| Marcador, dificultad, modo, sonido | `ViewModel` | `SharedPreferences` | `SharedPreferences` |
| Jugada pendiente de Android | la corrutina sigue viva en el `ViewModel` | se vuelve a programar al restaurar | — |

## Cómo correrlo

1. Android Studio → *Open* → `talleres/reto-06-orientation-and-state`.
2. Esperar el *Gradle Sync*.
3. Elegir un emulador o dispositivo y darle *Run 'app'*.
4. Para rotar el emulador: los botones de rotar de la barra lateral, o
   Ctrl+← / Ctrl+→ (el Ctrl-F11 del enunciado era del emulador viejo). La
   rotación automática tiene que estar activada en el sistema del emulador.

Tests (no requieren emulador):

```powershell
cd talleres\reto-06-orientation-and-state
.\gradlew.bat testDebugUnitTest
```

Para probar la muerte del proceso a mano: con la app en segundo plano,
correr `adb shell am kill co.edu.unal.tictactoeorientation` y volver a la
app desde Recientes.

## Datos del proyecto

| Campo | Valor |
|---|---|
| Paquete | `co.edu.unal.tictactoeorientation` |
| Activity | `AndroidTicTacToeActivity` |
| minSdk / targetSdk | 24 / 37 |
| Lenguaje | Kotlin + ViewBinding + ViewModel (MVVM) |

Paquete distinto al de los retos anteriores para que las apps puedan
instalarse a la vez sin pisarse.

## Diferencias con el enunciado

El enunciado es de ~2011 (Eclipse, Java), así que se adaptó:

- **`ViewModel` en vez de `onSaveInstanceState`/`onRestoreInstanceState` en
  la Activity**. Es la forma actual de Android (Jetpack) y la arquitectura
  del curso (MVVM). Al rotar, el ViewModel no se destruye, así que no hay
  nada que guardar ni restaurar. Para el caso en que Android mata el proceso,
  el ViewModel usa `SavedStateHandle`, que por dentro es el mismo `Bundle` de
  `onSaveInstanceState`, con las mismas claves que el enunciado (`board`,
  `mGameOver`, `mGoFirst`, más `mCurrentPlayer`).
- **El turno se guarda** (`mCurrentPlayer`). Eso evita el bug del paso 5,
  donde Android movía dos veces después de rotar.
- **El mensaje de info no se guarda como texto**: se arma de nuevo a partir
  del estado (quién mueve, si terminó y quién ganó). Así siempre sale en el
  idioma actual.
- **`SharedPreferences` se escribe apenas cambia el dato, no en `onStop`**.
  El ViewModel no se entera de `onStop`, y guardar al momento tampoco pierde
  nada si el proceso muere de golpe. Se usa la extensión KTX `edit { }` en
  vez de `commit()`.
- **La barra de título se mantiene en landscape** (el enunciado la saca
  para ganar espacio). En 2011 el menú se abría con el botón físico del
  teléfono; hoy vive en la toolbar, y ahí está "Reset Scores". El tablero de
  270dp entra igual.
- **Extra Challenge 1** (dificultad persistente): se guarda el `ordinal` del
  enum como `Int` y se convierte de vuelta con `DifficultyLevel.entries`. Si
  el número no es válido, queda Expert.
- **Extra Challenge 2** (Android no mueve si se rota mientras "piensa"): al
  rotar, la corrutina sigue viva en el ViewModel, así que Android mueve
  igual. Si el proceso muere en ese momento, el ViewModel nuevo ve en
  `init` que le toca a Android y vuelve a programar la jugada.
- **Extras no pedidos**: el modo de juego y el sonido silenciado también se
  guardan. El modo, para que el marcador guardado siempre corresponda al
  modo que se muestra. El sonido, porque antes se perdía al rotar. Además,
  cambiar el tema (que recrea la Activity) ya no reinicia la partida.
- **Versión en el About**: el diálogo "Acerca de" muestra "Reto 6 · v6.0".
  Todos los retos del triqui tienen el mismo nombre e ícono, y así se sabe
  cuál está abierto. Sale de `versionCode`/`versionName` en
  `app/build.gradle.kts` (el `versionCode` es el número de reto), leídos con
  `BuildConfig`. Por lo mismo, la app se llama "Triqui R6" ("Tic-Tac-Toe R6"
  en inglés) en el launcher y en la toolbar.
