# Informe MK8

## Qué se agregó
### Sistema de faltas (nuevo)
- **Qué se cobra:** (1) *barrida* a un rival sin haber tocado antes la pelota en ese planchazo (si la toca primero es limpia; dos barridas que se cruzan no se cobran); (2) *carga violenta*: choque en sprint (≥ 8,5 m/s) contra un rival cuando el que choca no está jugando la pelota. En un choque de frente a velocidades parecidas no se cobra nada.
- **Gravedad 1–4:** 1 base, +1 por detrás, +1 si el rival tenía la pelota, +1 si fue muy fuerte. Gravedad 3 = amarilla, 4 = roja. Cada 3 faltas del mismo jugador, amarilla por reincidencia. Segunda amarilla = roja.
- **Roja = expulsión temporal de 25 s de juego** (no definitiva: con un jugador de campo por equipo sería partido terminado). Vuelve por su campo. Mientras tanto no colisiona ni se dibuja.
- **Después de la falta:** pausa con silbato y cartel (quién, tipo, tarjeta) → cuenta de 2,5 s con todos acomodados → tiro libre (barrera a 5,5 m) o **penal** si fue dentro del área del infractor (punto de penal a 4,1 m, el mismo que dibuja la cancha). Los rivales quedan quietos hasta 2 s (3 s en penal) o hasta que la pelota se mueva. Falta en los últimos 4 s: se da tiempo para cobrar.
- **HUD:** faltas y tarjetas por equipo a los costados del marcador, "EXPULSADO n s", anillo pulsante donde fue la falta y donde se cobra, flecha sobre quien patea, filas nuevas en el resumen final, y las faltas bajan la nota.
- **IA:** con faltas activas solo se tira a barrer si la pelota está al alcance, y casi nunca dentro de su propia área.
- **Online:** el snapshot lleva faltas, tarjetas y expulsiones. **Cambia el protocolo (VERSION 3): una MK7 y una MK8 no se conectan.**
- Se puede apagar en Ajustes (`FALTAS Y TARJETAS`).

### Interfaz (otra pantalla)
`LobbyScreen` ahora es solo el menú principal. Nuevas: `SetupScreen` (modo, nombre, equipos, IP online; con camisetas y enfrentamiento) y `SettingsScreen` (dificultad, duración, volumen, faltas). Base común `MenuScreen`: lienzo virtual 1280x800 escalado a la ventana (el lobby de la MK7 no escalaba), soporte de mouse, teclas 1–5 para el modo.

### Ley de la ventaja (nueva)
- Si te cometen una falta (sin roja, fuera del área) y tu equipo conserva la pelota, **no se corta el juego**: la falta y su tarjeta quedan anotadas y se abre una ventana de hasta 2,5 s (`Constants.ADVANTAGE_*`).
- Se da por aprovechada si controlás la pelota ~1,3 s seguidos, la controlás ≥ 0,6 s al vencer la ventana, o rematás. Si el infractor se queda con la pelota (0,2 s), o la ventana vence con la pelota suelta, o se acaba el tiempo, **se vuelve atrás y se cobra el tiro libre donde fue la falta**.
- Nunca hay ventaja en penales ni con roja, ni con menos de 3 s de partido.
- Lógica pura en `sim/AdvantageRules.java`; cartel "¡VENTAJA!" + aviso sonoro suave (sin silbato). Se puede apagar en Ajustes.
- **Online:** el snapshot lleva `advantageTeam` y `advantageCount`. **Protocolo v4** (una MK8 anterior, v3, no se conecta con esta).

### Roja definitiva en 2v2 (opción)
- Ajuste "ROJA EN 2 VS 2": 25 s (por defecto) o hasta el final del partido. Nunca deja a un equipo sin jugadores de campo: si el compañero ya está expulsado para siempre, esa roja es de 25 s.

### Correcciones
- **IA con faltas:** antes cometía ~12 faltas y ~2,8 rojas por partido de 2 min (barría a ciegas y esprintaba contra el rival). Ahora solo barre con probabilidad normal si va a tocar la pelota antes que al rival; si el rival se interpone se arriesga poco; no esprinta a menos de 4,5 m de un rival; se acomoda del lado contrario del que lleva la pelota. Resultado: ~1,7 faltas y ~0,1 rojas por partido. **Costo:** casi no recupera por barrida (4,8 → ~0,6 por partido); defiende más con el cuerpo.
- **2v2 con un expulsado:** la IA rival ya no se queda "apoyando" si su compañero expulsado era el que presionaba, y no marca al rival expulsado; la cámara compartida apunta solo al jugador que sigue en cancha (antes, al punto medio con el cuerpo inactivo).

- **Cierre de ventana al pasar de un menú a otro (Jugar, Ajustes, Volver...):** `MenuScreen.hide()` liberaba fuente y renderers en medio del `render()` de la pantalla que se estaba reemplazando, que después seguía dibujando con recursos destruidos. Ahora la liberación espera al final del cuadro. Este error venía de la sesión anterior (los menús nunca se habían podido ejecutar con ventana) y no se detecta con los tests.

## Cómo se verificó
- Todo `core` compila contra LibGDX 1.12.1 (fuentes de GitHub; sin Maven no hay jars).
- **Física:** se usó el Box2D en Java puro de `gdx-box2d-gwt` (misma API) con un `SharedLibraryLoader` de relleno, **no el Box2D nativo de C++**: puede haber diferencias finas. Con eso corren **todas** las pruebas sin ventana.
- `FoulSelfTest` (sin física): juicio de faltas, tarjetas, área, barrido de 2080 faltas y las reglas de la ventaja.
- `FoulSimTest` (nuevo, **con física**): barrida que se cobra y su acomodo (pelota, barrera a 5,5 m), carga en sprint, caminar sin falta, entrada limpia, faltas apagadas, penal, secuencia 3ª falta → amarilla y 6ª → roja con expulsión de 25 s y regreso, ventaja aprovechada / perdida / pelota suelta / apagada, roja definitiva en 2v2, y 9 partidos de IA 1v1 + 3 de 2v2 completos (terminan, sin pausas colgadas, cuerpos coherentes).
- `NetSelfTest` (el snapshot con faltas y ventaja llega idéntico), `SimSelfTest`: pasan.

## NO verificado (importante)
- **Todo lo que se dibuja:** carteles, "¡VENTAJA!", HUD, tarjetas, anillo, textos de expulsión y las pantallas de menú (no hay GPU). Ojo con la pantalla de Ajustes: ahora tiene 7 filas y se reacomodó (filas de 48 px, ayuda más abajo); mirar que no se superponga.
- La **sensación de juego**: umbral de carga (8,5 m/s) al conducir, tiempos de la ventaja, qué tan floja quedó la IA al barrer.
- La repetición de un gol con un expulsado en cancha (el que vuelve de la expulsión se ve durante toda la repetición).
- El Box2D nativo real (ver arriba).

## Ajustes rápidos
Todo en `Constants.java`: sección "MK8: sistema de faltas" (umbrales, roja, barrera), "MK8: ley de la ventaja" y `AI_*` (conducta de la IA con faltas: `AI_RISKY_SLIDE_CHANCE`, `AI_FOUL_CAUTION_DIST`, `AI_FLANK_DIST`).

## Pendiente / ideas
Tiros libres con barrera física real (hoy la barrera son los jugadores rivales a 5,5 m; haría falta sincronizarla online), que la IA se oriente hacia la pelota antes de barrer (para recuperar más sin faltas), marcar en la repetición quién estaba expulsado.
