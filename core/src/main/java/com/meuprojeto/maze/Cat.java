package com.meuprojeto.maze;

/** O gato que aparece de tempos em tempos e persegue os ratinhos. Sem dependências de LibGDX. */
public class Cat {
    /** Escondido (esperando a hora de aparecer). */
    public static final int HIDDEN = 0;
    /** Aviso piscando no lugar onde ele vai aparecer. */
    public static final int WARNING = 1;
    /** Caçando. */
    public static final int ACTIVE = 2;

    /** Duração do aviso antes de o gato aparecer. */
    public static final float WARNING_TIME = 1.5f;
    /** Quanto tempo o gato fica caçando antes de ir embora. */
    public static final float HUNT_TIME = 8f;

    public int state = HIDDEN;
    /** HIDDEN: tempo até o aviso. WARNING: tempo até aparecer. ACTIVE: tempo até ir embora. */
    public float timer;

    public int cx, cy, tx, ty;
    public float progress;
    public boolean moving;
    public int facing = Maze.LEFT;
    public float renderX, renderY;

    void placeAt(int x, int y) {
        cx = tx = x;
        cy = ty = y;
        progress = 0f;
        moving = false;
        updateRender();
    }

    void updateRender() {
        renderX = cx + (tx - cx) * progress;
        renderY = cy + (ty - cy) * progress;
    }
}
