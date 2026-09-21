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
    TWO_PLAYERS
}
