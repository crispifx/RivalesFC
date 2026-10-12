# Informe de Evolución del Proyecto: Rivales FC — MK5

## MK5 — Online pulido (sobre MK4 + Etapa 2)

### 1. Problemas del online de la MK4.5 que se atacaron
- **Movimiento a saltos en el cliente:** cada snapshot (30/s) se aplicaba crudo mientras se dibuja a 60 fps; el jitter de red se notaba directamente.
- **Sin información de conexión:** ningún jugador sabía su ping.
- **Sin forma de jugar otra:** al terminar el partido online había que salir al menú y reconectar.

### 2. Cambios
1. `net/SnapshotInterpolator` (nuevo, Java puro): buffer circular de 8 snapshots con hora de llegada; se dibuja con 70 ms de retraso mezclando linealmente; saltos > 4 m no se interpolan; datos discretos del snapshot más cercano; extrapolación máxima de 100 ms.
2. Protocolo v2: `PING`/`PONG`; el cliente mide el ping (suavizado 70/30) y se lo informa al host. Ambos lo ven en pantalla.
3. Revancha: el host reinicia con R en `FULL_TIME`; el cliente espera y recibe el partido nuevo por snapshots.
4. `NetSelfTest`: 17 → 26 pruebas.
5. Modo 1 jugador a pantalla completa (IA nítida), cámara con más campo y sesgo hacia la pelota, corrección del recordatorio de controles online, textos menores.

6. Balance: barrida nerfeada (más lenta, más corta, recarga 1,8 s, 0,35 s de recuperación, menor alcance y toque) y tiro buffeado (+31 % de potencia máxima, carga más rápida, más alcance, dirección apuntable).

### 3. Verificación
- Capa de red + interpolador: 26/26 por loopback.
- `MatchScreen`: modificado pero **sin compilar** (el entorno no tiene LibGDX). Probar en dos ventanas.

### 4. Resumen de evolución
| Versión | Objetivo | Principales incorporaciones |
|---|---|---|
| MK1 | Prototipo | Física, movimiento, disparos, arquero y split-screen |
| MK2 | Jugabilidad y estabilidad | Rediseño gráfico, corrección de física, barrida, lobby e IA |
| MK3 | Experiencia y feedback | Audio, screen shake, animaciones, posesión y tutorial |
| MK4 | Cierre de pendientes y pulido | Estirada, IA con decisiones, replay, estadísticas y MVP, shader de blur, opciones |
| MK4.5 | Etapa 2 | Online 1v1 por UDP (host + cliente) |
| MK5 | Online pulido | Interpolación en el cliente, ping, revancha, protocolo v2 |

### 5. Fuera de alcance
Predicción del cliente, lobby en red, formaciones y chat (Etapa 3); faltas, tarjetas, offside y tiro con efecto (Etapa 4); arte y sonido definitivos (Etapa 5).
