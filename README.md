Aquí tienes el archivo `README.md` modificado. He actualizado la sección **"Guía de Instalación, Compilación y Ejecución"** para que el método principal y recomendado sea a través de **IntelliJ IDEA**, manteniendo las instrucciones para generar el `.JAR` final si lo necesitan.

```markdown
# Rivales F.C. ⚽🎮

> **Proyecto Final de Laboratorio y Programación — 6° Año (División 6°2°)**  
> **Escuela Técnica N° 35 D.E. 18 "Ing. Eduardo Latzina"**  
> *Especialidad: Computación*

---

## 📌 Descripción General

**Rivales F.C.** es un videojuego multijugador de fútbol 2D en tiempo real diseñado para partidos en red local (LAN) o internet de hasta **4 jugadores humanos simultáneos (2 vs 2)**. 

Cada usuario controla a un futbolista dentro del campo de juego, mientras que el resto del plantel es comandado en tiempo real por un sistema de **Inteligencia Artificial de apoyo autoritativa basada en Máquinas de Estados Finitos (FSM)**.

El propósito principal del proyecto es resolver la problemática técnica de la **sincronización de red sin servidor externo ni base de datos**, aplicando técnicas avanzadas de *netcode* sobre **Java** y el framework **LibGDX**.

---

## 🎯 Desafíos Técnicos y Soluciones de Netcode

Para garantizar un juego fluido y justo en conexiones reales con latencia (20 ms a 150 ms) y pérdida de paquetes, el motor implementa las siguientes técnicas:

### 1. Arquitectura Host-Autoritativo
- Uno de los jugadores actúa simultáneamente como servidor (*Host*) y cliente.
- El *Host* ejecuta el **bucle de simulación física (a 30 Hz)**, la detección de colisiones y las reglas reglamentarias. Los clientes nunca deciden el estado final de la pelota o las disputas.

### 2. Protocolo Híbrido TCP/UDP
- **TCP (`Socket` / `ServerSocket`):** Canal de datos garantizado para acciones críticas y ordenadas. Se utiliza en el **Lobby de espera, Chat, inicio/fin del partido, goles, tarjetas, faltas y desconexiones**.
- **UDP (`DatagramSocket`):** Canal de alta frecuencia para datos volátiles de baja latencia. Envía **comandos de entrada del cliente (~30 Hz)** y **snapshots autoritativos del estado global (~20 Hz)** en paquetes binarios compactos (< 512 bytes).

### 3. Técnicas de Compensación de Latencia
- **Predicción del lado del Cliente:** El cliente aplica inmediatamente los comandos de movimiento en su pantalla local sin esperar el viaje ida y vuelta (*RTT*) al servidor, logrando respuesta instantánea.
- **Reconciliación y Corrección:** Si la posición predicha diverge del estado devuelto por el *Host*, el cliente corrige suavemente la posición y vuelve a simular los comandos pendientes de confirmación.
- **Interpolación de Entidades Remotas:** Los futbolistas manejados por otros jugadores se renderizan con un pequeño retraso de interpolación (~100 ms), eliminando tirones visuales (*jitter*).

---

## 🧠 Inteligencia Artificial de Apoyo (FSM)

Los futbolistas no controlados por usuarios son gestionados por la computadora en la simulación central del *Host*. La IA utiliza una **máquina de estados finitos** con 4 comportamientos dinámicos:

| Estado | Condición de Activación | Comportamiento |
| :--- | :--- | :--- |
| **Marcar** | El equipo rival tiene el balón y hay una marca asignada. | Se posiciona entre el atacante y el arco propio a distancia de intercepción. |
| **Cubrir espacio** | El equipo rival tiene el balón sin rival directo cercano. | Regresa a su zona según la formación táctica elegida. |
| **Desmarcarse** | El equipo propio tiene la pelota y el bot está distante. | Busca una posición libre en el campo visual del poseedor del balón. |
| **Ofrecer pase** | El equipo propio tiene la pelota y el bot está cerca. | Se frena en un carril abierto pidiendo el pase corto. |

---

## 📐 Estructura de la Arquitectura de Software

El proyecto sigue una clara separación de capas para facilitar el mantenimiento y las pruebas:

```text
com.rivalesfc
├── core
│   ├── ai          # Máquinas de estado para la IA de apoyo
│   ├── entities    # Entidades (Jugador, Pelota, Cancha, Árbitro)
│   ├── input       # Mapeo de controles y teclado
│   ├── logic       # Física LibGDX/Box2D y reglas de partido
│   └── physics     # Resolución determinística de colisiones
├── net
│   ├── packets     # Definición de estructuras binarias TCP/UDP
│   ├── client      # Cliente TCP/UDP (Predicción e Interpolación)
│   └── server      # Host autoritativo (Tickrate y Snapshots)
└── ui
    ├── screens     # Menú, Lobby, Partido y Pantalla de Resultados
    └── hud         # Marcador, tiempo, barras de potencia e indicadores

```

---

## 🎮 Reglas y Funcionalidades del Juego

* **Modalidad:** 2v2 o 1v1 (con adaptación automática ante desconexiones parciales).
* **Control y Mecánicas:** Pases cortos/largos, remate cargado con barra de potencia, tiros con efecto, barridas tácticas y atajada de arquero.
* **Reglamento:** Faltas, tarjetas amarillas/rojas (expulsión), offside automático y saques reglamentarios (córner, lateral, tiro libre).
* **Repetición Instantánea:** Replay automático de 3 a 5 segundos generado localmente tras cada gol.
* **Lobby y Chat:** Creación de salas personalizadas, selección de formaciones, colores de camiseta y chat de texto previo al partido.

---

## 🛠️ Tecnologías e Herramientas

* **Lenguaje Principal:** Java (JDK 8 o superior / JDK 17 recomendado)
* **Framework Gráfico y Físico:** [LibGDX 1.12.x](https://libgdx.com/?utm_source=gemini) (Scene2D + Box2D)
* **Networking:** Java Native Net API (`java.net.*`) con `DataInputStream`/`DataOutputStream` para empaquetado binario ligero.
* **Gestión de Proyecto:** Gradle / Git / GitHub.
* **Simulación de Mala Red:** Clumsy (Windows) / `tc` / `netem` (Linux) para pruebas de estrés a 200ms de ping y 5% de packet loss.

---

## ⚙️ Guía de Instalación, Compilación y Ejecución

La forma recomendada y más sencilla de compilar y ejecutar este proyecto de LibGDX es utilizando el entorno de desarrollo **IntelliJ IDEA**.

### 1. Requisitos Previos

1. **Java Development Kit (JDK 17 recomendado)**. Puedes descargarlo desde [Adoptium / Temurin](https://adoptium.net/?utm_source=gemini).
2. **IntelliJ IDEA Community Edition** (gratuito) o Ultimate. Puedes descargarlo desde la [página oficial de JetBrains](https://www.jetbrains.com/idea/download/?utm_source=gemini).
3. **Git** para clonar el repositorio.

---

### 2. Clonar el Repositorio

Abre tu terminal y clona el proyecto localmente:

```bash
git clone [https://github.com/tu-usuario/rivales-fc.git](https://github.com/tu-usuario/rivales-fc.git)

```

---

### 3. Ejecutar el Juego en IntelliJ IDEA

1. **Abrir el proyecto:**
* Inicia IntelliJ IDEA y selecciona la opción **Open**.
* Navega hasta la carpeta clonada y selecciona el directorio `rivalesfc` (donde se encuentra el archivo `build.gradle`).
* Haz clic en **OK** para abrirlo.


2. **Sincronización de dependencias:**
* Al abrir la carpeta, IntelliJ detectará automáticamente que es un proyecto gestionado con **Gradle**.
* Espera unos minutos mientras el IDE descarga las dependencias de LibGDX y configura el entorno (verás una barra de carga en la parte inferior derecha).


3. **Lanzar el juego:**
* En el explorador de archivos lateral (Project), navega por la siguiente ruta:
`desktop / src / main / java / com / rivalesfc / game / desktop /`
* Haz clic derecho sobre el archivo **`DesktopLauncher.java`**.
* Selecciona la opción **Run 'DesktopLauncher.main()'**.
* ¡Listo! El proyecto se compilará y se abrirá la ventana del juego automáticamente.



---

### 4. Generar el Ejecutable `.JAR` (Opcional)

Si deseas empaquetar todo el proyecto en un ejecutable comprimido listo para distribución, puedes utilizar el Gradle Wrapper (`gradlew`) incluido en la raíz del proyecto. Abre la terminal de IntelliJ o tu consola de comandos en la carpeta `rivalesfc` y ejecuta:

* **En Windows (PowerShell/CMD):**
```powershell
.\gradlew.bat desktop:dist

```


* **En Linux / macOS:**
```bash
./gradlew desktop:dist

```



El ejecutable se generará en la ruta:
`desktop/build/libs/desktop-1.0.jar` (o un nombre similar).

Para abrir el `.jar` en cualquier máquina con Java instalado:

```bash
java -jar desktop/build/libs/desktop-1.0.jar

```

---

## 🎮 Cómo Jugar en Red (LAN)

1. **Crear una Sala (Host):**

* Un jugador selecciona **Crear Sala** e indica el puerto (por defecto `54555`).
* El Host debe compartir su dirección IP de red local a los demás jugadores.

2. **Unirse a la Sala (Clientes):**

* Los demás jugadores seleccionan **Unirse**, ingresan la dirección IP del Host y confirman.

3. **Inicio del Partido:**

* Una vez listos en el Lobby, el Host inicia el partido.

---

## 👥 Integrantes del Equipo

* **Alejo Angulo**
* **Martín Belay**
* **Tobías Miranda**
* **Jeriel Estrada**
* **Santino Crespo**

---

## ⚖️ Licencia y Créditos

Proyecto desarrollado para la **Escuela Técnica N° 35 D.E. 18 "Ing. Eduardo Latzina"** (Buenos Aires, Argentina) como trabajo final integrador.

```

```
