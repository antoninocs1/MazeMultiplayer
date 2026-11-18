package com.meuprojeto.maze;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Preferences;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.viewport.Viewport;

/** Classe principal do cliente LibGDX. Guarda os recursos compartilhados pelas telas. */
public class MazeClient extends Game {
    public static final float HUD_H   = 60f;
    public static final float MAZE_W  = MazeRenderer.CELL * Match.COLS;
    public static final float MAZE_H  = MazeRenderer.CELL * Match.ROWS;
    public static final float WORLD_W = MAZE_W;
    public static final float WORLD_H = MAZE_H + HUD_H;

    public SpriteBatch batch;
    public ShapeRenderer shape;
    public BitmapFont font;
    public GlyphLayout layout;
    public Viewport viewport;
    public Preferences prefs;
    public Sfx sfx;
    /** Tema escolhido: {@link Theme#AUTO} ou índice de {@link Theme#ALL}. */
    public int themeChoice;

    @Override
    public void create() {
        batch = new SpriteBatch();
        shape = new ShapeRenderer();
        font = new BitmapFont();
        font.getRegion().getTexture().setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        font.setUseIntegerPositions(false);
        layout = new GlyphLayout();
        viewport = new FitViewport(WORLD_W, WORLD_H);
        prefs = Gdx.app.getPreferences("maze-multiplayer");
        sfx = new Sfx();
        sfx.setEnabled(prefs.getBoolean("sound_on", true));
        themeChoice = prefs.getInteger("theme", Theme.AUTO);
        if (themeChoice >= Theme.ALL.length) themeChoice = Theme.AUTO;
        setScreen(new MenuScreen(this));
    }

    @Override
    public void resize(int width, int height) {
        viewport.update(width, height, true);
        super.resize(width, height);
    }

    @Override
    public void render() {
        // M liga/desliga o som e T troca o tema (exceto enquanto o IP está sendo digitado no menu)
        Screen s = getScreen();
        boolean typing = s instanceof MenuScreen && ((MenuScreen) s).isTypingIp();
        if (!typing && Gdx.input.isKeyJustPressed(Input.Keys.M)) {
            sfx.setEnabled(!sfx.isEnabled());
            prefs.putBoolean("sound_on", sfx.isEnabled());
            prefs.flush();
            if (sfx.isEnabled()) sfx.play(Sfx.Id.MENU_SELECT);
        }
        if (!typing && Gdx.input.isKeyJustPressed(Input.Keys.T)) {
            themeChoice = Theme.next(themeChoice);
            prefs.putInteger("theme", themeChoice);
            prefs.flush();
            sfx.play(Sfx.Id.MENU_MOVE, 1.3f);
        }
        super.render();
    }

    /** Troca de tela liberando a anterior. */
    public void changeScreen(Screen next) {
        Screen old = getScreen();
        setScreen(next);
        if (old != null) old.dispose();
    }

    /** Desenha texto (batch já iniciado). y = topo do texto; align = Align.left/center/right. */
    public void text(String s, float x, float y, float scale, Color color, int align) {
        font.getData().setScale(scale);
        font.setColor(color);
        layout.setText(font, s);
        float dx = 0f;
        if (Align.isCenterHorizontal(align)) dx = -layout.width / 2f;
        else if (Align.isRight(align)) dx = -layout.width;
        font.draw(batch, layout, x + dx, y);
    }

    @Override
    public void dispose() {
        Screen s = getScreen();
        if (s != null) s.dispose();
        batch.dispose();
        shape.dispose();
        font.dispose();
        sfx.dispose();
    }
}
