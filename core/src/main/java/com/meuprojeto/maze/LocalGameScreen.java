package com.meuprojeto.maze;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.ScreenAdapter;

/** Partidas que rodam inteiramente neste computador (sem rede). */
public class LocalGameScreen extends ScreenAdapter {

    public enum Mode { SOLO, VS_CPU, TWO_PLAYERS }

    private final MazeClient game;
    private final Mode mode;
    private final Match match = new Match(1);
    private final MazeRenderer renderer;
    private final DirInput input1;
    private final DirInput input2;
    private final int localId;
    private final String label;
    private float savedBest;
    private boolean recordSaved;
    private boolean newRecord;
    private int bestRound;
    private final MatchSounds sounds;

    public LocalGameScreen(MazeClient game, Mode mode, int aiLevel) {
        this.game = game;
        this.mode = mode;
        this.renderer = new MazeRenderer(game);
        this.sounds = new MatchSounds(game.sfx);
        match.hazards = game.prefs.getBoolean("hazards", true);

        switch (mode) {
            case SOLO:
                match.addMouse(0, "Você", false, 0);
                input1 = DirInput.both();
                input2 = null;
                localId = 0;
                label = "Sozinho";
                break;
            case VS_CPU:
                match.addMouse(0, "Você", false, 0);
                match.addMouse(1, "Computador", true, aiLevel);
                input1 = DirInput.both();
                input2 = null;
                localId = 0;
                label = "Contra o computador (" + Match.AI_LEVEL_NAMES[aiLevel] + ")";
                break;
            default:
                match.addMouse(0, "Jogador 1", false, 0);
                match.addMouse(1, "Jogador 2", false, 0);
                input1 = DirInput.wasd();
                input2 = DirInput.arrows();
                localId = -1;
                label = "2 jogadores";
                break;
        }

        if (mode == Mode.SOLO) {
            savedBest = game.prefs.getFloat("best_solo", -1f);
            match.bestTime = savedBest;
            // 3 vidas para o jogo inteiro (só faz sentido com o gato ligado)
            if (match.hazards) {
                match.maxLives = 3;
                match.getMouse(0).lives = 3;
            }
        }
    }

    @Override
    public void render(float delta) {
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            game.changeScreen(new MenuScreen(game));
            return;
        }

        match.setInput(0, input1.poll());
        if (input2 != null) match.setInput(1, input2.poll());
        match.update(Math.min(delta, 0.05f));

        if (mode == Mode.SOLO && match.bestTime > 0f && match.bestTime != savedBest) {
            savedBest = match.bestTime;
            game.prefs.putFloat("best_solo", savedBest);
            game.prefs.flush();
        }

        if (match.phase == Match.Phase.GAME_OVER) {
            gameOver(delta);
            return;
        }

        sounds.update(match, localId);
        renderer.render(match, localId, delta, label);
        renderer.phaseOverlay(match, localId);
    }

    /** Sem vidas: mostra até onde chegou, guarda o recorde e oferece jogar de novo. */
    private void gameOver(float delta) {
        if (!recordSaved) {
            recordSaved = true;
            bestRound = game.prefs.getInteger("best_round_solo", 0);
            if (match.round > bestRound) {
                newRecord = true;
                bestRound = match.round;
                game.prefs.putInteger("best_round_solo", bestRound);
                game.prefs.flush();
            }
            sounds.update(match, localId); // toca o som de fim de jogo
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.ENTER) || Gdx.input.isKeyJustPressed(Input.Keys.SPACE)
            || Gdx.input.isKeyJustPressed(Input.Keys.NUMPAD_ENTER)) {
            game.sfx.play(Sfx.Id.MENU_SELECT);
            game.changeScreen(new LocalGameScreen(game, Mode.SOLO, 0));
            return;
        }
        renderer.render(match, localId, delta, label);
        renderer.overlay("Fim de jogo!",
            "O gato pegou você 3 vezes.",
            "Você chegou até a rodada " + match.round + ".",
            newRecord ? "NOVO RECORDE!" : "Recorde: rodada " + bestRound,
            "",
            "Enter: jogar de novo    Esc: voltar ao menu");
    }
}
