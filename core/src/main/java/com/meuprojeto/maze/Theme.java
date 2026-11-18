package com.meuprojeto.maze;

import com.badlogic.gdx.graphics.Color;

/** Aparência do labirinto (cores do chão, paredes, destaque etc.). */
public class Theme {
    public final String name;
    public final Color floor, wall, flash, start, hudBg;

    private Theme(String name, int floor, int wall, int flash, int start, int hudBg) {
        this.name = name;
        this.floor = rgb(floor);
        this.wall = rgb(wall);
        this.flash = rgb(flash);
        this.start = rgb(start);
        this.hudBg = rgb(hudBg);
    }

    private static Color rgb(int hex) {
        return new Color(((hex >> 16) & 0xFF) / 255f, ((hex >> 8) & 0xFF) / 255f, (hex & 0xFF) / 255f, 1f);
    }

    public static final Theme[] ALL = {
        new Theme("Clássico", 0x1A1C29, 0x5294FA, 0xFF8C26, 0x296B38, 0x0D0D14),
        new Theme("Jardim",   0x2E4A1F, 0x1F7A2E, 0xFFE14D, 0x8A6A3A, 0x142010),
        new Theme("Masmorra", 0x2A2420, 0x8C8579, 0xFF5A36, 0x4A3B2A, 0x120F0D),
        new Theme("Gelo",     0x16304A, 0xBFEFFF, 0xFF6FB0, 0x2E6E8E, 0x0A1724),
        new Theme("Doces",    0x3A1E35, 0xFF8FC8, 0x7DFFB0, 0x7A3A6A, 0x1C0E1A),
        new Theme("Lava",     0x1E0E0A, 0xFF6A1F, 0x6FE3FF, 0x5A2A10, 0x0F0705),
        new Theme("Neon",     0x0B0B12, 0xC64BFF, 0x3DFFD8, 0x2B2050, 0x050508),
    };

    /** Escolha do jogador: -1 = automático (troca a cada rodada); 0.. = tema fixo. */
    public static final int AUTO = -1;

    /** Tema usado em uma rodada, conforme a escolha do jogador. */
    public static Theme forRound(int choice, int round) {
        if (choice >= 0 && choice < ALL.length) return ALL[choice];
        return ALL[Math.max(0, round - 1) % ALL.length];
    }

    public static String choiceName(int choice) {
        return choice >= 0 && choice < ALL.length ? ALL[choice].name : "Automático (muda a cada rodada)";
    }

    /** Próxima opção do ciclo: Automático -> Clássico -> Jardim -> ... -> Automático. */
    public static int next(int choice) {
        return choice + 1 >= ALL.length ? AUTO : choice + 1;
    }
}
