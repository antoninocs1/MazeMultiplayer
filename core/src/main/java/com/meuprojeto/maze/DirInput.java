package com.meuprojeto.maze;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;

/**
 * Lê a direção desejada do teclado. Se várias teclas estiverem pressionadas,
 * vale a última que foi apertada (fica natural para fazer curvas).
 */
public class DirInput {
    private final int[][] keys; // keys[direção] = teclas
    private int last = Maze.NONE;

    private DirInput(int[][] keys) {
        this.keys = keys;
    }

    public static DirInput wasd() {
        return new DirInput(new int[][]{{Input.Keys.W}, {Input.Keys.D}, {Input.Keys.S}, {Input.Keys.A}});
    }

    public static DirInput arrows() {
        return new DirInput(new int[][]{{Input.Keys.UP}, {Input.Keys.RIGHT}, {Input.Keys.DOWN}, {Input.Keys.LEFT}});
    }

    public static DirInput both() {
        return new DirInput(new int[][]{
            {Input.Keys.W, Input.Keys.UP},
            {Input.Keys.D, Input.Keys.RIGHT},
            {Input.Keys.S, Input.Keys.DOWN},
            {Input.Keys.A, Input.Keys.LEFT}});
    }

    public int poll() {
        for (int d = 0; d < 4; d++) {
            for (int k : keys[d]) if (Gdx.input.isKeyJustPressed(k)) last = d;
        }
        if (last != Maze.NONE && held(last)) return last;
        for (int d = 0; d < 4; d++) {
            if (held(d)) {
                last = d;
                return d;
            }
        }
        last = Maze.NONE;
        return Maze.NONE;
    }

    private boolean held(int d) {
        for (int k : keys[d]) if (Gdx.input.isKeyPressed(k)) return true;
        return false;
    }
}
