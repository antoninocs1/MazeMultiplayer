package com.meuprojeto.maze;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Regras da partida: rodadas, contagem regressiva, movimento dos ratinhos, IA,
 * mudanças dinâmicas do labirinto e vencedor.
 *
 * É usada diretamente nos modos locais (sozinho, contra o computador, 2 jogadores)
 * e pelo servidor no modo online. No cliente online, uma cópia é recebida a cada tick
 * via {@link #read(DataInputStream)} apenas para desenhar.
 */
public class Match {

    public enum Phase { WAITING, COUNTDOWN, RUNNING, FINISHED, GAME_OVER }

    public static final int COLS = 25;
    public static final int ROWS = 15;
    public static final int MAX_MICE = 4;

    public static final float COUNTDOWN_TIME = 3f;
    public static final float FINISHED_TIME = 5f;
    /** A cada quantos segundos o labirinto muda. */
    public static final float MUTATION_INTERVAL = 4f;
    /** Quantas paredes trocam de lugar em cada mudança. */
    public static final int MUTATIONS_PER_WAVE = 4;
    /** Fração de paredes extras abertas (caminhos alternativos). */
    public static final float LOOP_FACTOR = 0.06f;

    public static final int MAX_HOLES = 4;
    public static final int MAX_TRAPS = 3;
    /** Tempo até a ratoeira armar de novo depois de pegar alguém. */
    public static final float TRAP_REARM = 6f;
    /** Duração da força do biscoito. */
    public static final float POWER_TIME = 6f;
    /** Tempo até aparecer um novo biscoito depois de comido. */
    public static final float FOOD_RESPAWN = 8f;
    /** Quando o primeiro gato da rodada aparece (segundos após a largada). */
    public static final float CAT_FIRST_DELAY = 4f;

    /** Velocidade do jogador humano, em células por segundo. */
    public static final float PLAYER_SPEED = 7f;

    public static final String[] AI_LEVEL_NAMES = {"Fácil", "Médio", "Difícil"};
    private static final float[] AI_SPEED   = {4.0f, 4.8f, 6.0f};
    private static final float[] AI_MISTAKE = {0.18f, 0.10f, 0.03f};

    public Maze maze;
    public final List<Mouse> mice = new ArrayList<Mouse>();
    public Phase phase = Phase.WAITING;
    public float timer;
    public float elapsed;
    public float mutationTimer;
    public int round;
    public int winnerId = -1;
    public float bestTime = -1f;

    /**
     * Vidas de cada ratinho para o jogo inteiro (0 = sem vidas). Usado só no modo Sozinho:
     * cada vez que o gato pega, perde uma; sem vidas, a partida vai para GAME_OVER.
     */
    public int maxLives;

    /** Gato e túneis ligados (opção do menu). */
    public boolean hazards = true;
    public final Cat cat = new Cat();
    /** Túneis: pares de buracos. O buraco 2i liga com o 2i+1. */
    public int holeCount;
    public final int[] holeX = new int[MAX_HOLES];
    public final int[] holeY = new int[MAX_HOLES];
    /** Ratoeiras. trapRearm > 0 = desarmada (acabou de fechar), volta a armar quando chega a 0. */
    public int trapCount;
    public final int[] trapX = new int[MAX_TRAPS];
    public final int[] trapY = new int[MAX_TRAPS];
    public final float[] trapRearm = new float[MAX_TRAPS];
    /** Biscoito de força: quem come espanta o gato. */
    public boolean foodActive;
    public int foodX, foodY;
    private float foodTimer;

    private final int minPlayers;
    private final Random rnd = new Random();
    private final Maze.EdgeLock lock = new Maze.EdgeLock() {
        @Override
        public boolean isLocked(int x, int y, int dir) {
            return isEdgeInUse(x, y, dir);
        }
    };

    /** @param minPlayers quantos ratinhos são necessários para começar uma rodada. */
    public Match(int minPlayers) {
        this.minPlayers = Math.max(1, minPlayers);
        maze = new Maze(COLS, ROWS);
        maze.generate(rnd, LOOP_FACTOR);
    }

    /** Usado apenas por {@link #read}. */
    private Match() {
        this.minPlayers = 1;
    }

    public int goalX() { return maze.cols - 1; }
    public int goalY() { return maze.rows - 1; }

    // ------------------------------------------------------------------ jogadores

    public Mouse addMouse(int id, String name, boolean ai, int aiLevel) {
        Mouse m = new Mouse(id);
        m.name = name;
        m.ai = ai;
        m.aiLevel = Math.max(0, Math.min(AI_SPEED.length - 1, aiLevel));
        m.color = freeColor();
        m.lives = maxLives;
        m.resetToStart();
        // Quem entra durante a contagem já participa; durante a corrida espera a próxima rodada.
        m.participating = phase == Phase.COUNTDOWN;
        mice.add(m);
        return m;
    }

    public void removeMouse(int id) {
        for (int i = 0; i < mice.size(); i++) {
            if (mice.get(i).id == id) {
                mice.remove(i);
                return;
            }
        }
    }

    public Mouse getMouse(int id) {
        for (Mouse m : mice) if (m.id == id) return m;
        return null;
    }

    public void setInput(int id, int dir) {
        Mouse m = getMouse(id);
        if (m != null && !m.ai) m.desiredDir = (dir >= 0 && dir <= 3) ? dir : Maze.NONE;
    }

    public int participants() {
        int n = 0;
        for (Mouse m : mice) if (m.participating) n++;
        return n;
    }

    public int getMinPlayers() {
        return minPlayers;
    }

    private int freeColor() {
        for (int c = 0; c < MAX_MICE; c++) {
            boolean used = false;
            for (Mouse m : mice) if (m.color == c) used = true;
            if (!used) return c;
        }
        return mice.size() % MAX_MICE;
    }

    private boolean enoughPlayers() {
        return !mice.isEmpty() && mice.size() >= minPlayers;
    }

    // ------------------------------------------------------------------ simulação

    public void update(float dt) {
        switch (phase) {
            case WAITING:
                if (enoughPlayers()) beginRound();
                break;

            case COUNTDOWN:
                if (!enoughPlayers()) {
                    phase = Phase.WAITING;
                    break;
                }
                timer -= dt;
                if (timer <= 0f) {
                    phase = Phase.RUNNING;
                    elapsed = 0f;
                    mutationTimer = MUTATION_INTERVAL;
                }
                break;

            case RUNNING:
                if (participants() == 0) {
                    phase = Phase.WAITING;
                    break;
                }
                elapsed += dt;
                tickHazards(dt);
                mutationTimer -= dt;
                if (mutationTimer <= 0f) {
                    mutationTimer += MUTATION_INTERVAL;
                    for (int i = 0; i < MUTATIONS_PER_WAVE; i++) maze.mutate(rnd, lock);
                }
                for (Mouse m : mice) {
                    if (m.participating && !m.finished) stepMouse(m, dt);
                    if (phase != Phase.RUNNING) break;
                }
                if (phase == Phase.RUNNING) updateCat(dt);
                break;

            case FINISHED:
                timer -= dt;
                if (timer <= 0f) {
                    if (enoughPlayers()) beginRound();
                    else phase = Phase.WAITING;
                }
                break;
        }
    }

    private void beginRound() {
        round++;
        maze.generate(rnd, LOOP_FACTOR);
        for (Mouse m : mice) {
            m.resetToStart();
            m.participating = true;
        }
        winnerId = -1;
        elapsed = 0f;
        foodActive = false;
        trapCount = 0;
        placeHoles();
        placeTraps();
        placeFood();
        resetCat();
        timer = COUNTDOWN_TIME;
        mutationTimer = MUTATION_INTERVAL;
        phase = Phase.COUNTDOWN;
    }

    private void stepMouse(Mouse m, float dt) {
        if (m.stun > 0f) { // preso na ratoeira
            m.updateRender();
            return;
        }
        float move = (m.ai ? AI_SPEED[m.aiLevel] : PLAYER_SPEED) * dt;
        while (move > 0f) {
            if (!m.moving) {
                int d = m.ai ? aiDecide(m) : m.desiredDir;
                if (d == Maze.NONE || !maze.canMove(m.cx, m.cy, d)) break;
                m.tx = m.cx + Maze.DX[d];
                m.ty = m.cy + Maze.DY[d];
                m.moving = true;
                m.progress = 0f;
                m.facing = d;
            }
            float need = 1f - m.progress;
            if (move < need) {
                m.progress += move;
                move = 0f;
            } else {
                move -= need;
                m.cx = m.tx;
                m.cy = m.ty;
                m.progress = 0f;
                m.moving = false;
                if (m.cx == goalX() && m.cy == goalY()) {
                    finish(m);
                    break;
                }
                int hole = hazards ? holeAt(m.cx, m.cy) : -1;
                if (hole >= 0) {
                    int other = hole ^ 1; // o par do buraco
                    m.cx = m.tx = holeX[other];
                    m.cy = m.ty = holeY[other];
                    m.teleports++;
                    m.wanderSteps = 0;
                }
                if (arriveHazards(m)) break;
            }
        }
        m.updateRender();
    }

    private void finish(Mouse m) {
        m.finished = true;
        m.finishTime = elapsed;
        if (winnerId < 0) {
            winnerId = m.id;
            m.wins++;
            if (bestTime < 0f || elapsed < bestTime) bestTime = elapsed;
            phase = Phase.FINISHED;
            timer = FINISHED_TIME;
        }
    }

    // ------------------------------------------------------------------ gato e túneis

    /** Pares de túneis na rodada: 1 na primeira, 2 a partir da segunda. */
    public static int holePairsForRound(int round) {
        return round <= 1 ? 1 : 2;
    }

    /** O gato só aparece a partir da 2ª rodada. */
    public static boolean catInRound(int round) {
        return round >= 2;
    }

    /** Velocidade do gato (células/s): começa em 4,5 e sobe 0,3 por rodada, até 6,2 (o jogador anda 7). */
    public static float catSpeed(int round) {
        return Math.min(6.2f, 4.5f + 0.3f * (round - 2));
    }

    /** Intervalo entre um gato e o próximo: 13 s na 2ª rodada, diminuindo 2 s por rodada, mínimo 6 s. */
    public static float catInterval(int round) {
        return Math.max(6f, 13f - 2f * (round - 2));
    }

    /** Ratoeiras na rodada: 1 na 1ª, 2 na 2ª, 3 a partir da 3ª. */
    public static int trapsForRound(int round) {
        return Math.max(1, Math.min(MAX_TRAPS, round));
    }

    /** Quanto tempo o ratinho fica preso: 2 s na 1ª rodada, +0,5 s por rodada, até 4 s. */
    public static float trapStun(int round) {
        return Math.min(4f, 2f + 0.5f * (round - 1));
    }

    /** Ratoeira armada em (x, y), ou -1. */
    public int trapAt(int x, int y) {
        for (int i = 0; i < trapCount; i++) {
            if (trapX[i] == x && trapY[i] == y && trapRearm[i] <= 0f) return i;
        }
        return -1;
    }

    private boolean occupied(int x, int y) {
        if (holeAt(x, y) >= 0) return true;
        for (int i = 0; i < trapCount; i++) if (trapX[i] == x && trapY[i] == y) return true;
        return foodActive && foodX == x && foodY == y;
    }

    /** Célula livre aleatória, longe da partida e da chegada; -1 se não achar. */
    private int randomFreeCell(int minFromStart) {
        int[] fromStart = maze.distancesTo(0, 0);
        int[] fromGoal = maze.distancesTo(goalX(), goalY());
        for (int attempt = 0; attempt < 400; attempt++) {
            int x = rnd.nextInt(maze.cols), y = rnd.nextInt(maze.rows);
            int i = maze.index(x, y);
            if (fromStart[i] < minFromStart || fromGoal[i] < 3 || occupied(x, y)) continue;
            boolean nearMouse = false;
            for (Mouse m : mice) if (Math.abs(m.cx - x) + Math.abs(m.cy - y) < 3) nearMouse = true;
            if (!nearMouse) return i;
        }
        return -1;
    }

    private void placeTraps() {
        trapCount = 0;
        if (!hazards) return;
        int n = trapsForRound(round);
        for (int t = 0; t < n; t++) {
            int c = randomFreeCell(5);
            if (c < 0) break;
            trapX[trapCount] = c % maze.cols;
            trapY[trapCount] = c / maze.cols;
            trapRearm[trapCount] = 0f;
            trapCount++;
        }
    }

    private void placeFood() {
        foodActive = false;
        if (!hazards || !catInRound(round)) return;
        int c = randomFreeCell(6);
        if (c < 0) return;
        foodX = c % maze.cols;
        foodY = c / maze.cols;
        foodActive = true;
    }

    /** Relógios de ratoeiras, biscoito e efeitos dos ratinhos. */
    private void tickHazards(float dt) {
        for (Mouse m : mice) {
            if (m.stun > 0f) m.stun = Math.max(0f, m.stun - dt);
            if (m.power > 0f) m.power = Math.max(0f, m.power - dt);
        }
        if (!hazards) return;
        for (int i = 0; i < trapCount; i++) {
            if (trapRearm[i] > 0f) trapRearm[i] = Math.max(0f, trapRearm[i] - dt);
        }
        if (!foodActive && catInRound(round)) {
            foodTimer -= dt;
            if (foodTimer <= 0f) placeFood();
        }
    }

    /** Efeitos da célula onde o ratinho acabou de chegar. Retorna true se ele ficou preso. */
    private boolean arriveHazards(Mouse m) {
        if (!hazards) return false;
        if (foodActive && m.cx == foodX && m.cy == foodY) {
            foodActive = false;
            foodTimer = FOOD_RESPAWN;
            m.power = POWER_TIME;
            m.ate++;
        }
        int t = trapAt(m.cx, m.cy);
        if (t >= 0) {
            trapRearm[t] = TRAP_REARM;
            m.stun = trapStun(round);
            m.trapped++;
            m.wanderSteps = 0;
            return true;
        }
        return false;
    }

    /** Índice do buraco em (x, y), ou -1. */
    public int holeAt(int x, int y) {
        for (int i = 0; i < holeCount; i++) if (holeX[i] == x && holeY[i] == y) return i;
        return -1;
    }

    private void placeHoles() {
        holeCount = 0;
        if (!hazards) return;
        int pairs = holePairsForRound(round);
        int[] fromStart = maze.distancesTo(0, 0);
        int[] fromGoal = maze.distancesTo(goalX(), goalY());
        for (int p = 0; p < pairs; p++) {
            for (int attempt = 0; attempt < 400; attempt++) {
                int ax = rnd.nextInt(maze.cols), ay = rnd.nextInt(maze.rows);
                int bx = rnd.nextInt(maze.cols), by = rnd.nextInt(maze.rows);
                if (!goodHoleCell(ax, ay, fromStart, fromGoal) || !goodHoleCell(bx, by, fromStart, fromGoal)) continue;
                if (Math.abs(ax - bx) + Math.abs(ay - by) < 12) continue; // pontas bem longe uma da outra
                holeX[holeCount] = ax; holeY[holeCount] = ay; holeCount++;
                holeX[holeCount] = bx; holeY[holeCount] = by; holeCount++;
                break;
            }
        }
    }

    private boolean goodHoleCell(int x, int y, int[] fromStart, int[] fromGoal) {
        int i = maze.index(x, y);
        if (fromStart[i] < 4 || fromGoal[i] < 4) return false; // nem colado na partida nem na chegada
        for (int h = 0; h < holeCount; h++) {
            if (Math.abs(holeX[h] - x) + Math.abs(holeY[h] - y) < 3) return false;
        }
        return true;
    }

    private void resetCat() {
        cat.state = Cat.HIDDEN;
        cat.timer = CAT_FIRST_DELAY;
        cat.placeAt(goalX(), goalY());
    }

    private void updateCat(float dt) {
        if (!hazards || !catInRound(round)) {
            cat.state = Cat.HIDDEN;
            return;
        }
        cat.timer -= dt;
        switch (cat.state) {
            case Cat.HIDDEN:
                if (cat.timer <= 0f) {
                    int spawn = catSpawnCell();
                    cat.placeAt(spawn % maze.cols, spawn / maze.cols);
                    cat.state = Cat.WARNING;
                    cat.timer = Cat.WARNING_TIME;
                }
                break;
            case Cat.WARNING:
                if (cat.timer <= 0f) {
                    cat.state = Cat.ACTIVE;
                    cat.timer = Cat.HUNT_TIME;
                }
                break;
            case Cat.ACTIVE:
                if (cat.timer <= 0f) {
                    catLeaves();
                    break;
                }
                stepCat(dt);
                checkCatch();
                break;
        }
    }

    private void catLeaves() {
        cat.state = Cat.HIDDEN;
        cat.timer = catInterval(round);
    }

    /** Célula onde o gato aparece: a mais longe possível de todos os ratinhos (e fora da partida/chegada). */
    private int catSpawnCell() {
        int n = maze.cols * maze.rows;
        int[] minDist = new int[n];
        java.util.Arrays.fill(minDist, Integer.MAX_VALUE);
        for (Mouse m : mice) {
            if (!m.participating || m.finished) continue;
            int[] d = maze.distancesTo(m.cx, m.cy);
            for (int i = 0; i < n; i++) if (d[i] >= 0 && d[i] < minDist[i]) minDist[i] = d[i];
        }
        int best = -1, bestScore = -1;
        for (int i = 0; i < n; i++) {
            int x = i % maze.cols, y = i / maze.cols;
            if ((x == 0 && y == 0) || (x == goalX() && y == goalY()) || holeAt(x, y) >= 0) continue;
            int score = minDist[i] == Integer.MAX_VALUE ? 0 : Math.min(minDist[i], 18) * 10 + rnd.nextInt(30);
            if (score > bestScore) {
                bestScore = score;
                best = i;
            }
        }
        return best < 0 ? maze.index(maze.cols / 2, maze.rows / 2) : best;
    }

    private void stepCat(float dt) {
        float move = catSpeed(round) * dt;
        while (move > 0f) {
            if (!cat.moving) {
                int d = catDecide();
                if (d == Maze.NONE) break;
                cat.tx = cat.cx + Maze.DX[d];
                cat.ty = cat.cy + Maze.DY[d];
                cat.moving = true;
                cat.progress = 0f;
                cat.facing = d;
            }
            float need = 1f - cat.progress;
            if (move < need) {
                cat.progress += move;
                move = 0f;
            } else {
                move -= need;
                cat.cx = cat.tx;
                cat.cy = cat.ty;
                cat.progress = 0f;
                cat.moving = false;
            }
        }
        cat.updateRender();
    }

    /** Vai atrás do ratinho mais próximo pelo labirinto. A casa de partida é um refúgio: o gato não entra. */
    private int catDecide() {
        int[] fromCat = maze.distancesTo(cat.cx, cat.cy);
        Mouse target = null;
        int best = Integer.MAX_VALUE;
        boolean strongNearby = false;
        for (Mouse m : mice) {
            if (!m.participating || m.finished || (m.cx == 0 && m.cy == 0 && !m.moving)) continue;
            if (m.power > 0f) {
                int d = fromCat[maze.index(m.cx, m.cy)];
                if (d >= 0 && d <= 6) strongNearby = true;
                continue; // com medo de ratinho forte
            }
            int d = fromCat[maze.index(m.cx, m.cy)];
            if (d >= 0 && d < best) {
                best = d;
                target = m;
            }
        }
        if (strongNearby && (target == null || best > 4)) return catFlee();
        if (target == null) return Maze.NONE;
        int d = maze.directionTowards(cat.cx, cat.cy, target.cx, target.cy);
        if (d == Maze.NONE) return Maze.NONE;
        if (cat.cx + Maze.DX[d] == 0 && cat.cy + Maze.DY[d] == 0) return Maze.NONE;
        return d;
    }

    /** Foge dos ratinhos fortes: vai para a célula vizinha mais longe deles. */
    private int catFlee() {
        int n = maze.cols * maze.rows;
        int[] near = new int[n];
        java.util.Arrays.fill(near, Integer.MAX_VALUE);
        for (Mouse m : mice) {
            if (m.power <= 0f || !m.participating || m.finished) continue;
            int[] d = maze.distancesTo(m.cx, m.cy);
            for (int i = 0; i < n; i++) if (d[i] >= 0 && d[i] < near[i]) near[i] = d[i];
        }
        int best = Maze.NONE, bestDist = near[maze.index(cat.cx, cat.cy)];
        for (int d = 0; d < 4; d++) {
            if (!maze.canMove(cat.cx, cat.cy, d)) continue;
            int nx = cat.cx + Maze.DX[d], ny = cat.cy + Maze.DY[d];
            if (nx == 0 && ny == 0) continue;
            int v = near[maze.index(nx, ny)];
            if (v > bestDist) {
                bestDist = v;
                best = d;
            }
        }
        return best;
    }

    private void checkCatch() {
        for (Mouse m : mice) {
            if (!m.participating || m.finished) continue;
            float dx = m.renderX - cat.renderX, dy = m.renderY - cat.renderY;
            if (dx * dx + dy * dy < 0.55f * 0.55f) {
                if (m.power > 0f) { // ratinho forte espanta o gato
                    m.scares++;
                    catLeaves();
                    return;
                }
                m.sendToStart();
                m.caught++;
                if (maxLives > 0) {
                    m.lives--;
                    if (m.lives <= 0) {
                        m.lives = 0;
                        phase = Phase.GAME_OVER;
                    }
                }
                catLeaves(); // satisfeito, vai embora por um tempo
                return;
            }
        }
    }

    /** Uma passagem está "em uso" se algum ratinho está atravessando-a agora. */
    private boolean isEdgeInUse(int x, int y, int dir) {
        int nx = x + Maze.DX[dir], ny = y + Maze.DY[dir];
        for (Mouse m : mice) {
            if (!m.moving) continue;
            if (m.cx == x && m.cy == y && m.tx == nx && m.ty == ny) return true;
            if (m.cx == nx && m.cy == ny && m.tx == x && m.ty == y) return true;
        }
        if (cat.state == Cat.ACTIVE && cat.moving) {
            if (cat.cx == x && cat.cy == y && cat.tx == nx && cat.ty == ny) return true;
            if (cat.cx == nx && cat.cy == ny && cat.tx == x && cat.ty == y) return true;
        }
        return false;
    }

    /**
     * IA do computador: segue o menor caminho até o queijo (recalculado a cada célula,
     * então se adapta às mudanças do labirinto). De vez em quando "se distrai" e explora
     * um caminho errado por alguns passos — a frequência depende da dificuldade.
     */
    private int aiDecide(Mouse m) {
        int back = Maze.opposite(m.facing);
        int[] options = new int[4];
        int n = 0;
        for (int d = 0; d < 4; d++) {
            if (d != back && maze.canMove(m.cx, m.cy, d)) options[n++] = d;
        }

        if (m.wanderSteps > 0) {
            m.wanderSteps--;
            if (n == 0) return maze.canMove(m.cx, m.cy, back) ? back : Maze.NONE;
            return options[rnd.nextInt(n)];
        }

        int best = aiBestDirection(m);
        if (n >= 2 && rnd.nextFloat() < AI_MISTAKE[m.aiLevel]) {
            int[] wrong = new int[4];
            int w = 0;
            for (int i = 0; i < n; i++) if (options[i] != best) wrong[w++] = options[i];
            if (w > 0) {
                m.wanderSteps = 3 + rnd.nextInt(8);
                return wrong[rnd.nextInt(w)];
            }
        }
        return best;
    }

    /**
     * Melhor direção para o queijo considerando os túneis (entrar num buraco = estar no outro)
     * e fugindo do gato (evita andar para perto dele).
     */
    private int aiBestDirection(Mouse m) {
        int[] dist = maze.distancesTo(goalX(), goalY());
        int[] fromCat = cat.state == Cat.ACTIVE ? maze.distancesTo(cat.cx, cat.cy) : null;
        int[] foodDist = hazards && foodActive && cat.state != Cat.HIDDEN ? maze.distancesTo(foodX, foodY) : null;
        int best = Maze.NONE, bestCost = Integer.MAX_VALUE;
        for (int d = 0; d < 4; d++) {
            if (!maze.canMove(m.cx, m.cy, d)) continue;
            int nx = m.cx + Maze.DX[d], ny = m.cy + Maze.DY[d];
            int cost = dist[maze.index(nx, ny)];
            int hole = hazards ? holeAt(nx, ny) : -1;
            if (hole >= 0) {
                int exit = dist[maze.index(holeX[hole ^ 1], holeY[hole ^ 1])];
                if (exit >= 0 && (cost < 0 || exit < cost)) cost = exit;
            }
            if (cost < 0) cost = 10000;
            if (hazards && trapAt(nx, ny) >= 0) cost += 40;
            if (foodDist != null && m.power <= 0f) {
                int f = foodDist[maze.index(nx, ny)];
                if (f >= 0 && f < 6) cost = Math.min(cost, f - 50); // biscoito perto e gato solto: vai comer
            }
            if (fromCat != null && m.power > 0f) {
                int c = fromCat[maze.index(nx, ny)];
                if (c >= 0 && c < 5) cost = Math.min(cost, c - 50); // forte: vai enfrentar o gato
            }
            if (fromCat != null && m.power <= 0f) {
                int c = fromCat[maze.index(nx, ny)];
                if (c >= 0 && c <= 3) cost += (4 - c) * 15; // quanto mais perto do gato, pior
            }
            if (cost < bestCost) {
                bestCost = cost;
                best = d;
            }
        }
        return best;
    }

    // ------------------------------------------------------------------ rede

    /** Serializa o estado completo (snapshot) para enviar aos clientes. */
    public void write(DataOutputStream out) throws IOException {
        out.writeByte(phase.ordinal());
        out.writeFloat(timer);
        out.writeFloat(elapsed);
        out.writeFloat(mutationTimer);
        out.writeInt(round);
        out.writeInt(winnerId);
        out.writeFloat(bestTime);
        out.writeShort(maze.cols);
        out.writeShort(maze.rows);
        out.write(maze.walls);
        out.writeBoolean(hazards);
        out.writeByte(holeCount);
        for (int i = 0; i < holeCount; i++) {
            out.writeByte(holeX[i]);
            out.writeByte(holeY[i]);
        }
        out.writeByte(trapCount);
        for (int i = 0; i < trapCount; i++) {
            out.writeByte(trapX[i]);
            out.writeByte(trapY[i]);
            out.writeFloat(trapRearm[i]);
        }
        out.writeBoolean(foodActive);
        out.writeByte(foodX);
        out.writeByte(foodY);
        out.writeByte(cat.state);
        out.writeFloat(cat.timer);
        out.writeFloat(cat.renderX);
        out.writeFloat(cat.renderY);
        out.writeByte(cat.facing);
        out.writeByte(mice.size());
        for (Mouse m : mice) {
            out.writeInt(m.id);
            out.writeUTF(m.name);
            out.writeUTF(m.address == null ? "" : m.address);
            out.writeByte(m.color);
            out.writeBoolean(m.ai);
            out.writeFloat(m.renderX);
            out.writeFloat(m.renderY);
            out.writeByte(m.facing);
            out.writeBoolean(m.participating);
            out.writeBoolean(m.finished);
            out.writeFloat(m.finishTime);
            out.writeInt(m.wins);
            out.writeInt(m.caught);
            out.writeInt(m.teleports);
            out.writeInt(m.trapped);
            out.writeInt(m.ate);
            out.writeInt(m.scares);
            out.writeFloat(m.stun);
            out.writeFloat(m.power);
        }
    }

    /** Lê um snapshot enviado por {@link #write}. O objeto resultado serve apenas para desenho. */
    public static Match read(DataInputStream in) throws IOException {
        Match s = new Match();
        s.phase = Phase.values()[in.readByte()];
        s.timer = in.readFloat();
        s.elapsed = in.readFloat();
        s.mutationTimer = in.readFloat();
        s.round = in.readInt();
        s.winnerId = in.readInt();
        s.bestTime = in.readFloat();
        int cols = in.readShort();
        int rows = in.readShort();
        s.maze = new Maze(cols, rows);
        in.readFully(s.maze.walls);
        s.hazards = in.readBoolean();
        s.holeCount = in.readByte();
        for (int i = 0; i < s.holeCount; i++) {
            s.holeX[i] = in.readByte();
            s.holeY[i] = in.readByte();
        }
        s.trapCount = in.readByte();
        for (int i = 0; i < s.trapCount; i++) {
            s.trapX[i] = in.readByte();
            s.trapY[i] = in.readByte();
            s.trapRearm[i] = in.readFloat();
        }
        s.foodActive = in.readBoolean();
        s.foodX = in.readByte();
        s.foodY = in.readByte();
        s.cat.state = in.readByte();
        s.cat.timer = in.readFloat();
        s.cat.renderX = in.readFloat();
        s.cat.renderY = in.readFloat();
        s.cat.facing = in.readByte();
        int n = in.readByte();
        for (int i = 0; i < n; i++) {
            Mouse m = new Mouse(in.readInt());
            m.name = in.readUTF();
            m.address = in.readUTF();
            m.color = in.readByte();
            m.ai = in.readBoolean();
            m.renderX = in.readFloat();
            m.renderY = in.readFloat();
            m.facing = in.readByte();
            m.participating = in.readBoolean();
            m.finished = in.readBoolean();
            m.finishTime = in.readFloat();
            m.wins = in.readInt();
            m.caught = in.readInt();
            m.teleports = in.readInt();
            m.trapped = in.readInt();
            m.ate = in.readInt();
            m.scares = in.readInt();
            m.stun = in.readFloat();
            m.power = in.readFloat();
            s.mice.add(m);
        }
        return s;
    }
}
