# Rivales F.C. — MK8: faltas y menús nuevos

> ## Novedades de la MK8 (detalle y qué NO se verificó en `INFORME_MK8.md`)
>
> - **Faltas y tarjetas:** barridas sin tocar la pelota y cargas violentas se sancionan con tiro libre (o **penal** dentro del área), amarilla (grave o 3ª falta) y roja (expulsión de 25 s). Se apaga en Ajustes.
> - **Menús en pantallas separadas:** menú principal → *Jugar* (modo, nombre, equipos, IP online) / *Ajustes*. Escalan con la ventana y aceptan mouse.
> - **Ley de la ventaja** y opción de **roja definitiva en 2v2** (Ajustes). La IA ya no se llena de faltas.
> - **Online:** protocolo v4 (no es compatible con la MK7 ni con una MK8 anterior).
> - Pruebas: `FoulSelfTest` (sin física), `FoulSimTest` (con física), `NetSelfTest`, `SimSelfTest`.

# Rivales F.C. — MK7: visibilidad y arreglos de jugabilidad

> ## 2 vs 2 local cooperativo (nuevo en la MK7)
>
> Desde el lobby: tarjeta **2 VS 2 COOPERATIVO** (o la tecla `3`). **Dos compañeros juegan juntos en el mismo equipo desde el mismo dispositivo** contra un equipo rojo de dos jugadores de IA, con una sola cámara compartida a pantalla completa.
>
> - **J1 (azul):** WASD mover, Shift correr, Espacio cargar/patear, Ctrl izq. planchazo. **J2 (azul, anillo celeste):** Flechas, Ctrl der. correr, Enter cargar/patear, Shift der. planchazo.
> - **La IA del rojo se reparte los roles:** el más cercano a la pelota la presiona (con histéresis de 2 m) y el otro apoya (adelante y del lado contrario) o cubre (entre la pelota y su arco). Medido: se mantienen a ~7 m uno del otro.
> - **Cámara:** apunta al punto medio de los dos compañeros inclinada 45 % hacia la pelota, con 24 m de alto (casi toda la cancha). El minimapa muestra los cuatro; el punto celeste es J2.
> - Repetición de gol, reposicionamiento, estadísticas y pelota trabada funcionan con los cuatro jugadores. **Solo local** (no hay versión online) y no cambia el protocolo.
> - Los dos humanos comparten el mismo equipo: no hay "2 contra 2 humanos". Si querés 4 humanos (dos por equipo) se puede agregar sobre esta base, pero un solo teclado difícilmente aguanta ocho teclas de movimiento más acciones a la vez.

> ## Qué trae la MK7 (consolida los gráficos suaves de la versión anterior + visibilidad + bugs)
>
> Esta vez el proyecto se **compiló y se probó con la física Box2D real, sin ventana** (ver `SimSelfTest` y `INFORME_MK7.md`).
>
> **Jugabilidad (bugs medidos y corregidos)**
> - **Sprint real:** con Shift se llegaba a ~7,9 m/s en vez de 10,5 (la fuerza y el damping limitaban la velocidad). Ahora llega a ~10,5, y al soltar Shift la velocidad baja de a poco (antes frenazo seco).
> - **Pelota trabada:** las esquinas son ochavadas (corte de 1,2 m a 45°) y, si la pelota casi no se mueve durante ~5 s, se reubica hacia adentro con un cartel y silbato.
> - **IA que se quedaba empujando contra el muro** (0 remates y 0 goles en 3 min en dificultad NORMAL): ahora no apunta a puntos fuera de la cancha, alcanza con alinearse a 0,80 y apunta con el movimiento al soltar.
> - **Control de pelota:** el rebote jugador-pelota baja de 0,65 a 0,12 (los arqueros y muros siguen igual). Al conducir, la pelota queda al alcance ~88 % del tiempo (antes ~52 %): al correr contra ella ya no sale disparada más rápido que vos.
> - **R y ESC accidentales:** R solo reinicia en pausa o al final; ESC pide apretarlo dos veces durante el juego.
> - **Frames colgados:** un frame largo (arrastrar la ventana) ya no simula decenas de ticks de golpe. Al reanudar la pausa se descartan teclas pegadas.
>
> **Visibilidad**
> - **Movimiento suave:** la simulación sigue a 30 Hz pero se dibuja interpolada a 60 fps.
> - **Pelota visible:** se dibuja ~1,8x más grande (la física no cambia), con halo claro, estela cuando va rápida y rotación según el desplazamiento.
> - **Pantalla dividida:** vista de 20 m (antes 16) y cámara inclinada hacia la pelota y suavizada; **flecha amarilla con la distancia** cuando la pelota queda fuera del panel.
> - **Minimapa** abajo al centro con jugadores, arqueros, pelota y el recuadro que ve cada cámara.
> - **Línea de puntería** al cargar el tiro (misma cuenta que la simulación, incluye el ajuste por dirección) y barra de potencia más grande.
> - **HUD escalado** según el alto de la ventana (los paneles centrales de pausa/final/red quedan a escala fija).
> - **Quién tiene la pelota:** el anillo de tu jugador se pone amarillo cuando la tenés al alcance, y durante la cuenta regresiva del saque una flecha que rebota te marca.
>
> **No cambia el protocolo online.** Pruebas: `SimSelfTest` (nuevo, 30 comprobaciones) y `NetSelfTest`.

## Antes: gráficos suaves (adiós al pixelado)

> ## Novedades de la MK7 (solo visual; no cambia la jugabilidad ni el protocolo online)
>
> **Por qué se veía pixelado:** los jugadores eran sprites de 20x32 px y la pelota de 20x20, ampliados con filtro *Nearest* a una escala no entera; el pasto y la tribuna eran texturas minúsculas estiradas por toda la cancha (de ahí las rayitas verticales raras); las líneas de la cancha eran de 1 px sin suavizado; y la fuente era la Arial de 15 px de LibGDX, ampliada y sin algunos símbolos (el cuadradito en "MK6 — elegí").
>
> - **Arte en alta resolución con antialiasing** (`gfx/SoftCanvas`, `gfx/ArtPainter`, Java puro sin LibGDX): jugadores y arqueros de 120x192 con contorno, sombreado, cara, pelo y guantes; pelota de 160x160 con volumen y brillo; todo se suaviza por supermuestreo y se sube con **filtro trilineal + mipmaps** (`PixelArtFactory`), así que se ve bien a cualquier zoom.
> - **Cancha completa en una textura de 32 px/m:** pasto con rayas (10, el cambio cae en la línea central), manchas, hebras y grano; **líneas con bordes suaves** calculadas por distancia analítica (contorno, medio campo, círculo, áreas, puntos y medialunas, arcos de córner); arcos con red y postes; carteles publicitarios negro/dorado. Se cachea entre partidos.
> - **Tribuna real:** público sentado en 4 filas, con hinchas festejando, bufandas y profundidad; en vez de una textura de 24x2 celdas estirada. La de abajo va espejada para que el fondo quede siempre lejos de la cancha.
> - **Sombras suaves** bajo jugadores, arqueros y pelota; **anillo elíptico** suave para tu jugador (antes un círculo de 1 px).
> - **Fuente nítida** (`gfx/Fonts`): se genera con FreeType desde `assets/font.ttf` (Barlow Condensed Bold, licencia OFL en `assets/FONT_LICENSE_OFL.txt`) a 64 px con mipmaps. Trae á é í ó ú ü ñ ¡ ¿ — · y demás. Todo `setScale(x)` del código pasó a `setScale((x) * Fonts.K)`. Si FreeType o la fuente no cargan, usa la de respaldo suavizada y el juego arranca igual.
> - **Antialiasing MSAA 4x** (`DesktopLauncher`) para círculos, reloj y bordes del HUD; el logo usa mipmaps; el blur del rival se adaptó a la textura más grande.
> - **Arreglo de paso:** los carteles de nombre sobre los jugadores no se veían (con la fuente por defecto, el redondeo a píxeles enteros los colapsaba al dibujarlos en metros). Ahora `setUseIntegerPositions(false)` y se ven.
> - **Arreglo de paso:** los arcos de córner estaban dibujados hacia afuera de la cancha; ahora van hacia adentro.
> - **Nueva dependencia:** `gdx-freetype` y `gdx-freetype-platform:natives-desktop` (ya agregadas en `build.gradle`); Gradle las baja solo.
> - **Sin compilar contra LibGDX:** el arte (`SoftCanvas`/`ArtPainter`) se compiló y se probó como Java puro (previsualizado como imágenes); `Fonts` y `PixelArtFactory` se verificaron contra stubs de la API; `MatchScreen`/`LobbyScreen`/`DesktopLauncher` se revisaron de sintaxis pero **no se compilaron con LibGDX**. Probar en partida; si algo no compila, el error va a estar en esas pocas líneas.
> - **Ajustes rápidos:** tamaño de letra = `Fonts.TARGET_SIZE`; nitidez del suelo = `PixelArtFactory.GROUND_PPM`; colores del pasto y cantidad de rayas en `ArtPainter.ground`.

# Rivales F.C. — MK6: gol de oro y HUD pulido

> ## Novedades de la MK6 (cambios chicos sobre la MK5)
>
> - **Tiempo extra con gol de oro:** si el segundo tiempo termina empatado se juegan 60 s extra (`Constants.OVERTIME_SECONDS`); el primer gol gana (festejo + repetición y fin). Si nadie convierte, queda empate. Online funciona sin cambiar el protocolo (usa `half = 3`).
> - **HUD:** porcentaje de posesión a cada lado de la barra; el reloj se pone rojo y titila en los últimos 10 s; la etiqueta de fase dice `GOLDEN GOAL` en el tiempo extra y el saque muestra "TIEMPO EXTRA - GOL DE ORO"; barrita de recarga del planchazo bajo tu jugador (no se muestra en el cliente online, donde ese dato no se sincroniza); el ping online ahora tiene fondo y un punto de color.
> - **Logo del club** (`assets/logo.png`, más `icon16/32/64/128.png`): icono de la ventana (`DesktopLauncher`), cabecera del lobby (franja negra con el logo) y esquina derecha de la barra superior del partido. Si falta `logo.png` el juego arranca igual con el título de texto.
> - **Registro de goles en el HUD:** los últimos 3 goles ("AZUL 1:23") arriba a la izquierda de la barra, también en el cliente online.
> - **Elegir equipo y nombre (lobby):** filas nuevas `TU NOMBRE` (se escribe; hasta 12 caracteres, mayúsculas), `TU EQUIPO` y `EQUIPO RIVAL` (8 equipos: azul, rojo, verde, amarillo, naranja, violeta, blanco, rosa; ver `Team.java`). Se guardan entre sesiones. El equipo cambia la camiseta, el color del nombre, la estela, el arquero, el marcador, el registro de goles, los carteles de gol y la pantalla final. Contra la IA el rival se llama `CPU`; en 2 jugadores el segundo es `JUGADOR 2`. Si elegís el mismo equipo para los dos lados, el de la derecha pasa al siguiente.
> - **Online:** el HELLO/WELCOME ahora lleva (opcional) equipo + nombre de cada uno, así que cada jugador ve al rival con su equipo y nombre reales. No cambia la versión del protocolo (sin ese dato el rival se ve con valores por defecto). `NetSelfTest` pasó a 30 pruebas, todas OK por loopback.
> - **Sin compilar:** igual que la MK5, estos cambios se revisaron a mano pero no se compilaron (sin LibGDX en el entorno). Probar en partida: empatar a propósito para ver el tiempo extra.

# Rivales F.C. — MK5: online más suave, con ping y revancha

> ## Balance de la MK5 (jugabilidad)
>
> **Barrida nerfeada** (todo en `Constants.java`):
> | | Antes | Ahora |
> |---|---|---|
> | Velocidad (`SLIDE_SPEED`) | 9,5 m/s | 8,0 m/s |
> | Duración (`SLIDE_DURATION`) | 0,35 s | 0,30 s (alcance total ~3,3 m → ~2,4 m) |
> | Recarga (`SLIDE_COOLDOWN`) | 1,0 s | 1,8 s |
> | Alcance a la pelota (`SLIDE_REACH`) | 0,9 m (el de pateo) | 0,8 m |
> | Toque a la pelota (`SLIDE_KICK_POWER`) | 0,35 | 0,22 |
> | Recuperación en el piso (`SLIDE_RECOVERY_TIME`) | — | 0,35 s sin moverse ni patear |
>
> Un planchazo errado ahora te deja vulnerable, y ya no es un botón para spamear.
>
> **Tiro buffeado:**
> - Potencia máxima (`SHOT_IMPULSE_MAX`): 2,9 → 3,8 (~38 → ~50 m/s; el tope de Box2D es ~60).
> - Carga más rápida (`KICK_CHARGE_TIME`): 1,0 s → 0,75 s.
> - Alcance de pateo (`KICK_RANGE`): 0,9 → 1,05 m.
> - **Se puede apuntar**: antes el remate salía siempre en línea recta jugador → pelota. Ahora la dirección se mezcla con hacia dónde te movés al soltar el botón (`AIM_ASSIST`); si te movés hacia atrás de la pelota, se ignora.
> - Tirado en el piso (planchazo/recuperación) no se puede patear.
>
> Con tiros más fuertes los arqueros IA van a atajar menos: si queda desparejo, tocá `GK_SPEED`/`GK_DIVE_SPEED` en `Constants.java`.
>
> ## Novedades de la MK5 (sobre la MK4.5 con online 1v1)
>
> - **Interpolación de snapshots en el cliente** (`net/SnapshotInterpolator`). Antes el cliente aplicaba cada snapshot apenas llegaba: el host manda ~30/s y el cliente dibuja a 60 fps, así que todo se veía a saltos y el jitter de red lo empeoraba. Ahora se dibuja el estado de hace ~70 ms mezclando los dos snapshots que lo rodean. Los saltos grandes (saque tras un gol) no se interpolan; si el siguiente snapshot se retrasa, se extrapola hasta 100 ms con la velocidad. *Costo:* ~70 ms de retraso visual extra; se ajusta con `SnapshotInterpolator.DELAY_NANOS`.
> - **Ping en pantalla** (esquina inferior izquierda, verde/amarillo/rojo). El cliente manda un `PING` por segundo, el host lo devuelve (`PONG`) y el cliente además le informa su ping al host, así que **los dos lo ven**.
> - **Revancha online**: al terminar el partido el host presiona **R** y empieza uno nuevo sin cortar la conexión; el cliente ve "Esperando revancha del host".
> - **Protocolo v2**: se agregaron `PING`/`PONG`. **Una MK4.5 y una MK5 no se conectan entre sí** (el cliente viejo dirá "no se pudo conectar"); ambos jugadores deben tener la MK5.
> - `NetSelfTest` pasó de 17 a **26 pruebas** (ping medido, interpolación a mitad de camino, datos discretos, teletransporte, extrapolación, buffer circular).
>
> - **1 jugador a pantalla completa**: contra la IA ya no se divide la pantalla; ves toda la cancha en un solo panel (solo el modo 2 jugadores local sigue dividido). Como no hay un segundo humano al que distinguir, la IA se dibuja **nítida** (sin difuminado). El online mantiene el rival difuminado.
> - **Cámara de pantalla completa**: muestra más cancha (`FULL_VIEW_HEIGHT = 20` m, antes 16) y se inclina 30 % hacia la pelota (`FULL_VIEW_BALL_BIAS`) para no perder la jugada. Aplica a 1 jugador y online; ambas son constantes en `Constants.java`.
> - **Arreglo:** el recordatorio de controles del saque en online mostraba las teclas del Jugador 2 (flechas); online se juega con WASD, ahora muestra las correctas.
> - Textos menores: el título del marcador pasó de "Local 2v2" a "Rivales F.C." y la descripción de "1 JUGADOR" en el lobby aclara que es pantalla completa.
>
> **Estado de la verificación (importante):** la capa de red y el interpolador son Java puro y se corrieron con `NetSelfTest` por loopback: **26/26 OK**. Los cambios en `MatchScreen` (interpolador, ping, tecla R de revancha, texto del panel final, pantalla completa en 1 jugador y la cámara nueva) **no se pudieron compilar** en el entorno donde se escribieron (sin LibGDX): son pocas líneas y se revisaron a mano, pero probalas en dos ventanas antes de darlas por buenas.
>
> **Sigue pendiente:** predicción del lado del cliente (con ping alto el jugador propio responde con retraso: ping + ~70 ms), lobby en red, formaciones y chat (Etapa 3); faltas, tarjetas, offside y tiro con efecto (Etapa 4); arte y sonido definitivos (Etapa 5).
>
> **Correr las pruebas de red sin LibGDX:**
> ```
> cd core/src
> javac -encoding UTF-8 -d /tmp/out main/java/com/rivalesfc/game/net/*.java main/java/com/rivalesfc/game/input/PlayerInput.java test/java/com/rivalesfc/game/net/NetSelfTest.java
> java -cp /tmp/out com.rivalesfc.game.net.NetSelfTest
> ```

# Rivales F.C. — Etapa 2: 1v1 online (UDP, host + cliente)

**Cómo jugar online (misma red local, o con el puerto UDP 7777 abierto/redirigido en el router del host):**
1. Jugador A (host): en el lobby elige **ONLINE: CREAR PARTIDA** y presiona ENTER. Ve su IP en pantalla y queda esperando. Es el equipo AZUL.
2. Jugador B (cliente): elige **ONLINE: UNIRSE**, escribe la IP del host (opcionalmente `ip:puerto`) y presiona ENTER. Es el equipo ROJO.
3. Ambos juegan con **WASD + Shift (sprint) + Espacio (cargar y patear) + Ctrl izq. (planchazo)**. Cada uno ve su propio panel a pantalla completa.

**Cómo funciona:** el host corre toda la simulación (`MatchSimulation`, igual que en local) y usa el input del cliente para el jugador rojo. Manda un `Snapshot` ~30 veces por segundo; el cliente lo aplica (`applySnapshot`) y dibuja. El cliente **no** hace predicción: con latencia alta verá su movimiento con retraso.

**Online no hay** pausa (P); el reinicio (R) solo lo tiene el host, y únicamente al terminar el partido (revancha). ESC vuelve al menú y cierra la conexión. Si el rival se cae, el host queda esperando y puede volver a entrar.

**Estado de la verificación:** la capa de red (`net/`) tiene prueba automática por loopback (`NetSelfTest`, 26/26 en la MK5). La integración en `MatchScreen`/`MatchSimulation`/`LobbyScreen` **no se compiló ni se corrió** en el entorno donde se escribió (sin LibGDX): probarla en dos ventanas o dos máquinas antes de darla por buena.

# Rivales F.C. — Núcleo local, pantalla dividida, 2v2 fijo (Etapa 1 — MK4)

> ## Novedades de la MK4
>
> **Arreglos de errores heredados de la MK3** (afectaban al "feel" sin que se notara la causa):
> - **La potencia de pateo siempre era 0.** `PlayerEntity.applyInput` ponía la carga en 0 en el mismo tick del "soltar", justo antes de que `handleKick` la leyera; todos los pateos salían como pase mínimo. Ahora la carga se consume al patear.
> - **Se perdían pateos y planchazos.** El juego renderiza a 60 fps pero simula a 30 Hz; `pollInput` pisaba `kickReleased`/`slidePressed` en cada frame, así que si el evento caía en un frame sin tick, se perdía (~50 %). Ahora son "pegajosos" hasta que la simulación los usa.
> - **Escala de la pelota.** Con `PASS_IMPULSE_MIN=3` y `SHOT_IMPULSE_MAX=16` sobre una pelota de ~0,076 kg, todo remate superaba el tope de velocidad de Box2D (~60 m/s): cargar más no cambiaba nada. Se reajustó a 1,1 / 2,9 (≈14 a ≈38 m/s), así la potencia realmente importa. *Son dos constantes en `Constants.java`, fáciles de tocar si prefieren otro ritmo.*
> - `build.gradle` fuerza UTF-8 al compilar (el código tiene tildes y ñ).
>
> **Lo que la MK3 dejó fuera de alcance, ahora hecho:**
> - **Estirada del arquero** (`GoalkeeperEntity`): estados TRACK / DIVE. Anticipa la trayectoria de un remate al arco, se tira tras un reflejo corto con error según la dificultad, tiene recarga y se anima (salto + estiramiento, sonido).
> - **IA de soporte con pases y posicionamiento** (`SupportAI`): DEFEND / CHASE / CARRY, remata al rincón más lejano del arquero, hace toques a espacio libre (aclaración: en 2v2 fijo no hay compañero de campo, así que el "pase" es a espacio, no a un jugador), se acomoda detrás de la pelota para apuntar y defiende del lado del arco propio.
> - **Pantalla de mejor jugador** (`MatchStats` + `drawFullTimePanel`): resultado, posesión, remates, remates al arco, recuperaciones, atajadas, notas 1–10 de los cuatro jugadores y MVP.
> - **Desenfoque real por shader** (`gfx/BlurShader`): gaussiano 5x5 en GLSL aplicado al sprite del rival, sin FrameBuffer. Si el driver no compila el shader, se usa el desenfoque por capas de la MK3 como respaldo.
>
> **Novedades nuevas (rumbo a la versión final):**
> - **Repetición de gol** en cámara lenta (0,6x, 3,5 s de jugada), con barras "cine", cartel REPLAY y cámara siguiendo la pelota. Se saltea con TAB. Es la funcionalidad de la sección 8.4 de la propuesta.
> - **Atajadas** contadas, con cartel `¡ATAJADA!`, sonido y shake.
> - **Opciones en el lobby**: dificultad (fácil/normal/difícil), duración del tiempo (1/2/3/5 min) y volumen, guardadas entre sesiones (`Settings`, con `Preferences` de libGDX).
> - **Confeti** y ovación del público al hacer un gol; el cartel de gol nombra al goleador.
> - Sonidos nuevos sintetizados: ovación, "fuuum" de la estirada, atajada. Tecla **M** para silenciar.
> - Menú de pausa con las opciones disponibles.
>
> **Sigue fuera de alcance** (etapas 2 en adelante): red, lobby en red, formaciones, faltas/tarjetas/offside, tiro con efecto completo, pases a compañeros reales (requieren más de un jugador de campo por equipo).

> **Novedades de la MK3** (sobre la MK2), pensadas puntualmente para que el
> juego "se sienta" más profesional sin tocar el alcance de red/lobby de las
> etapas siguientes:
>
> - **Audio sintetizado en runtime** (`audio/AudioFactory`): sin descargar ni
>   empaquetar ningún archivo de sonido (mismo criterio que los sprites
>   pixel-art: este entorno no tiene internet), se generan ondas
>   cuadradas/triangulares a mano y se escriben como WAV temporal para poder
>   reproducirlas con `Sound` de libGDX. Hay pitido de pateo (grave si es
>   pase, agudo si es remate fuerte), golpe seco de planchazo, silbato de
>   saque/entretiempo, fanfarria de gol y bocinazo de final de partido.
> - **Screen shake**: la cámara de cada panel tiembla brevemente en remates
>   fuertes, planchazos que ganan la pelota y, sobre todo, en los goles.
> - **Animación de carrera sin sprites extra**: los personajes ahora suben y
>   bajan levemente (bobbing) con un "squash" sutil al correr, con cadencia
>   proporcional a su velocidad, en vez de deslizarse como un sprite rígido.
> - **Indicador de posesión**: una barrita azul/roja bajo el marcador durante
>   el partido, y el porcentaje de posesión de cada equipo en la pantalla de
>   fin de partido.
> - **Recordatorio de controles en pantalla**: durante el saque inicial del
>   partido (0-0, primer tiempo) se muestra un cartel breve con las teclas de
>   cada jugador, para no depender de que hayan leído el README antes de
>   jugar.


> **Novedades de esta versión** (mejora visual y de "feel" de partido sobre
> la Etapa 1 original, sin tocar el alcance de red/lobby de las etapas
> siguientes), inspiradas en una referencia visual estilo arcade pixel-art:
>
> - **Barra superior compartida** (no una por panel): reloj con manecilla,
>   marcador `BLUE X - Y RED` con los colores de cada equipo, título
>   `FIRST HALF` / `SECOND HALF` / `HALFTIME` / `FULL TIME` y el nombre del
>   partido, todo generado con `ShapeRenderer`/`BitmapFont` (sin PNGs).
> - **Sprites de jugadores pixel-art generados en tiempo de ejecución**
>   (`gfx/PixelArtFactory`): cuerpo, camiseta, short, medias y botines por
>   equipo, con guantes amarillos distintivos en el arquero. No se agregó
>   ningún archivo de imagen: todo se dibuja sobre `Pixmap` al arrancar el
>   juego, porque este entorno de trabajo no tiene acceso a internet para
>   bajar assets.
> - **Nombres sobre cada jugador** (`ALEJO`, `MARTIN`, `TOBIAS`, `JERIEL`,
>   configurables en `Constants`), con sombra para que se lean sobre el
>   pasto.
> - **Rastro de movimiento** (`gfx/MotionTrail`) que aparece detrás de un
>   jugador cuando corre por encima de cierta velocidad, con el color de su
>   equipo — el mismo efecto de "estela" de la referencia visual.
> - **Cancha más completa**: pasto a rayas, áreas grande y chica, marca de
>   penal, arcos de esquina y redes dibujadas con grilla, más una tribuna
>   decorativa (público en pixel-art) en los bordes visibles de la cancha.
> - **Fases de partido** en `MatchSimulation` (`Phase`): cuenta regresiva de
>   3 segundos antes de cada saque (inicial y tras cada gol), cartel de gol
>   con la pelota congelada, entretiempo de verdad entre el primer y el
>   segundo tiempo (3 minutos cada uno, configurable), y pantalla de fin de
>   partido con el resultado.
> - **Pausa (`P`) y reinicio (`R`)** del partido completo en cualquier
>   momento.
>
> Todo esto vive en el mismo núcleo de física a tick fijo (30 Hz) que ya
> estaba pensado para reutilizarse cuando la simulación pase a correr del
> lado del host en la Etapa 2: no se tocó `Ball`, `Field`, `PlayerEntity` ni
> `GoalkeeperEntity` a nivel de física, solo se sumó estado de presentación
> (`Phase`, `MotionTrail`, texturas) alrededor de ellas.


Implementación de la **Etapa 1** del cronograma (semanas 3–4, sección 2.5):
todavía **sin red**. Se juega en una sola máquina, en pantalla dividida, con
el formato fijo **2 vs 2**: un jugador humano local por equipo más un
arquero 100% IA por equipo. El lobby, las formaciones, la red y la IA de
soporte para más jugadores por equipo llegan en las etapas 2 y 3.

## Qué incluye

- **Pantalla dividida** (split screen): dos cámaras independientes, una por
  cada jugador humano, cada una siguiendo a su propio personaje dentro de
  los límites de la cancha.
- **El rival se ve difuminado**: dentro del panel de cada jugador, su propio
  personaje se dibuja nítido y con contorno blanco; el jugador humano rival
  se dibuja con un desenfoque aproximado (varias capas translúcidas de radio
  creciente), para que cada uno identifique de un vistazo cuál es "el suyo".
  *Nota técnica*: es un desenfoque "barato" hecho con `ShapeRenderer` (sin
  `FrameBuffer` ni shader de blur real), suficiente para el propósito de
  distinguir personajes sin agregar la complejidad de un pipeline de
  post-procesado en esta etapa.
- **2v2 fijo**: exactamente un jugador de campo humano y un arquero IA por
  equipo. No hay otras configuraciones (1v1, más jugadores, etc.) en este
  modo.
- **Arquero 100% IA** (`GoalkeeperEntity`): body cinemático de Box2D que
  **solo se mueve en el eje vertical**, persiguiendo la coordenada Y de la
  pelota sin salir de la boca de su propio arco. Nunca recibe input humano
  ni es empujado por choques.
- Cancha reglamentaria simplificada con bordes, arcos y sensores de gol
  (`Field`).
- Pelota con física Box2D (rebote, fricción, impulso al patear) (`Ball`).
- Pateo con potencia variable: mantener y soltar el botón de pateo carga y
  ejecuta el remate (secc. 1.2).
- Detección de gol con reinicio automático de posiciones (jugadores y
  arqueros vuelven a su lugar de saque).
- **Simulación a tick fijo de 30 Hz** desacoplada del framerate de render
  (60 fps), tal como describe la sección 2.3 de la propuesta, para que el
  mismo bucle de `MatchSimulation` se reutilice en la Etapa 2 cuando pase a
  correr del lado del host.
- `PlayerInput` ya tiene la forma del futuro mensaje UDP `INPUT_STATE`
  (número de secuencia, ejes, acciones) descripto en la sección 2.4.

## Qué falta a propósito (etapas siguientes)

- Red (host/cliente, TCP/UDP, predicción y reconciliación) → Etapa 2.
- Reemplazar al segundo jugador local por un cliente conectado en red;
  lobby, formaciones, chat → Etapa 3.
- Faltas, tarjetas, offside, tiro con efecto completo, replay → Etapa 4.
- Arte, animaciones, sonido, interfaz definitiva, desenfoque real por
  shader (si se lo quiere pulir visualmente) → Etapa 5.

## Estructura del proyecto

```
rivalesfc/
├── build.gradle              # build raíz (equivalente al de gdx-setup)
├── settings.gradle
├── assets/                   # vacío por ahora (Etapa 1 no usa sprites)
├── core/
│   ├── build.gradle
│   └── src/main/java/com/rivalesfc/game/
│       ├── Constants.java
│       ├── RivalesFCGame.java
│       ├── entities/
│       │   ├── Field.java
│       │   ├── Ball.java
│       │   ├── PlayerEntity.java
│       │   └── GoalkeeperEntity.java   # arquero 100% IA
│       ├── input/
│       │   └── PlayerInput.java
│       ├── gfx/
│       │   ├── PixelArtFactory.java    # texturas pixel-art generadas en runtime
│       │   └── MotionTrail.java        # rastro de movimiento a alta velocidad
│       ├── sim/
│       │   └── MatchSimulation.java    # 2v2 fijo + fases de partido (Phase)
│       └── screens/
│           └── MatchScreen.java        # pantalla dividida + HUD compartido + rival difuminado
└── desktop/
    ├── build.gradle
    └── src/main/java/com/rivalesfc/game/desktop/
        └── DesktopLauncher.java
```

## Cómo levantarlo (importante)

Este entorno de trabajo no tiene acceso a internet, así que no pude
descargar LibGDX/Box2D ni compilar o probar el proyecto acá. El código está
escrito para LibGDX **1.12.x** (la versión que menciona la propuesta) y
sigue exactamente la estructura que genera el asistente oficial de setup.
Para correrlo:

1. Generar un proyecto base con el
   [LibGDX Project Generator](https://libgdx.com/wiki/start/project-generation)
   (o `gdx-liftoff`), eligiendo:
   - Nombre del paquete: `com.rivalesfc.game`
   - Módulos: **Core** y **Desktop**
   - Extensión: **Box2D**
   - Versión de LibGDX: 1.12.1 (o la última 1.12.x disponible)
2. Reemplazar/copiar dentro del proyecto generado los archivos `.java` de
   este ZIP en las mismas rutas de paquete (`core/src/.../com/rivalesfc/game/...`
   y `desktop/src/.../com/rivalesfc/game/desktop/...`).
3. Importar el proyecto en Eclipse como *Existing Gradle Project* (tal como
   pide la cátedra) y ejecutar `DesktopLauncher`, o desde la terminal:
   ```
   ./gradlew desktop:run
   ```

## Controles

**Jugador 1 — equipo AZUL — panel izquierdo**

| Acción              | Tecla              |
|---------------------|---------------------|
| Mover               | W A S D             |
| Sprint              | Shift izquierdo     |
| Cargar y patear     | Mantener y soltar Espacio |

**Jugador 2 — equipo ROJO — panel derecho**

| Acción              | Tecla              |
|---------------------|---------------------|
| Mover               | Flechas             |
| Sprint              | Ctrl derecho        |
| Cargar y patear     | Mantener y soltar Enter |

**General**

| Acción                       | Tecla |
|------------------------------|-------|
| Ver bodies de Box2D (debug)  | F1    |
| Pausar / reanudar            | P     |
| Reiniciar el partido         | R     |
| Saltear la repetición de gol | TAB   |
| Silenciar / activar sonido   | M     |
| Menú principal               | ESC   |

Los arqueros no se controlan: se mueven solos, solo en vertical, siguiendo
a la pelota.

## Notas de diseño pensando en la Etapa 2

- `MatchSimulation.step(delta, inputLeft, inputRight)` separa deliberadamente
  "cuánto tiempo real pasó" de "cuántos ticks de simulación corrieron", con
  un acumulador. Cuando el host reciba `INPUT_STATE` por UDP de cada
  cliente, alcanza con pasarle esos inputs a `fixedTick` en lugar de los del
  teclado local, sin tocar la física.
- El segundo jugador local (`playerRight`) es el candidato natural a
  reemplazarse primero por un cliente remoto en la Etapa 2 (partido 1 vs 1
  en red), dejando `playerLeft` como referencia de lo que hoy corre en la
  misma máquina.
- `GoalkeeperEntity` queda aislado de la lógica de red: al ser 100% IA y
  calcularse siempre en el host, ningún cliente necesita enviar su input.
- Todas las magnitudes físicas están en metros/segundo (convención de
  Box2D), con `Constants.PPM` para convertir a píxeles al momento de
  dibujar con sprites (cuando se sume arte en la Etapa 5).
