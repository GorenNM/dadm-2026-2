# Reto 03 — Juego Triqui (Tic-Tac-Toe)

Taller individual: tutorial "Tic-Tac-Toe App" del curso, portado a Kotlin.

Triqui con la **X verde** y la **O roja**, en dos modos:

- **vs Android**: el humano juega con X contra la IA del enunciado. Android
  espera un momento antes de mover y su jugada aparece con una animación.
- **2 jugadores**: Jugador 1 (X) contra Jugador 2 (O) en el mismo teléfono.

Arriba del tablero se resalta de quién es el turno. **New Game / Nuevo juego**
está como botón bajo el tablero y como ícono ↻ en la barra superior.

## Qué se hizo

- **`TicTacToeGame.kt`**: toda la lógica del juego, sin nada de Android.
  Portada de `TicTacToeConsole.java` con los métodos públicos que pide el
  enunciado: `clearBoard()`, `setMove()`, `getComputerMove()`,
  `checkForWinner()`. La IA gana si puede, si no bloquea, si no juega al azar.
- **`AndroidTicTacToeActivity.kt`**: solo UI. Tablero de 9 botones, texto de
  estado, `mGameOver` para no seguir jugando al terminar, menú "New Game".
  Además (fuera del enunciado): selector de modo, indicador de turno, pausa y
  animación en la jugada de Android, botón visible de "New Game".
- **`activity_main.xml`**: `LinearLayout` + `TableLayout` (3 `TableRow` × 3
  botones) + `TextView` de estado.
- **`strings.xml`**: ningún texto quemado en el código. Inglés por defecto y
  español en `values-es/` (se usa automáticamente si el teléfono está en español).
- **Extra Challenge**: se alterna quién empieza en cada partida y hay un
  marcador (Humano / Empates / Android) posicionado con `RelativeLayout`.
- **Tests JUnit** de la lógica en `app/src/test/.../TicTacToeGameTest.kt`.

## Cómo correrlo

1. Android Studio → *Open* → `talleres/reto-03-juego-triqui`.
2. Esperar el *Gradle Sync*.
3. Elegir un emulador o dispositivo y darle *Run 'app'*.

Tests de la lógica (no requieren emulador):

```powershell
cd talleres\reto-03-juego-triqui
.\gradlew.bat testDebugUnitTest
```

## Datos del proyecto

| Campo | Valor |
|---|---|
| Paquete | `co.edu.unal.tictactoe` |
| Activity | `AndroidTicTacToeActivity` |
| minSdk / targetSdk | 24 / 37 |
| Lenguaje | Kotlin + ViewBinding |

## Diferencias con el enunciado

El enunciado es de la época de Eclipse, Java y teléfonos con botón físico de
menú, así que se adaptó a Android actual:

- Kotlin + ViewBinding en vez de Java + `findViewById`.
- `match_parent` y tamaños de texto en `sp` en vez de `fill_parent` y `dp`.
- "New Game" en la barra superior (`MaterialToolbar`) en vez del botón físico
  de menú.
- Todo en el paquete `co.edu.unal.tictactoe` (el enunciado mezcla dos paquetes).
- Colores de X y O en `colors.xml` en vez de valores en el código.
- Sin ViewModel: al rotar la pantalla la partida se reinicia (guardar estado es
  el tema del reto 06).
