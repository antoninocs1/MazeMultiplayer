package com.meuprojeto.maze;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * Labirinto em grade. Não depende de LibGDX, então é usado tanto pelo cliente quanto pelo servidor.
 *
 * Cada célula guarda 4 bits de parede (cima, direita, baixo, esquerda).
 * A linha 0 é a de cima; a coluna 0 é a da esquerda.
 * Início = (0, 0) canto superior esquerdo. Chegada = (cols-1, rows-1) canto inferior direito.
 */
public class Maze {
    public static final int NONE  = -1;
    public static final int UP    = 0;
    public static final int RIGHT = 1;
    public static final int DOWN  = 2;
    public static final int LEFT  = 3;
    public static final int[] DX = {0, 1, 0, -1};
    public static final int[] DY = {-1, 0, 1, 0};

    /** Diz se uma passagem não pode ser fechada agora (ex.: um ratinho está atravessando). */
    public interface EdgeLock {
        boolean isLocked(int x, int y, int dir);
    }

    public final int cols;
    public final int rows;
    public final byte[] walls;

    public Maze(int cols, int rows) {
        this.cols = cols;
        this.rows = rows;
        this.walls = new byte[cols * rows];
        Arrays.fill(walls, (byte) 0x0F);
    }

    public static int opposite(int dir) {
        return (dir + 2) & 3;
    }

    public int index(int x, int y) {
        return y * cols + x;
    }

    public boolean inside(int x, int y) {
        return x >= 0 && y >= 0 && x < cols && y < rows;
    }

    public boolean hasWall(int x, int y, int dir) {
        return (walls[index(x, y)] & (1 << dir)) != 0;
    }

    public boolean canMove(int x, int y, int dir) {
        if (dir < 0 || dir > 3 || !inside(x, y)) return false;
        return !hasWall(x, y, dir) && inside(x + DX[dir], y + DY[dir]);
    }

    /** Abre ou fecha a parede dos dois lados (célula e vizinha). */
    public void setWall(int x, int y, int dir, boolean closed) {
        applyBit(x, y, dir, closed);
        int nx = x + DX[dir], ny = y + DY[dir];
        if (inside(nx, ny)) applyBit(nx, ny, opposite(dir), closed);
    }

    private void applyBit(int x, int y, int dir, boolean closed) {
        int i = index(x, y);
        if (closed) walls[i] |= (1 << dir);
        else        walls[i] &= ~(1 << dir);
    }

    /**
     * Gera um labirinto perfeito (backtracking recursivo iterativo) e depois abre algumas
     * passagens extras para criar caminhos alternativos (bom para corridas).
     */
    public void generate(Random rnd, float loopFactor) {
        Arrays.fill(walls, (byte) 0x0F);
        boolean[] visited = new boolean[cols * rows];
        int[] stack = new int[cols * rows];
        int sp = 0;
        stack[sp++] = 0;
        visited[0] = true;
        int[] options = new int[4];

        while (sp > 0) {
            int cur = stack[sp - 1];
            int x = cur % cols, y = cur / cols;
            int n = 0;
            for (int d = 0; d < 4; d++) {
                int nx = x + DX[d], ny = y + DY[d];
                if (inside(nx, ny) && !visited[index(nx, ny)]) options[n++] = d;
            }
            if (n == 0) {
                sp--;
                continue;
            }
            int d = options[rnd.nextInt(n)];
            int nx = x + DX[d], ny = y + DY[d];
            setWall(x, y, d, false);
            visited[index(nx, ny)] = true;
            stack[sp++] = index(nx, ny);
        }

        int extra = Math.round(cols * rows * loopFactor);
        for (int i = 0; i < extra; i++) {
            int x = rnd.nextInt(cols), y = rnd.nextInt(rows), d = rnd.nextInt(4);
            if (inside(x + DX[d], y + DY[d]) && hasWall(x, y, d)) setWall(x, y, d, false);
        }
    }

    /** Células alcançáveis a partir de (sx, sy). */
    public boolean[] reachableFrom(int sx, int sy) {
        boolean[] seen = new boolean[cols * rows];
        int[] queue = new int[cols * rows];
        int head = 0, tail = 0;
        queue[tail++] = index(sx, sy);
        seen[index(sx, sy)] = true;
        while (head < tail) {
            int c = queue[head++];
            int x = c % cols, y = c / cols;
            for (int d = 0; d < 4; d++) {
                if (!canMove(x, y, d)) continue;
                int n = index(x + DX[d], y + DY[d]);
                if (!seen[n]) {
                    seen[n] = true;
                    queue[tail++] = n;
                }
            }
        }
        return seen;
    }

    /** Distância (em passos) de cada célula até (gx, gy); -1 se inalcançável. */
    public int[] distancesTo(int gx, int gy) {
        int[] dist = new int[cols * rows];
        Arrays.fill(dist, -1);
        int[] queue = new int[cols * rows];
        int head = 0, tail = 0;
        queue[tail++] = index(gx, gy);
        dist[index(gx, gy)] = 0;
        while (head < tail) {
            int c = queue[head++];
            int x = c % cols, y = c / cols;
            for (int d = 0; d < 4; d++) {
                if (!canMove(x, y, d)) continue;
                int n = index(x + DX[d], y + DY[d]);
                if (dist[n] < 0) {
                    dist[n] = dist[c] + 1;
                    queue[tail++] = n;
                }
            }
        }
        return dist;
    }

    /** Primeiro passo do menor caminho de (x, y) até (gx, gy), ou NONE. */
    public int directionTowards(int x, int y, int gx, int gy) {
        int[] dist = distancesTo(gx, gy);
        int here = dist[index(x, y)];
        if (here <= 0) return NONE;
        for (int d = 0; d < 4; d++) {
            if (canMove(x, y, d) && dist[index(x + DX[d], y + DY[d])] == here - 1) return d;
        }
        return NONE;
    }

    /**
     * "Labirinto dinâmico": fecha uma passagem aleatória e abre outra parede, garantindo
     * que TODAS as células continuem conectadas (sempre existe caminho até a chegada).
     */
    public boolean mutate(Random rnd, EdgeLock lock) {
        List<int[]> open = new ArrayList<int[]>();
        List<int[]> closed = new ArrayList<int[]>();
        for (int y = 0; y < rows; y++) {
            for (int x = 0; x < cols; x++) {
                for (int d = RIGHT; d <= DOWN; d++) {
                    if (!inside(x + DX[d], y + DY[d])) continue;
                    int[] e = {x, y, d};
                    if (hasWall(x, y, d)) closed.add(e);
                    else if (lock == null || !lock.isLocked(x, y, d)) open.add(e);
                }
            }
        }
        if (open.isEmpty() || closed.isEmpty()) return false;

        int[] cut = open.get(rnd.nextInt(open.size()));
        setWall(cut[0], cut[1], cut[2], true);

        boolean[] side = reachableFrom(cut[0], cut[1]);
        int ox = cut[0] + DX[cut[2]], oy = cut[1] + DY[cut[2]];

        List<int[]> candidates;
        if (side[index(ox, oy)]) {
            // Continua conectado: abre outra parede qualquer para manter a quantidade de passagens.
            candidates = closed;
        } else {
            // Ficou dividido em duas partes: abre uma parede que ligue as duas novamente.
            candidates = new ArrayList<int[]>();
            for (int[] e : closed) {
                boolean a = side[index(e[0], e[1])];
                boolean b = side[index(e[0] + DX[e[2]], e[1] + DY[e[2]])];
                if (a != b) candidates.add(e);
            }
        }
        if (candidates.isEmpty()) {
            setWall(cut[0], cut[1], cut[2], false);
            return false;
        }
        int[] e = candidates.get(rnd.nextInt(candidates.size()));
        setWall(e[0], e[1], e[2], false);
        return true;
    }
}
