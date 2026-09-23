# Reto 05 — Gráficos y Sonidos

Taller individual: tutorial "Graphics and Sound" del curso, sobre la base del
Triqui del reto 04 (menús y diálogos), portado a Kotlin.

El tablero de botones se reemplazó por un `View` custom (`BoardView`) que
dibuja la grilla y las fichas a mano sobre un `Canvas`, y cada jugada suena
un efecto distinto según quién mueve.

## Qué se hizo

- **`BoardView.kt`** (nuevo): `View` custom que reemplaza el `TableLayout` de
  botones. `onDraw()` dibuja las dos líneas verticales y las dos horizontales
  de la grilla, y una ficha X u O por cada celda ocupada (`TicTacToeGame`).
  Reporta toques mediante `setOnCellTouchedListener`, y anima con un
  `ValueAnimator` la ficha recién puesta (aparece chica y "rebota" a su
  tamaño, `OvershootInterpolator`, igual efecto que el reto 04 tenía con los
  botones).
- **`BoardGeometry.kt`** (nuevo): el cálculo de "qué rectángulo le toca a la
  celda (row, col)" y "qué celda corresponde a un toque en (x, y)" vive acá,
  sin nada de Android — se testea con JUnit sin emulador. `BoardView` es la
  única que lo usa.
- **`SoundEffects.kt`** (nuevo): wrapper sobre `SoundPool` con `play(player)`
  y un flag `muted`. Carga los dos efectos (`res/raw/move_human.wav`,
  `move_computer.wav`) al crearse, se libera en `onDestroy`.
- **`TicTacToeGame.kt`**: se agregó `getBoardOccupant(location)` (la pide el
  enunciado para que `BoardView` sepa qué dibujar en cada celda). El resto
  queda igual que en el reto 04.
- **`AndroidTicTacToeActivity.kt`**: se sacó `mBoardButtons`/
  `ButtonClickListener`; ahora hay un `mBoardView` (via ViewBinding) con su
  propio listener de celda tocada, y `setMove()` llama a
  `mSoundEffects.play(player)` y `mBoardView.refresh(animateCell = location)`.
  El menú de opciones suma un ítem para silenciar/activar el sonido, que
  cambia de ícono y título según el estado (`onPrepareOptionsMenu`). Modo de
  juego, dificultad, marcador y diálogos de about/quit quedan igual que en
  el reto 04.
- **`res/drawable/ic_piece_x.xml`, `ic_piece_o.xml`**: las fichas del
  tablero, vectores propios con los mismos colores que ya usaba el reto 04
  (`human_player` verde, `computer_player` rojo) en vez de bitmaps bajados de
  internet.
- **`res/drawable/ic_sound_on.xml`, `ic_sound_off.xml`, `ic_theme.xml`**:
  íconos del menú, Material Icons (Apache 2.0).
- **`res/drawable/bg_board.xml`** (extra, no lo pide el enunciado): fondo del
  `BoardView` (tarjeta con borde y esquinas redondeadas, colores del tema).
  Sin esto el tablero quedaba como un cuadro en blanco: el `Canvas` solo
  dibuja líneas grises finas, y antes (reto 04) ese espacio lo ocupaba una
  grilla de botones delineados que le daba peso visual.
- **`ThemePreference.kt`, `TicTacToeApp.kt`** (extra, pedido por el usuario):
  selector de tema Claro/Oscuro/Según el sistema en el menú de opciones.
  `TicTacToeApp` (nueva `Application`, registrada en el Manifest) aplica el
  tema guardado antes de crear la Activity, para que no parpadee entre claro
  y oscuro al abrir la app.
- **`res/raw/move_human.wav`, `move_computer.wav`**: efectos generados por
  código (tonos sintéticos cortos), no bajados de internet.
- **Tests**: `BoardGeometryTest.kt` nuevo (bordes de celda, mapeo de toque a
  celda, y el caso límite del toque justo en el borde del tablero).
  `TicTacToeGameTest.kt` suma un test de `getBoardOccupant`; el resto no se
  tocó.

## Cómo correrlo

1. Android Studio → *Open* → `talleres/reto-05-graphics-and-sound`.
2. Esperar el *Gradle Sync*.
3. Elegir un emulador o dispositivo y darle *Run 'app'*.

Tests de la lógica (no requieren emulador):

```powershell
cd talleres\reto-05-graphics-and-sound
.\gradlew.bat testDebugUnitTest
```

## Datos del proyecto

| Campo | Valor |
|---|---|
| Paquete | `co.edu.unal.tictactoegraphics` |
| Activity | `AndroidTicTacToeActivity` |
| minSdk / targetSdk | 24 / 37 |
| Lenguaje | Kotlin + ViewBinding |

Paquete distinto al de los retos anteriores para que las apps puedan
instalarse a la vez sin pisarse.

## Diferencias con el enunciado

El enunciado es de ~2010 (Eclipse, Java, `MediaPlayer`), así que se adaptó:

- Kotlin en vez de Java; un solo constructor con `@JvmOverloads` en vez de
  los tres constructores manuales que pedía el `BoardView` original.
- `findViewById` → ViewBinding (`binding.board`), igual que el resto del
  proyecto desde el reto 03.
- Las X/O no son bitmaps bajados de internet (el enunciado sugiere buscarlos
  en Google): son `VectorDrawable` propios, nítidos en cualquier densidad y
  sin depender de una fuente externa con licencia poco clara.
- Los efectos de sonido no son mp3 bajados de un sitio de sonidos gratis:
  son dos tonos cortos generados por código (WAV sintetizado), sin problema
  de licencia ni dependencia de red.
- **`SoundPool` en vez de `MediaPlayer`**: `MediaPlayer` tiene latencia
  notable al arrancar cada reproducción, lo cual se nota feo en un juego con
  sonidos cortos y repetidos; `SoundPool` es el mecanismo pensado para eso.
  Por lo mismo, los sonidos se cargan una vez en `onCreate`/se liberan en
  `onDestroy`, no en `onResume`/`onPause` como sugiere el enunciado (acá no
  hay otra Activity compitiendo por el recurso).
- **Extra Challenge** (demorar el movimiento de Android con
  `Handler.postDelayed`) ya estaba resuelto desde el reto 04; se reutilizó
  tal cual.
- **Animación de la ficha**: el reto 04 animaba el "rebote" al colocar una
  ficha escalando el `Button`. Portado al `Canvas` con un `ValueAnimator`
  que reescala el rectángulo de destino de la ficha en cada frame y llama a
  `invalidate()`.
