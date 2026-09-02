# Rivales F.C. — Núcleo local, pantalla dividida, 2v2 fijo (Etapa 1 — versión mejorada)

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
