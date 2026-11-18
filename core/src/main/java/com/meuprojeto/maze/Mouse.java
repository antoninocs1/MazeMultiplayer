package com.meuprojeto.maze;

/** Um ratinho (jogador humano ou computador). Sem dependências de LibGDX. */
public class Mouse {
    public final int id;
    public String name = "";
    public int color;
    public boolean ai;
    /** Endereço IP de onde o jogador conectou (apenas no modo online; vazio nos modos locais). */
    public String address = "";
    public int aiLevel;

    /** Célula atual (origem do movimento). */
    public int cx, cy;
    /** Célula de destino enquanto está se movendo. */
    public int tx, ty;
    public float progress;
    public boolean moving;
    public int facing = Maze.RIGHT;
    public int desiredDir = Maze.NONE;

    public boolean participating;
    public boolean finished;
    public float finishTime;
    public int wins;
    /** Vidas restantes (só no modo Sozinho; nos outros modos não é usado). */
    public int lives;
    /** Contadores (só aumentam) usados pelo cliente para tocar sons: pego pelo gato / passou por túnel. */
    public int caught;
    public int teleports;
    public int trapped;
    public int ate;
    public int scares;

    /** Segundos que ainda fica preso na ratoeira (0 = livre). */
    public float stun;
    /** Segundos de força restantes depois de comer o biscoito (0 = normal). */
    public float power;

    /** Posição para desenho, em unidades de célula (canto da célula). */
    public float renderX, renderY;

    // Estado da IA
    int wanderSteps;

    public Mouse(int id) {
        this.id = id;
    }

    public void resetToStart() {
        cx = cy = tx = ty = 0;
        progress = 0f;
        moving = false;
        facing = Maze.RIGHT;
        desiredDir = Maze.NONE;
        finished = false;
        finishTime = 0f;
        wanderSteps = 0;
        stun = 0f;
        power = 0f;
        updateRender();
    }

    /** Volta para a casa de partida sem perder a tecla que o jogador está segurando. */
    void sendToStart() {
        cx = cy = tx = ty = 0;
        progress = 0f;
        moving = false;
        wanderSteps = 0;
        stun = 0f;
        updateRender();
    }

    void updateRender() {
        renderX = cx + (tx - cx) * progress;
        renderY = cy + (ty - cy) * progress;
    }
}
