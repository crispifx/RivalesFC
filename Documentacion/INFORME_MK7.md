# Informe MK7

## Cómo se verificó
- Compilación de todo `core` contra LibGDX 1.12.1 (fuentes compiladas con `javac`).
- Box2D real (C++ de LibGDX) compilado a una librería nativa con un puente JNI mínimo; `MatchSimulation` se ejecutó sin ventana.
- `core/src/test/.../sim/SimSelfTest.java`: 30 comprobaciones (sprint, esquinas, pelota trabada, IA vs IA, interpolación, flujo completo del partido, frame colgado). Correr con `main()`; con Gradle usa `Box2D.init()`, a mano `-Dbox2d.lib=/ruta/libgdx-box2d.so`.
- `NetSelfTest` sigue pasando.
- Mandos: la lógica de `Gamepads` se probó con un proveedor simulado (18 comprobaciones: zona muerta, flancos, desconexión, proveedor que falla).
- Compilación completa con y sin los cambios de 4 jugadores/mandos, sin LibGDX disponible: los únicos errores son por esa falta, ninguno en símbolos propios.
- **No verificado:** dibujo en pantalla (minimapa, flecha, halo, estela, HUD escalado) ni el modo online de punta a punta: no hay GPU ni nativos de ventana en el entorno de prueba. Compilan, pero conviene mirarlos corriendo el juego.

## 2v2 local cooperativo
- Nuevo `GameMode.TWO_VS_TWO`: `MatchSimulation` suma `mateLeft` (humano, se maneja con el segundo teclado) y `mateRight` (IA). La repetición, el reposicionamiento, la interpolación de render y la posesión cuentan a los cuatro.
- `SupportAI` tiene ahora rol (presiona / apoya-cubre). Medición con la física real: partidos completos, goles de ambos lados, distancia media entre los dos rojos ~7 m, pelota quieta máx. ~6 s.
- Lobby: tarjeta nueva (fila 2, atajo `3`), filas y alturas compactadas para que entre en 800 px de alto.
- Teclas alternativas para J2 (`I J K L` + `N` / `,` / `.`) sumadas a las flechas; se pueden usar juntas.
- **Modo `GameMode.FOUR_PLAYERS` (2 humanos vs 2 humanos):** J3 y J4 (equipo rojo) con mandos. `MatchSimulation.setRightHumans()` / `isRightHuman()` y `GameMode.isTeamMode()`. Los lugares sin mando los ocupa la CPU, también durante el partido; si el compañero rojo es humano, la CPU que queda siempre presiona la pelota.
- **Mandos:** `input/Gamepads.java` (core) y `desktop/.../GlfwGamepads.java`, vía GLFW, sin dependencias nuevas. Valen en todos los modos locales y en el lobby; START pausa.
- Lobby: la fila 3 alterna 2v2 / 4 jugadores con ← →, atajo `4`, contador de mandos. Partido: anillo y flecha por jugador (J2 celeste, J3 naranja, J4 violeta), minimapa, 3 líneas de controles en el saque.
- `SimSelfTest` tiene pruebas nuevas de 4 jugadores (setup, controles de J3/J4, IA que presiona si el compañero es humano, partido completo): **escritas pero no corridas**, no hay Box2D ejecutable en este entorno.
- Se eliminaron todos los comentarios del código (`.java` y `.gradle`); la compilación da exactamente los mismos resultados antes y después.
- **No verificado en pantalla:** el dibujo de los cuatro jugadores, los anillos y el layout del lobby compilan, pero no los vi corriendo. Tampoco probé el juego con cuatro manos reales en un solo teclado (el rollover de teclas depende del hardware). Tampoco se vieron los mandos reales, el orden de mando 1 / mando 2, la tarjeta del lobby con las flechas `< >` ni que las 3 líneas de controles no tapen el minimapa.

## Medido en MK6 y corregido en MK7
| Problema | Antes | Después |
|---|---|---|
| Velocidad con sprint | ~7,9 m/s (pedido 10,5) | ~10,5 m/s |
| Pelota quieta en juego (IA vs IA) | hasta 55 s | máx. 6,4 s |
| NORMAL IA vs IA | 0 remates / 0 goles en 3 min (6 de 6 partidos) | goles de ambos lados |
| Frame colgado de 5 s | ~150 ticks de golpe | 2 ticks |
| Pelota al alcance al conducir a sprint | ~50 % del tiempo | ~88 % |

## Sin cambios a propósito
Balance de tiros contra el arquero (a máxima potencia: 70 / 63 / 53 % de gol en FÁCIL / NORMAL / DIFÍCIL) y regla del gol (cuenta con el centro de la pelota ya pasada la línea).

## Notas de medición
- Planchazo: 0,33 s deslizando + 0,33 s de recuperación; gana la pelota al rival que la lleva en 24 de 40 intentos de prueba.
- IA vs IA en FÁCIL sale muy desparejo (el rojo gana casi siempre) incluso con las dos IA manejadas por el mismo camino: ambas llegan juntas al centro y el duelo contra el muro lo decide una diferencia numérica mínima del motor. Es un artefacto de enfrentar dos IA idénticas y deterministas; no mide persona contra IA. NORMAL y DIFÍCIL salen parejos.
- Un primer arnés mío tenía un error (no limpiaba banderas de entrada); se corrigió y no afectó al juego.

## Pendiente / ideas
- Mirar en pantalla el tamaño del minimapa y de la pelota a 1080p y 4K.
- El lobby no escala con la resolución.
