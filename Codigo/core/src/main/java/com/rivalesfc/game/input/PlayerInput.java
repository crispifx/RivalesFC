package com.rivalesfc.game.input;

/**
 * Representa la intención de un jugador en un instante dado.
 *
 * En la Etapa 1 (núcleo local, sin red) esta clase se llena leyendo el teclado
 * directamente en {@code MatchScreen}. Se la diseña ya con la forma que va a
 * tener el mensaje UDP "INPUT_STATE" de la sección 2.4 de la propuesta
 * (número de secuencia + ejes + acciones), para que en la Etapa 2 (red 1 vs 1)
 * alcance con serializar esta misma clase en vez de rediseñar el modelo de entrada.
 */
public class PlayerInput {

    /** Número de secuencia creciente (útil más adelante para reconciliación cliente-servidor). */
    public int sequence;

    /** Eje horizontal deseado, en rango [-1, 1]. */
    public float moveX;

    /** Eje vertical deseado, en rango [-1, 1]. */
    public float moveY;

    /** Sprint sostenido. */
    public boolean sprint;

    /** Botón de patear/cargar tiro sostenido este frame. */
    public boolean kickHeld;

    /** Se soltó el botón de patear este frame (dispara el remate con la potencia acumulada). */
    public boolean kickReleased;

    /** Cambiar de jugador controlado dentro del equipo (útil en 1 vs 1 / con IA de soporte). */
    public boolean switchPlayer;

    /** Se presionó el botón de planchazo/barrida este frame (acción de un solo disparo, no sostenida). */
    public boolean slidePressed;

    public PlayerInput() {
    }

    public void clearTransient() {
        kickReleased = false;
        switchPlayer = false;
        slidePressed = false;
    }

    /** Normaliza el vector de movimiento para que correr en diagonal no sea más rápido. */
    public void normalizeMove() {
        float len = (float) Math.sqrt(moveX * moveX + moveY * moveY);
        if (len > 1f) {
            moveX /= len;
            moveY /= len;
        }
    }
}
