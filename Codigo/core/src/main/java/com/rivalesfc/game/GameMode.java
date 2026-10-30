package com.rivalesfc.game;

/**
 * Modo de juego elegido en el lobby, antes de entrar a {@code MatchScreen}.
 *
 * - {@link #ONE_PLAYER}: el Jugador 1 (panel izquierdo, WASD) es humano; el
 *   "Jugador 2" (equipo ROJO) lo controla una IA simple de soporte
 *   ({@code com.rivalesfc.game.ai.SupportAI}), igual que ya pasa con ambos
 *   arqueros. La pantalla sigue dividida en dos paneles para no duplicar
 *   lógica de cámara/HUD; el panel derecho muestra lo que "vería" la IA.
 * - {@link #TWO_PLAYERS}: comportamiento original — dos humanos locales,
 *   cada uno en su mitad de pantalla (Jugador 1 con WASD, Jugador 2 con flechas).
 */
public enum GameMode {
    ONE_PLAYER,
    TWO_PLAYERS,
    /**
     * MK7: 2 vs 2 local cooperativo. Dos humanos compañeros de equipo en el mismo dispositivo (AZUL: J1 con WASD y
     * J2 con flechas) contra un equipo ROJO de dos jugadores de IA. Una sola cámara compartida a pantalla completa.
     * Es solo local: no tiene versión online.
     */
    TWO_VS_TWO,
    /** Online 1v1, lado HOST: corre la simulación; es el equipo AZUL (izquierda). El rival llega por red. */
    ONLINE_HOST,
    /** Online 1v1, lado CLIENTE: no simula; manda su input al host y dibuja lo que recibe. Es el equipo ROJO. */
    ONLINE_CLIENT;

    public boolean isOnline() {
        return this == ONLINE_HOST || this == ONLINE_CLIENT;
    }
}
