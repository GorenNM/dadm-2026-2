# Reto 04 — Menús y Diálogos

Taller individual: tutorial "Menus and Dialog Boxes" del curso, sobre la base
del Triqui del reto 03, portado a Kotlin.

Menú de opciones (⋮) con cuatro ítems: **New Game** (además visible como
ícono ↻ en la barra), **Difficulty**, **About** y **Quit**.

## Qué se hizo

- **`TicTacToeGame.kt`**: `DifficultyLevel` (`Easy`, `Harder`, `Expert`) y la
  propiedad `difficultyLevel` (por defecto `Expert`, para no cambiar el
  comportamiento del reto 03). `getComputerMove()` se separó en
  `getWinningMove()` / `getBlockingMove()` / `getRandomMove()` según pide el
  enunciado: Easy juega al azar, Harder gana si puede y si no juega al azar
  (sin bloquear), Expert gana, bloquea o juega al azar en ese orden.
- **`AndroidTicTacToeActivity.kt`**: `onOptionsItemSelected` gana los casos
  `ai_difficulty`, `about` y `quit`. Cada uno abre un `AlertDialog` construido
  al vuelo (no `showDialog()`/`onCreateDialog()`, deprecados y eliminados de
  `Activity` hace años):
  - **Difficulty**: `setSingleChoiceItems` con las tres opciones, la actual
    preseleccionada; al elegir una, cambia `mGame.difficultyLevel` y muestra
    un `Toast`.
  - **Quit**: confirmación Sí/No; "Sí" llama a `finish()`.
  - **About** (Extra Challenge): infla `about_dialog.xml` con
    `layoutInflater` y lo pone en el diálogo con `setView()`.
- **`res/menu/menu_main.xml`**: los cuatro ítems. "New Game" sigue visible
  como ícono (`showAsAction="ifRoom"`); los otros tres van en el overflow
  (`showAsAction="never"`) porque son configuraciones poco frecuentes.
- **Íconos**: tres vectores nuevos (`ic_difficulty`, `ic_about`, `ic_quit`),
  Material Icons (Apache 2.0), mismo estilo que el `ic_refresh` del reto 03.
- **Ícono de la app** (Extra Challenge): `ic_launcher_foreground.xml` /
  `ic_launcher_background.xml` rehechos con una X verde y una O roja (mismos
  colores de `colors.xml`) sobre fondo oscuro, en vez del robot de Android por
  defecto. Es un ícono adaptativo (Android 8+); ver "Diferencias con el
  enunciado" abajo.
- **`res/layout/about_dialog.xml`**: ícono de la app, nombre, descripción
  corta, autor y curso.
- **Tests JUnit** nuevos en `TicTacToeGameTest.kt` para los tres niveles de
  dificultad (con un `Random` de índice fijo para que Easy/Harder sean
  deterministas), sin tocar los del reto 03.

## Cómo correrlo

1. Android Studio → *Open* → `talleres/reto-04-menus-dialogos`.
2. Esperar el *Gradle Sync*.
3. Elegir un emulador o dispositivo y darle *Run 'app'*.

Tests de la lógica (no requieren emulador):

```powershell
cd talleres\reto-04-menus-dialogos
.\gradlew.bat testDebugUnitTest
```

## Datos del proyecto

| Campo | Valor |
|---|---|
| Paquete | `co.edu.unal.tictactoemenu` |
| Activity | `AndroidTicTacToeActivity` |
| minSdk / targetSdk | 24 / 37 |
| Lenguaje | Kotlin + ViewBinding |

Paquete distinto al del reto 03 (`co.edu.unal.tictactoe`) para que las dos
apps puedan instalarse a la vez sin pisarse.

## Diferencias con el enunciado

El enunciado es de 2010 (Eclipse, Java, `showDialog()`), así que se adaptó:

- Kotlin en vez de Java; getter/setter de dificultad como propiedad Kotlin
  (`var difficultyLevel`) en vez de `getDifficultyLevel()`/`setDifficultyLevel()`.
- `AlertDialog` construido directamente en cada acción del menú, en vez de
  `showDialog(id)` + `onCreateDialog(id)` (API eliminada de `Activity`).
- Íconos del menú como vectores en `res/drawable`, no PNG en carpetas por
  densidad (`drawable-hdpi`/`-mdpi`/`-ldpi`).
- El menú ya existía como `res/menu/menu_main.xml` desde el reto 03; se le
  agregaron los ítems nuevos ahí en vez de crear `options_menu.xml` aparte.
- **Ícono de la app**: el ícono adaptativo (`ic_launcher_foreground.xml` +
  `ic_launcher_background.xml`) queda con el diseño nuevo, pero los mipmaps
  de respaldo para Android 7 (API 24-25, que no soportan íconos adaptativos)
  siguen siendo los de la plantilla de Android Studio — regenerarlos bien
  requiere el Image Asset Studio del IDE, no algo que se pueda hacer por
  código/terminal. En un emulador con Android 8+ (lo normal hoy) se ve el
  ícono nuevo sin problema.
