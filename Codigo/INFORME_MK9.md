# Rivales FC — MK9 (IA y visuales)

## IA del jugador de campo (`ai/SupportAI.java`, reescrita)
Problema de la MK8: dos IA iguales hacían ~1 remate por partido de 2 min. Sus "toques suaves" salían a ~27 m/s,
la pelota se iba lejos y quedaba suelta ~36 % del tiempo.

- Percepción con retraso según dificultad (FÁCIL 9 ticks, NORMAL 5, DIFÍCIL 0) y desvío lento al azar (evita bucles idénticos).
- Predicción de la pelota con el frenado real de la física; va al punto de intercepción.
- Conducción empujando la pelota hacia el arco, con esquive del rival.
- Tiro: elige rincón según arquero, línea libre y comodidad; mayor alcance (x1,25).
- Pelota pegada a la banda: tiro con rebote (compensa la restitución) y rodeo de la pelota.
- Pases/despejes con potencia calculada (antes ~3x excesiva). Toque al espacio si la pelota está disputada.
- 2v2: pase al compañero si está mejor ubicado.
- Defensa: se coloca del lado del arco propio; barre solo si llega a la pelota antes que al rival.
- Menos faltas: `Constants.AI_RISKY_SLIDE_CHANCE` 0,010 -> 0,0015 y barrida limpia 0,35 -> 0,25 (0,05 en su área).
  Medido: de 1,1 a 0,5 faltas y de 0,27 a 0,07 rojas por partido, y la IA nueva sigue ganándole a la MK8 (38-15).

## Arquero (`GoalkeeperEntity`)
Se adelanta (0,22 s) a la trayectoria de remates que vienen al arco.

## Visuales
- `ArtPainter.person(jersey, shorts, keeper, variant)`: 4 variantes (piel, pelo y diseño de camiseta: lisa, rayas, franjas, banda).
  La versión de 3 argumentos deduce la variante de los colores (cada equipo tiene su aspecto).
- `gfx/Particles`: polvo/chispas al patear (más al patear fuerte), nube en planchazos, destellos en atajadas.
  Conectado en `MatchScreen` (no se pudo ver en pantalla: sin GPU en el entorno; compila).

- `gfx/NetRipple`: la red del arco se sacude con una onda al entrar un gol (~1,6 s). Conectada en `MatchScreen`
  (no visto en pantalla; la cancha es una textura fija, así que se dibuja encima).

## Pruebas
Nuevas: `ai/AiSelfTest`, `gfx/ParticlesSelfTest`, `gfx/NetRippleSelfTest`. Herramientas de medición: `ai/AiBench` (IA nueva vs MK8 o vs sí misma),
`AiTrace`, `AiGoals`, `LegacyAI` (copia de la IA MK8), `gfx/ArtPreview` (vuelca el arte a PNG).
Todas las pruebas anteriores siguen pasando (corridas con Box2D en Java puro, no nativo).

Resultados (IA vs IA, 2 min): NORMAL le gana a FÁCIL 87-13; DIFÍCIL a NORMAL 64-31; ~4 a 11 remates por partido.
Límites: no probado con una persona; la estadística "al arco" no cuenta tiros con rebote; FÁCIL podría resultar demasiado fácil.
