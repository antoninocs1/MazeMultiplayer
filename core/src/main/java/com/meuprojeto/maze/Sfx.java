package com.meuprojeto.maze;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Sound;

/**
 * Efeitos sonoros do jogo (arquivos em assets/sounds, gerados por tools/gerar_sons.py).
 * Se o computador não tiver áudio ou um arquivo faltar, o jogo continua, só que sem aquele som.
 */
public class Sfx {
    public enum Id {
        MENU_MOVE("menu_move", 0.6f),
        MENU_SELECT("menu_select", 0.7f),
        COUNTDOWN("countdown", 0.7f),
        GO("go", 0.8f),
        WALL_CHANGE("wall_change", 0.6f),
        STEP("step", 0.25f),
        FINISH("finish", 0.7f),
        WIN("win", 0.8f),
        LOSE("lose", 0.8f),
        GAME_OVER("gameover", 0.8f),
        MEOW("meow", 0.8f),
        CAUGHT("caught", 0.8f),
        PLOP("plop", 0.7f),
        SNAP("snap", 0.8f),
        EAT("eat", 0.7f),
        HISS("hiss", 0.8f),
        JOIN("join", 0.8f),
        LEAVE("leave", 0.8f),
        ERROR("error", 0.7f);

        final String file;
        final float volume;

        Id(String file, float volume) {
            this.file = file;
            this.volume = volume;
        }
    }

    private final Sound[] sounds = new Sound[Id.values().length];
    private boolean enabled = true;

    public Sfx() {
        for (Id id : Id.values()) {
            try {
                sounds[id.ordinal()] = Gdx.audio.newSound(Gdx.files.internal("sounds/" + id.file + ".wav"));
            } catch (Exception e) {
                Gdx.app.log("Sfx", "Som indisponível: " + id.file + " (" + e.getMessage() + ")");
            }
        }
    }

    public void play(Id id) {
        play(id, 1f);
    }

    public void play(Id id, float pitch) {
        if (!enabled) return;
        Sound s = sounds[id.ordinal()];
        if (s != null) s.play(id.volume, pitch, 0f);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void dispose() {
        for (Sound s : sounds) if (s != null) s.dispose();
    }
}
