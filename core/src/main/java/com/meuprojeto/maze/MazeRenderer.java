package com.meuprojeto.maze;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.ScreenUtils;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Desenha o labirinto, os ratinhos, o HUD e as mensagens. Usado por todos os modos. */
public class MazeRenderer {
    public static final float CELL = 36f;
    private static final float WALL_T = 4f;

    private static final Color CHECK   = new Color(0.15f, 0.15f, 0.18f, 1f);
    private static final Color PINK    = new Color(1f, 0.62f, 0.70f, 1f);
    private static final Color NOSE    = new Color(0.85f, 0.30f, 0.42f, 1f);
    private static final Color WHISKER = new Color(0.85f, 0.85f, 0.85f, 1f);
    private static final Color CHEESE  = new Color(0.98f, 0.80f, 0.20f, 1f);
    private static final Color CHEESE_DARK = new Color(0.62f, 0.42f, 0.06f, 1f);
    private static final Color CHEESE_HOLE = new Color(0.86f, 0.62f, 0.10f, 1f);
    private static final Color TEXT_DIM = new Color(0.75f, 0.78f, 0.85f, 1f);
    private static final Color GOLD     = new Color(1f, 0.85f, 0.30f, 1f);

    public static final Color[] MOUSE_COLORS = {
        new Color(0.80f, 0.80f, 0.84f, 1f),  // cinza
        new Color(0.74f, 0.50f, 0.30f, 1f),  // marrom
        new Color(0.45f, 0.75f, 1.00f, 1f),  // azul
        new Color(0.95f, 0.80f, 0.35f, 1f),  // dourado
    };
    /** Cor de cada par de túneis. */
    public static final Color[] HOLE_COLORS = {
        new Color(0.75f, 0.45f, 1.00f, 1f),  // roxo
        new Color(0.25f, 0.95f, 0.80f, 1f),  // verde-água
    };
    private static final Color CAT_FUR     = new Color(0.95f, 0.58f, 0.20f, 1f);
    private static final Color CAT_STRIPE  = new Color(0.72f, 0.36f, 0.08f, 1f);
    private static final Color CAT_EYE     = new Color(0.55f, 1.00f, 0.35f, 1f);
    private static final Color DANGER      = new Color(1.00f, 0.25f, 0.25f, 1f);
    private static final Color WOOD        = new Color(0.80f, 0.58f, 0.32f, 1f);
    private static final Color WOOD_DARK   = new Color(0.50f, 0.34f, 0.16f, 1f);
    private static final Color METAL       = new Color(0.82f, 0.84f, 0.88f, 1f);
    private static final Color COOKIE      = new Color(0.95f, 0.72f, 0.30f, 1f);
    private static final Color COOKIE_CHIP = new Color(0.40f, 0.22f, 0.10f, 1f);
    private static final Color POWER       = new Color(1.00f, 0.92f, 0.30f, 1f);

    private static final float[][] OVERLAP_OFFSETS = {{-1, 1}, {1, -1}, {1, 1}, {-1, -1}};

    private final MazeClient game;
    private final List<float[]> flashes = new ArrayList<float[]>(); // x, y, dir, fechou(1/0), tempo
    private byte[] prevWalls;
    private int prevRound = -1;
    private final Color tmp = new Color();

    /** Tema da rodada atual e controle do aviso "Tema: ..." quando ele muda. */
    private Theme theme = Theme.ALL[0];
    private int lastChoice = Integer.MIN_VALUE;
    private Theme lastTheme;
    private float themeNotice;
    private float time;

    // usados por drawMouse
    private float mx, my, mcos, msin, mk;

    public MazeRenderer(MazeClient game) {
        this.game = game;
    }

    private float cellX(int x) { return x * CELL; }
    private float cellY(Maze mz, int y) { return (mz.rows - 1 - y) * CELL; }

    // ------------------------------------------------------------------ quadro principal

    public void render(Match m, int localId, float delta, String modeLabel) {
        trackChanges(m, delta);
        updateTheme(m, delta);
        time += delta;

        ScreenUtils.clear(0f, 0f, 0f, 1f);
        game.viewport.apply();
        ShapeRenderer s = game.shape;
        s.setProjectionMatrix(game.viewport.getCamera().combined);
        game.batch.setProjectionMatrix(game.viewport.getCamera().combined);

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        s.begin(ShapeRenderer.ShapeType.Filled);
        drawMaze(s, m.maze);
        drawHoles(s, m);
        drawTraps(s, m);
        drawFood(s, m);
        drawFlashes(s, m.maze);
        drawMice(s, m, localId);
        drawCat(s, m);
        drawHudBackground(s, m);
        s.end();

        game.batch.begin();
        drawHudText(m, localId, modeLabel);
        drawStatusText(m);
        game.batch.end();

        if (themeNotice > 0f) {
            String hint = game.themeChoice == Theme.AUTO ? "  (automático)" : "";
            notice("Tema: " + theme.name + hint + "   |   T: trocar tema", Math.min(1f, themeNotice / 0.5f), 15);
        }
    }

    private void updateTheme(Match m, float delta) {
        theme = Theme.forRound(game.themeChoice, m.round);
        boolean first = lastTheme == null;
        if (!first && (theme != lastTheme || game.themeChoice != lastChoice)) themeNotice = 2.5f;
        lastTheme = theme;
        lastChoice = game.themeChoice;
        if (themeNotice > 0f) themeNotice -= delta;
    }

    private void drawMaze(ShapeRenderer s, Maze mz) {
        s.setColor(theme.floor);
        s.rect(0, 0, mz.cols * CELL, mz.rows * CELL);

        // Célula de partida (canto superior esquerdo)
        s.setColor(theme.start);
        s.rect(cellX(0), cellY(mz, 0), CELL, CELL);

        // Linha de chegada quadriculada (canto inferior direito) + queijo
        float bx = cellX(mz.cols - 1), by = cellY(mz, mz.rows - 1);
        float q = CELL / 4f;
        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 4; j++) {
                s.setColor(((i + j) & 1) == 0 ? Color.WHITE : CHECK);
                s.rect(bx + i * q, by + j * q, q, q);
            }
        }
        drawCheese(s, bx + CELL / 2f, by + CELL / 2f, CELL / 36f);

        s.setColor(theme.wall);
        for (int y = 0; y < mz.rows; y++) {
            for (int x = 0; x < mz.cols; x++) {
                float px = cellX(x), py = cellY(mz, y);
                if (mz.hasWall(x, y, Maze.UP)) hWall(s, px, py + CELL, WALL_T);
                if (mz.hasWall(x, y, Maze.LEFT)) vWall(s, px, py, WALL_T);
                if (x == mz.cols - 1 && mz.hasWall(x, y, Maze.RIGHT)) vWall(s, px + CELL, py, WALL_T);
                if (y == mz.rows - 1 && mz.hasWall(x, y, Maze.DOWN)) hWall(s, px, py, WALL_T);
            }
        }
    }

    private void hWall(ShapeRenderer s, float x, float y, float t) {
        s.rect(x - t / 2f, y - t / 2f, CELL + t, t);
    }

    private void vWall(ShapeRenderer s, float x, float y, float t) {
        s.rect(x - t / 2f, y - t / 2f, t, CELL + t);
    }

    // ------------------------------------------------------------------ destaque das mudanças

    /** Compara o labirinto com o quadro anterior e registra as paredes que mudaram. */
    private void trackChanges(Match m, float delta) {
        byte[] walls = m.maze.walls;
        if (prevWalls == null || prevWalls.length != walls.length || m.round != prevRound) {
            flashes.clear();
            prevWalls = walls.clone();
            prevRound = m.round;
        } else {
            Maze mz = m.maze;
            for (int y = 0; y < mz.rows; y++) {
                for (int x = 0; x < mz.cols; x++) {
                    int i = mz.index(x, y);
                    if (prevWalls[i] == walls[i]) continue;
                    for (int d = Maze.RIGHT; d <= Maze.DOWN; d++) {
                        if (!mz.inside(x + Maze.DX[d], y + Maze.DY[d])) continue;
                        boolean before = (prevWalls[i] & (1 << d)) != 0;
                        boolean now = (walls[i] & (1 << d)) != 0;
                        if (before != now) flashes.add(new float[]{x, y, d, now ? 1f : 0f, 1f});
                    }
                }
            }
            System.arraycopy(walls, 0, prevWalls, 0, walls.length);
        }
        for (Iterator<float[]> it = flashes.iterator(); it.hasNext(); ) {
            float[] f = it.next();
            f[4] -= delta / 1.2f;
            if (f[4] <= 0f) it.remove();
        }
    }

    private void drawFlashes(ShapeRenderer s, Maze mz) {
        for (float[] f : flashes) {
            int x = (int) f[0], y = (int) f[1], d = (int) f[2];
            boolean closed = f[3] > 0.5f;
            float alpha = closed ? f[4] : f[4] * 0.6f;
            float t = closed ? WALL_T + 3f : WALL_T;
            s.setColor(theme.flash.r, theme.flash.g, theme.flash.b, alpha);
            if (d == Maze.RIGHT) vWall(s, cellX(x) + CELL, cellY(mz, y), t);
            else hWall(s, cellX(x), cellY(mz, y), t);
        }
    }

    // ------------------------------------------------------------------ túneis e gato

    private void drawHoles(ShapeRenderer s, Match m) {
        if (!m.hazards) return;
        Maze mz = m.maze;
        for (int i = 0; i < m.holeCount; i++) {
            Color c = HOLE_COLORS[(i / 2) % HOLE_COLORS.length];
            float x = cellX(m.holeX[i]) + CELL / 2f, y = cellY(mz, m.holeY[i]) + CELL / 2f;
            float pulse = 0.5f + 0.5f * MathUtils.sin(time * 4f + i);
            s.setColor(c.r, c.g, c.b, 0.25f + 0.25f * pulse);
            s.circle(x, y, CELL * (0.42f + 0.05f * pulse), 24);
            s.setColor(c);
            s.circle(x, y, CELL * 0.34f, 24);
            s.setColor(0.02f, 0.02f, 0.03f, 1f);
            s.circle(x, y, CELL * 0.26f, 24);
            // redemoinho girando dentro do buraco
            s.setColor(c.r, c.g, c.b, 0.8f);
            for (int k = 0; k < 3; k++) {
                float a = time * 3f + k * MathUtils.PI2 / 3f;
                s.circle(x + MathUtils.cos(a) * CELL * 0.14f, y + MathUtils.sin(a) * CELL * 0.14f, CELL * 0.045f, 8);
            }
        }
    }

    private void drawTraps(ShapeRenderer s, Match m) {
        if (!m.hazards) return;
        Maze mz = m.maze;
        for (int i = 0; i < m.trapCount; i++) {
            float x = cellX(m.trapX[i]) + CELL / 2f, y = cellY(mz, m.trapY[i]) + CELL / 2f;
            boolean armed = m.trapRearm[i] <= 0f;
            float a = armed ? 1f : 0.45f; // desarmada fica apagadinha até armar de novo
            // tábua
            s.setColor(WOOD_DARK.r, WOOD_DARK.g, WOOD_DARK.b, a);
            s.rect(x - 13f, y - 9f, 26f, 18f);
            s.setColor(WOOD.r, WOOD.g, WOOD.b, a);
            s.rect(x - 12f, y - 8f, 24f, 16f);
            // mola e isca
            s.setColor(METAL.r, METAL.g, METAL.b, a);
            s.circle(x - 3f, y, 2.4f, 10);
            if (armed) {
                s.setColor(CHEESE);
                s.triangle(x + 3f, y - 3f, x + 9f, y - 3f, x + 9f, y + 3f);
                // arco aberto para trás
                s.setColor(METAL);
                s.rectLine(x - 11f, y - 7f, x - 11f, y + 7f, 1.6f);
                s.rectLine(x - 11f, y - 7f, x - 3f, y - 7f, 1.6f);
                s.rectLine(x - 11f, y + 7f, x - 3f, y + 7f, 1.6f);
            } else {
                // arco fechado sobre a isca
                s.setColor(METAL.r, METAL.g, METAL.b, a);
                s.rectLine(x + 6f, y - 7f, x + 6f, y + 7f, 1.8f);
                s.rectLine(x - 3f, y - 7f, x + 6f, y - 7f, 1.6f);
                s.rectLine(x - 3f, y + 7f, x + 6f, y + 7f, 1.6f);
            }
        }
    }

    private void drawFood(ShapeRenderer s, Match m) {
        if (!m.hazards || !m.foodActive) return;
        float x = cellX(m.foodX) + CELL / 2f, y = cellY(m.maze, m.foodY) + CELL / 2f;
        float bob = MathUtils.sin(time * 5f) * 1.5f;
        float glow = 0.5f + 0.5f * MathUtils.sin(time * 6f);
        s.setColor(POWER.r, POWER.g, POWER.b, 0.18f + 0.22f * glow);
        s.circle(x, y + bob, CELL * 0.45f, 24);
        s.setColor(COOKIE_CHIP);
        s.circle(x, y + bob - 1f, 9.5f, 20);
        s.setColor(COOKIE);
        s.circle(x, y + bob, 9f, 20);
        s.setColor(COOKIE_CHIP);
        s.circle(x - 3.5f, y + bob + 2.5f, 1.6f, 8);
        s.circle(x + 3f, y + bob + 3.5f, 1.4f, 8);
        s.circle(x + 1f, y + bob - 3f, 1.7f, 8);
        s.circle(x - 4f, y + bob - 3.5f, 1.2f, 8);
        // brilhinho
        s.setColor(1f, 1f, 1f, glow);
        s.circle(x + 8f, y + bob + 8f, 1.6f, 8);
    }

    /** Número de segundos preso na ratoeira e "FORTE!" acima dos ratinhos. */
    private void drawStatusText(Match m) {
        if (!m.hazards || m.phase != Match.Phase.RUNNING) return;
        for (Mouse mo : m.mice) {
            if (!mo.participating || mo.finished) continue;
            float x = (mo.renderX + 0.5f) * CELL;
            float y = (m.maze.rows - mo.renderY - 0.5f) * CELL + CELL * 0.95f;
            if (mo.stun > 0f) {
                game.text(String.valueOf((int) Math.ceil(mo.stun)), x, y, 1.3f, DANGER, Align.center);
            } else if (mo.power > 0f) {
                game.text("FORTE!", x, y, 0.9f, POWER, Align.center);
            }
        }
    }

    private void drawCat(ShapeRenderer s, Match m) {
        if (!m.hazards || m.phase != Match.Phase.RUNNING) return;
        Cat cat = m.cat;
        float x = (cat.renderX + 0.5f) * CELL;
        float y = (m.maze.rows - cat.renderY - 0.5f) * CELL;
        if (cat.state == Cat.WARNING) {
            // aviso piscando onde o gato vai aparecer
            float blink = 0.5f + 0.5f * MathUtils.sin(time * 14f);
            s.setColor(DANGER.r, DANGER.g, DANGER.b, 0.35f + 0.4f * blink);
            s.circle(x, y, CELL * 0.5f, 24);
            s.setColor(1f, 1f, 1f, 0.9f);
            s.rect(x - 2.5f, y - 3f, 5f, 14f);
            s.circle(x, y - 8f, 2.8f, 10);
        } else if (cat.state == Cat.ACTIVE) {
            s.setColor(DANGER.r, DANGER.g, DANGER.b, 0.18f);
            s.circle(x, y, CELL * 0.6f, 24);
            drawCatShape(s, x, y, cat.facing, CELL / 36f * 1.15f);
        }
    }

    /** Gato visto de cima apontando para {@code facing}. */
    public void drawCatShape(ShapeRenderer s, float x, float y, int facing, float scale) {
        float angle = facingAngle(facing);
        setFrame(x, y, angle, scale);
        float wag = MathUtils.sin(time * 9f) * 3f;

        // rabo listrado
        s.setColor(CAT_FUR);
        seg(s, -10f, 0f, -16f, 2f + wag * 0.5f, 3.4f);
        seg(s, -16f, 2f + wag * 0.5f, -21f, wag, 3.0f);
        s.setColor(CAT_STRIPE);
        circ(s, -14f, 1.3f + wag * 0.4f, 1.5f);
        circ(s, -19f, 1f + wag * 0.8f, 1.4f);

        // corpo
        s.setColor(CAT_FUR);
        float bw = 22f * scale, bh = 15f * scale;
        float bcx = px(-2f, 0f), bcy = py(-2f, 0f);
        s.ellipse(bcx - bw / 2f, bcy - bh / 2f, bw, bh, angle, 20);
        s.setColor(CAT_STRIPE);
        seg(s, -6f, -6f, -6f, 6f, 1.6f);
        seg(s, -1f, -6.5f, -1f, 6.5f, 1.6f);

        // orelhas pontudas
        s.setColor(CAT_FUR);
        s.triangle(px(7f, 3f), py(7f, 3f), px(3f, 9f), py(3f, 9f), px(11f, 8f), py(11f, 8f));
        s.triangle(px(7f, -3f), py(7f, -3f), px(3f, -9f), py(3f, -9f), px(11f, -8f), py(11f, -8f));

        // cabeça
        circ(s, 8f, 0f, 7f);

        // bigodes
        s.setColor(WHISKER);
        seg(s, 12f, 2f, 18f, 5.5f, 0.6f);
        seg(s, 12f, -2f, 18f, -5.5f, 0.6f);
        seg(s, 12f, 1f, 18.5f, 1.5f, 0.6f);
        seg(s, 12f, -1f, 18.5f, -1.5f, 0.6f);

        // olhos e nariz
        s.setColor(CAT_EYE);
        circ(s, 10.5f, 3f, 1.8f);
        circ(s, 10.5f, -3f, 1.8f);
        s.setColor(Color.BLACK);
        circ(s, 10.9f, 3f, 0.8f);
        circ(s, 10.9f, -3f, 0.8f);
        s.setColor(NOSE);
        circ(s, 14.5f, 0f, 1.4f);
    }

    // ------------------------------------------------------------------ ratinhos

    private void drawMice(ShapeRenderer s, Match m, int localId) {
        Maze mz = m.maze;
        List<Mouse> visible = new ArrayList<Mouse>();
        for (Mouse mo : m.mice) {
            if (mo.participating || m.phase == Match.Phase.WAITING) visible.add(mo);
        }
        for (Mouse mo : visible) {
            float x = (mo.renderX + 0.5f) * CELL;
            float y = (mz.rows - mo.renderY - 0.5f) * CELL;
            // Se houver outro ratinho no mesmo lugar, afasta um pouco para os dois aparecerem
            for (Mouse other : visible) {
                if (other != mo && Math.abs(other.renderX - mo.renderX) < 0.4f
                    && Math.abs(other.renderY - mo.renderY) < 0.4f) {
                    float[] off = OVERLAP_OFFSETS[mo.color % OVERLAP_OFFSETS.length];
                    x += off[0] * CELL * 0.17f;
                    y += off[1] * CELL * 0.17f;
                    break;
                }
            }
            Color c = MOUSE_COLORS[mo.color % MOUSE_COLORS.length];
            if (mo.id == localId) {
                s.setColor(c.r, c.g, c.b, 0.28f);
                s.circle(x, y, CELL * 0.48f, 24);
            }
            boolean strong = mo.power > 0f && m.phase == Match.Phase.RUNNING;
            if (strong) {
                // brilho dourado pulsando (pisca rápido no último segundo e meio)
                float speed = mo.power < 1.5f ? 20f : 7f;
                float glow = 0.5f + 0.5f * MathUtils.sin(time * speed);
                s.setColor(POWER.r, POWER.g, POWER.b, 0.25f + 0.35f * glow);
                s.circle(x, y, CELL * (0.55f + 0.06f * glow), 24);
            }
            drawMouse(s, x, y, mo.facing, c, CELL / 36f * (strong ? 1.2f : 1f));
            if (mo.stun > 0f && m.phase == Match.Phase.RUNNING) {
                // preso: arco da ratoeira por cima do ratinho
                s.setColor(METAL);
                s.rectLine(x - 12f, y - 2f, x + 12f, y + 2f, 3f);
                s.setColor(WOOD_DARK.r, WOOD_DARK.g, WOOD_DARK.b, 0.9f);
                s.rect(x - 14f, y - 12f, 28f, 4f);
            }
        }
    }

    private static float facingAngle(int facing) {
        switch (facing) {
            case Maze.UP:   return 90f;
            case Maze.DOWN: return 270f;
            case Maze.LEFT: return 180f;
            default:        return 0f;
        }
    }

    private void setFrame(float x, float y, float angle, float k) {
        mx = x; my = y; mk = k;
        mcos = MathUtils.cosDeg(angle);
        msin = MathUtils.sinDeg(angle);
    }

    private float px(float lx, float ly) { return mx + (lx * mcos - ly * msin) * mk; }
    private float py(float lx, float ly) { return my + (lx * msin + ly * mcos) * mk; }

    private void circ(ShapeRenderer s, float lx, float ly, float r) {
        s.circle(px(lx, ly), py(lx, ly), r * mk, 16);
    }

    private void seg(ShapeRenderer s, float x1, float y1, float x2, float y2, float w) {
        s.rectLine(px(x1, y1), py(x1, y1), px(x2, y2), py(x2, y2), w * mk);
    }

    /** Desenha um ratinho visto de cima apontando para {@code facing}. */
    public void drawMouse(ShapeRenderer s, float x, float y, int facing, Color body, float scale) {
        float angle = facingAngle(facing);
        setFrame(x, y, angle, scale);

        // rabo
        s.setColor(PINK);
        seg(s, -9f, 0f, -14f, 3.5f, 1.8f);
        seg(s, -14f, 3.5f, -19f, 1f, 1.6f);
        seg(s, -19f, 1f, -22f, -2.5f, 1.3f);

        // orelhas
        s.setColor(body);
        circ(s, 3.5f, 6.5f, 4.2f);
        circ(s, 3.5f, -6.5f, 4.2f);
        s.setColor(PINK);
        circ(s, 3.8f, 6.7f, 2.4f);
        circ(s, 3.8f, -6.7f, 2.4f);

        // corpo
        s.setColor(body);
        float bw = 22f * scale, bh = 15f * scale;
        float bcx = px(-2f, 0f), bcy = py(-2f, 0f);
        s.ellipse(bcx - bw / 2f, bcy - bh / 2f, bw, bh, angle, 20);

        // cabeça e focinho
        circ(s, 7f, 0f, 6f);
        circ(s, 11.5f, 0f, 3.6f);

        // bigodes
        s.setColor(WHISKER);
        seg(s, 12.5f, 1.2f, 17f, 4.5f, 0.6f);
        seg(s, 12.5f, -1.2f, 17f, -4.5f, 0.6f);

        // olhos e nariz
        s.setColor(Color.BLACK);
        circ(s, 8.8f, 2.7f, 1.3f);
        circ(s, 8.8f, -2.7f, 1.3f);
        s.setColor(NOSE);
        circ(s, 14.8f, 0f, 1.8f);
    }

    /** Fatia de queijo (alvo na linha de chegada). */
    public void drawCheese(ShapeRenderer s, float cx, float cy, float k) {
        s.setColor(CHEESE_DARK);
        s.triangle(cx - 13f * k, cy - 9f * k, cx + 13f * k, cy - 9f * k, cx + 13f * k, cy + 9f * k);
        s.setColor(CHEESE);
        s.triangle(cx - 10f * k, cy - 7.5f * k, cx + 11.5f * k, cy - 7.5f * k, cx + 11.5f * k, cy + 6.5f * k);
        s.setColor(CHEESE_HOLE);
        s.circle(cx + 6f * k, cy - 3.5f * k, 2.2f * k, 12);
        s.circle(cx + 1f * k, cy - 5f * k, 1.4f * k, 10);
        s.circle(cx + 8.5f * k, cy + 2f * k, 1.4f * k, 10);
    }

    // ------------------------------------------------------------------ HUD

    private void drawHudBackground(ShapeRenderer s, Match m) {
        float top = MazeClient.WORLD_H;
        s.setColor(theme.hudBg);
        s.rect(0, MazeClient.MAZE_H, MazeClient.WORLD_W, MazeClient.HUD_H);
        for (int i = 0; i < m.mice.size(); i++) {
            Mouse mo = m.mice.get(i);
            Color c = MOUSE_COLORS[mo.color % MOUSE_COLORS.length];
            s.setColor(c);
            s.circle(16f + i * 200f + 6f, top - 42f, 6f, 16);
        }
        if (m.maxLives > 0 && !m.mice.isEmpty()) {
            Mouse mo = m.mice.get(0);
            for (int k = 0; k < m.maxLives; k++) {
                float alpha = k < mo.lives ? 1f : 0.18f;
                drawHeart(s, MazeClient.WORLD_W / 2f - 22f + k * 22f, top - 44f, alpha);
            }
        }
    }

    private void drawHeart(ShapeRenderer s, float x, float y, float alpha) {
        s.setColor(DANGER.r, DANGER.g, DANGER.b, alpha);
        s.circle(x - 3.6f, y + 2.5f, 4.4f, 14);
        s.circle(x + 3.6f, y + 2.5f, 4.4f, 14);
        s.triangle(x - 7.9f, y + 1.2f, x + 7.9f, y + 1.2f, x, y - 8f);
    }

    private void drawHudText(Match m, int localId, String modeLabel) {
        float top = MazeClient.WORLD_H;
        float w = MazeClient.WORLD_W;

        game.text(modeLabel + "  -  Rodada " + Math.max(1, m.round), 12f, top - 8f, 1.1f, Color.WHITE, Align.left);

        String time = String.format("Tempo: %.1f s", m.phase == Match.Phase.RUNNING || m.phase == Match.Phase.FINISHED
            ? m.elapsed : 0f);
        game.text(time, w / 2f, top - 8f, 1.2f, GOLD, Align.center);

        if (m.phase == Match.Phase.RUNNING) {
            game.text(String.format("O labirinto muda em %d s", (int) Math.ceil(m.mutationTimer)),
                w - 12f, top - 8f, 1.1f, theme.flash, Align.right);
        }
        if (m.bestTime > 0f) {
            game.text(String.format("Melhor tempo: %.1f s", m.bestTime), w - 12f, top - 34f, 1f, TEXT_DIM, Align.right);
        }

        for (int i = 0; i < m.mice.size(); i++) {
            Mouse mo = m.mice.get(i);
            String label = mo.name + (mo.id == localId ? " (você)" : "") + "  " + mo.wins
                + (mo.wins == 1 ? " vitória" : " vitórias");
            game.text(label, 16f + i * 200f + 18f, top - 34f, 0.95f, mo.id == localId ? Color.WHITE : TEXT_DIM, Align.left);
        }
    }

    // ------------------------------------------------------------------ mensagens

    /** Painel escuro com um título grande e linhas menores no centro do labirinto. */
    public void overlay(String title, String... lines) {
        float cx = MazeClient.MAZE_W / 2f, cy = MazeClient.MAZE_H / 2f;
        float h = 80f + lines.length * 26f;
        float w = 640f;

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        ShapeRenderer s = game.shape;
        s.begin(ShapeRenderer.ShapeType.Filled);
        s.setColor(0f, 0f, 0f, 0.72f);
        s.rect(cx - w / 2f, cy - h / 2f, w, h);
        s.end();

        game.batch.begin();
        float y = cy + h / 2f - 16f;
        game.text(title, cx, y, 2.6f, GOLD, Align.center);
        y -= 58f;
        for (String line : lines) {
            game.text(line, cx, y, 1.25f, Color.WHITE, Align.center);
            y -= 26f;
        }
        game.batch.end();
    }

    private static int livesLeft(Match m) {
        return m.mice.isEmpty() ? 0 : m.mice.get(0).lives;
    }

    /** Aviso curto em uma faixa no rodapé do labirinto. slot 0 = mais embaixo. */
    public void notice(String text, float alpha, int slot) {
        float y = 12f + slot * 30f;
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        game.font.getData().setScale(1.1f);
        game.layout.setText(game.font, text);
        float w = game.layout.width + 30f;
        ShapeRenderer s = game.shape;
        s.begin(ShapeRenderer.ShapeType.Filled);
        s.setColor(0.05f, 0.10f, 0.20f, 0.85f * alpha);
        s.rect(MazeClient.MAZE_W / 2f - w / 2f, y, w, 26f);
        s.end();
        game.batch.begin();
        game.text(text, MazeClient.MAZE_W / 2f, y + 19f, 1.1f, tmp.set(1f, 1f, 1f, alpha), Align.center);
        game.batch.end();
    }

    /** Mensagens padrão de cada fase da partida. */
    public void phaseOverlay(Match m, int localId, String... waitingInfo) {
        switch (m.phase) {
            case WAITING:
                overlay("Aguardando jogadores...", waitingInfo);
                break;
            case COUNTDOWN: {
                String tip;
                if (!m.hazards) tip = "";
                else if (!Match.catInRound(m.round)) tip = "Buracos coloridos são túneis. Desvie das ratoeiras!";
                else if (m.round == 2 && m.maxLives > 0) tip = "Cuidado com o GATO! Ele tira uma vida. Você tem " + livesLeft(m) + ".";
                else if (m.round == 2) tip = "Cuidado com o GATO! Coma o biscoito dourado para espantá-lo.";
                else tip = "O gato está mais rápido e há mais ratoeiras. A casa verde é um refúgio!";
                overlay(String.valueOf((int) Math.ceil(m.timer)),
                    "Prepare-se! Leve o ratinho até o queijo",
                    "no canto inferior direito. O labirinto vai mudar!", tip);
                break;
            }
            case RUNNING:
                if (m.elapsed < 0.8f) overlay("JÁ!");
                break;
            case FINISHED: {
                Mouse w = m.getMouse(m.winnerId);
                String next = String.format("Novo labirinto em %d s   |   Esc: voltar ao menu", (int) Math.ceil(m.timer));
                String best = m.bestTime > 0f ? String.format("Melhor tempo: %.1f s", m.bestTime) : "";
                if (w == null) {
                    overlay("Fim da rodada", next);
                } else {
                    String title;
                    if (m.mice.size() == 1) title = "Você chegou!";
                    else if (w.id == localId) title = "Você venceu!";
                    else title = w.name + " venceu!";
                    overlay(title, String.format("Tempo: %.1f s", w.finishTime), best, next);
                }
                break;
            }
        }
    }
}
