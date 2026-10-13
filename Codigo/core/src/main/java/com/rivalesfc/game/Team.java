package com.rivalesfc.game;

import com.badlogic.gdx.graphics.Color;

/** MK6: equipos entre los que se puede elegir en el lobby. El índice en {@link #ALL} es lo que se guarda y se manda por red. */
public final class Team {

    public final String name;
    public final Color color;
    /** Color del short (el arquero usa este color como camiseta secundaria / guantes según PixelArtFactory). */
    public final Color shorts;

    private Team(String name, Color color, Color shorts) {
        this.name = name;
        this.color = color;
        this.shorts = shorts;
    }

    public static final Team[] ALL = {
        new Team("AZUL",     new Color(0.20f, 0.45f, 0.95f, 1f), Color.WHITE),
        new Team("ROJO",     new Color(0.90f, 0.25f, 0.25f, 1f), Color.WHITE),
        new Team("VERDE",    new Color(0.18f, 0.72f, 0.34f, 1f), Color.WHITE),
        new Team("AMARILLO", new Color(0.97f, 0.82f, 0.15f, 1f), new Color(0.15f, 0.15f, 0.2f, 1f)),
        new Team("NARANJA",  new Color(0.97f, 0.55f, 0.12f, 1f), Color.WHITE),
        new Team("VIOLETA",  new Color(0.62f, 0.36f, 0.88f, 1f), Color.WHITE),
        new Team("BLANCO",   new Color(0.92f, 0.93f, 0.96f, 1f), new Color(0.15f, 0.15f, 0.2f, 1f)),
        new Team("ROSA",     new Color(0.96f, 0.46f, 0.70f, 1f), Color.WHITE),
    };

    /** Equipo por índice; si el índice es inválido (dato corrupto o de red) devuelve el primero. */
    public static Team get(int index) {
        return index >= 0 && index < ALL.length ? ALL[index] : ALL[0];
    }

    /** Si los dos índices coinciden, el de la derecha pasa al siguiente (los dos lados aplican la misma regla). */
    public static int distinctRight(int leftIndex, int rightIndex) {
        int r = Math.floorMod(rightIndex, ALL.length);
        return r == Math.floorMod(leftIndex, ALL.length) ? (r + 1) % ALL.length : r;
    }
}
