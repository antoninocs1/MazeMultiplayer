package com.meuprojeto.maze;

import java.util.HashMap;
import java.util.Map;

/**
 * Observa a partida quadro a quadro e toca os efeitos sonoros quando algo acontece
 * (contagem, largada, paredes mudando, passos, vitória/derrota).
 * Funciona igual nos modos locais e no online, pois só compara um estado com o anterior.
 */
public class MatchSounds {
    private final Sfx sfx;

    private Match.Phase prevPhase;
    private int prevRound = -1;
    private int prevCount = -1;
    private byte[] prevWalls;
    /** id do ratinho -> célula anterior (x * 1000 + y), para o som de passos. */
    private final Map<Integer, Integer> prevCell = new HashMap<Integer, Integer>();
    /** id do ratinho -> contadores anteriores (pego pelo gato / túnel). */
    private final Map<Integer, Integer> prevCaught = new HashMap<Integer, Integer>();
    private final Map<Integer, Integer> prevTeleports = new HashMap<Integer, Integer>();
    private final Map<Integer, Integer> prevTrapped = new HashMap<Integer, Integer>();
    private final Map<Integer, Integer> prevAte = new HashMap<Integer, Integer>();
    private final Map<Integer, Integer> prevScares = new HashMap<Integer, Integer>();
    private int prevCatState = Cat.HIDDEN;

    public MatchSounds(Sfx sfx) {
        this.sfx = sfx;
    }

    /** @param localId id do ratinho deste computador, ou -1 quando todos são locais (2 jogadores). */
    public void update(Match m, int localId) {
        Match.Phase phase = m.phase;

        if (phase == Match.Phase.COUNTDOWN) {
            int count = (int) Math.ceil(m.timer);
            if (count != prevCount && count > 0) sfx.play(Sfx.Id.COUNTDOWN);
            prevCount = count;
        } else {
            prevCount = -1;
        }

        if (prevPhase != null && phase != prevPhase) {
            if (phase == Match.Phase.RUNNING) {
                sfx.play(Sfx.Id.GO);
            } else if (phase == Match.Phase.FINISHED) {
                playResult(m, localId);
            } else if (phase == Match.Phase.GAME_OVER) {
                sfx.play(Sfx.Id.GAME_OVER);
            }
        }

        // Paredes mudaram durante a corrida (mesma rodada) -> som de "parede se mexendo"
        byte[] walls = m.maze.walls;
        if (prevWalls != null && prevWalls.length == walls.length && m.round == prevRound
            && phase == Match.Phase.RUNNING && !java.util.Arrays.equals(prevWalls, walls)) {
            sfx.play(Sfx.Id.WALL_CHANGE);
        }
        if (prevWalls == null || prevWalls.length != walls.length) prevWalls = walls.clone();
        else System.arraycopy(walls, 0, prevWalls, 0, walls.length);

        // Gato apareceu (aviso), ratinho pego, túnel
        if (m.cat.state == Cat.WARNING && prevCatState != Cat.WARNING) sfx.play(Sfx.Id.MEOW);
        prevCatState = m.cat.state;
        for (Mouse mo : m.mice) {
            Integer c = prevCaught.put(mo.id, mo.caught);
            if (c != null && mo.caught > c) sfx.play(Sfx.Id.CAUGHT, mo.id == localId || localId < 0 ? 1f : 1.15f);
            Integer t = prevTeleports.put(mo.id, mo.teleports);
            if (t != null && mo.teleports > t) sfx.play(Sfx.Id.PLOP, 0.9f + (mo.id % 4) * 0.1f);
            Integer tr = prevTrapped.put(mo.id, mo.trapped);
            if (tr != null && mo.trapped > tr) sfx.play(Sfx.Id.SNAP);
            Integer a = prevAte.put(mo.id, mo.ate);
            if (a != null && mo.ate > a) sfx.play(Sfx.Id.EAT);
            Integer sc = prevScares.put(mo.id, mo.scares);
            if (sc != null && mo.scares > sc) sfx.play(Sfx.Id.HISS);
        }

        // Passinhos dos ratinhos controlados neste computador
        for (Mouse mo : m.mice) {
            if (mo.ai || (localId >= 0 && mo.id != localId)) continue;
            int cell = Math.round(mo.renderX) * 1000 + Math.round(mo.renderY);
            Integer before = prevCell.put(mo.id, cell);
            boolean jumped = before != null && Math.abs(before / 1000 - cell / 1000) + Math.abs(before % 1000 - cell % 1000) > 1;
            if (before != null && before != cell && !jumped && phase == Match.Phase.RUNNING && mo.participating) {
                sfx.play(Sfx.Id.STEP, 0.9f + (mo.id % 4) * 0.1f);
            }
        }

        prevPhase = phase;
        prevRound = m.round;
    }

    private void playResult(Match m, int localId) {
        Mouse winner = m.getMouse(m.winnerId);
        if (winner == null) return;
        Mouse me = localId >= 0 ? m.getMouse(localId) : null;
        boolean allLocal = localId < 0;
        if (allLocal || m.mice.size() == 1 || winner.id == localId) {
            sfx.play(Sfx.Id.WIN);
        } else if (me != null && me.participating) {
            sfx.play(Sfx.Id.LOSE);
        } else {
            sfx.play(Sfx.Id.FINISH);
        }
    }
}
