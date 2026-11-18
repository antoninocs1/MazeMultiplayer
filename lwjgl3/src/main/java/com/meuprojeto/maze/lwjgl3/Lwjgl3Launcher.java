package com.meuprojeto.maze.lwjgl3;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.meuprojeto.maze.MazeClient;

public class Lwjgl3Launcher {
    public static void main(String[] args) {
        if (StartupHelper.startNewJvmIfRequired()) return; // necessário no macOS / Linux+NVIDIA
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("Labirinto do Ratinho - Multiplayer");
        config.setWindowedMode((int) MazeClient.WORLD_W, (int) MazeClient.WORLD_H);
        config.setResizable(true);
        config.useVsync(true);
        config.setForegroundFPS(60);
        config.setWindowIcon("libgdx128.png", "libgdx64.png", "libgdx32.png", "libgdx16.png");
        new Lwjgl3Application(new MazeClient(), config);
    }
}
