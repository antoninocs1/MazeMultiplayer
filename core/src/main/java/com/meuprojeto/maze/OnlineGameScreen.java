package com.meuprojeto.maze;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.meuprojeto.maze.server.GameServer;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Partida online. Conecta via TCP a um {@link GameServer}, envia a direção desejada
 * e desenha o snapshot recebido. Com {@code hosting = true}, também inicia o servidor
 * dentro deste mesmo programa.
 */
public class OnlineGameScreen extends ScreenAdapter {

    private static final float NOTICE_TIME = 5f;

    private final MazeClient game;
    private final MazeRenderer renderer;
    private final MatchSounds sounds;
    private final DirInput input = DirInput.both();
    private final String host;
    private final int port;
    private final boolean hosting;

    private GameServer server;
    private List<String> hostAddresses = new ArrayList<String>();

    private Socket socket;
    private DataOutputStream out;
    private volatile Match view;
    private volatile int playerId = -1;
    private volatile String myAddress = "";
    private volatile String status;
    private volatile boolean failed;
    private volatile boolean closing;
    private int lastSentDir = Integer.MIN_VALUE;
    private boolean errorSoundPlayed;

    /** Jogadores do snapshot anterior (por id), para avisar quem entrou/saiu. */
    private Map<Integer, Mouse> knownPlayers;
    /** Avisos temporários no rodapé: texto + tempo restante. */
    private final List<String> notices = new ArrayList<String>();
    private final List<Float> noticeTimers = new ArrayList<Float>();

    public OnlineGameScreen(MazeClient game, String host, int port, boolean hosting) {
        this.game = game;
        this.renderer = new MazeRenderer(game);
        this.sounds = new MatchSounds(game.sfx);
        this.host = host;
        this.port = port;
        this.hosting = hosting;
        this.status = "Conectando a " + host + ":" + port + "...";

        if (hosting) {
            try {
                server = new GameServer(port, game.prefs.getBoolean("hazards", true));
                server.start();
                hostAddresses = GameServer.localAddresses();
            } catch (IOException e) {
                server = null;
                fail("Não foi possível criar a partida na porta " + port + " (já está em uso?).");
                return;
            }
        }

        Thread t = new Thread(this::networkLoop, "ClientNetwork");
        t.setDaemon(true);
        t.start();
    }

    private void fail(String msg) {
        status = msg;
        failed = true;
    }

    private void networkLoop() {
        try {
            socket = new Socket();
            socket.connect(new InetSocketAddress(host, port), 5000);
            socket.setTcpNoDelay(true);
            myAddress = socket.getLocalAddress().getHostAddress();
            DataInputStream in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
            out = new DataOutputStream(socket.getOutputStream());

            if (in.readInt() != GameServer.MAGIC) {
                fail("O servidor em " + host + ":" + port + " não é um servidor do Labirinto (ou é de outra versão).");
                return;
            }
            int id = in.readInt();
            if (id < 0) {
                fail("A partida está cheia (máximo de " + Match.MAX_MICE + " jogadores).");
                return;
            }
            playerId = id;

            while (!closing) {
                view = Match.read(in);
            }
        } catch (IOException e) {
            if (!closing) {
                fail(view == null
                    ? "Não foi possível conectar em " + host + ":" + port + " (" + e.getMessage() + ")."
                    : "A conexão com o servidor foi perdida.");
            }
        } finally {
            closeSocket();
        }
    }

    private void sendDir(int dir) {
        DataOutputStream o = out;
        if (o == null || dir == lastSentDir) return;
        try {
            o.writeByte(dir);
            o.flush();
            lastSentDir = dir;
        } catch (IOException e) {
            if (!closing) fail("A conexão com o servidor foi perdida.");
        }
    }

    // ------------------------------------------------------------------ entrou / saiu

    private void trackPlayers(Match m) {
        Map<Integer, Mouse> now = new HashMap<Integer, Mouse>();
        for (Mouse mo : m.mice) now.put(mo.id, mo);

        if (knownPlayers == null) {
            // Primeiro snapshot: só confirma a conexão
            if (hosting) {
                notice("Partida criada! Passe o seu IP para os amigos: " + primaryHostIp());
            } else {
                notice("Você entrou na partida de " + host + "  (seu IP: " + myAddress + ")");
            }
            game.sfx.play(Sfx.Id.JOIN);
        } else {
            for (Mouse mo : m.mice) {
                if (!knownPlayers.containsKey(mo.id) && mo.id != playerId) {
                    notice(mo.name + " entrou na partida  -  IP " + displayIp(mo));
                    game.sfx.play(Sfx.Id.JOIN);
                }
            }
            for (Mouse old : knownPlayers.values()) {
                if (!now.containsKey(old.id)) {
                    notice(old.name + " saiu da partida  -  IP " + displayIp(old));
                    game.sfx.play(Sfx.Id.LEAVE);
                }
            }
        }
        knownPlayers = now;
    }

    private void notice(String text) {
        notices.add(text);
        noticeTimers.add(NOTICE_TIME);
        while (notices.size() > 3) {
            notices.remove(0);
            noticeTimers.remove(0);
        }
    }

    private void drawNotices(float delta) {
        for (int i = notices.size() - 1; i >= 0; i--) {
            float t = noticeTimers.get(i) - delta;
            if (t <= 0f) {
                notices.remove(i);
                noticeTimers.remove(i);
            } else {
                noticeTimers.set(i, t);
            }
        }
        for (int i = 0; i < notices.size(); i++) {
            float alpha = Math.min(1f, noticeTimers.get(i) / 0.6f);
            renderer.notice(notices.get(i), alpha, notices.size() - 1 - i);
        }
    }

    private static String displayIp(Mouse mo) {
        return mo.address == null || mo.address.isEmpty() ? "desconhecido" : mo.address;
    }

    private String primaryHostIp() {
        return hostAddresses.isEmpty() ? "?" : hostAddresses.get(0);
    }

    private String hostIpList() {
        StringBuilder ips = new StringBuilder();
        for (int i = 0; i < hostAddresses.size() && i < 2; i++) {
            if (i > 0) ips.append("  ou  ");
            ips.append(hostAddresses.get(i));
        }
        return ips.toString();
    }

    // ------------------------------------------------------------------ quadro

    @Override
    public void render(float delta) {
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            game.changeScreen(new MenuScreen(game));
            return;
        }
        if (failed && !errorSoundPlayed) {
            errorSoundPlayed = true;
            game.sfx.play(Sfx.Id.ERROR);
        }

        Match m = view;
        if (m == null) {
            drawBlank();
            renderer.overlay(failed ? "Ops!" : "Conectando...", status, failed ? "Esc: voltar ao menu" : "");
            return;
        }

        if (!failed) {
            sendDir(input.poll());
            trackPlayers(m);
            sounds.update(m, playerId);
        }

        String label = hosting
            ? "Anfitrião - IP " + primaryHostIp()
            : "Online - servidor " + host;
        renderer.render(m, playerId, delta, label);
        drawNotices(delta);

        if (failed) {
            renderer.overlay("Ops!", status, "Esc: voltar ao menu");
            return;
        }
        if (Gdx.input.isKeyPressed(Input.Keys.TAB)) {
            renderer.overlay("Jogadores", playerLines(m, true));
            return;
        }
        Mouse me = m.getMouse(playerId);
        if (me != null && !me.participating && m.phase != Match.Phase.WAITING) {
            renderer.overlay("Você entrou no meio da corrida", "Aguarde a próxima rodada...");
            return;
        }
        renderer.phaseOverlay(m, playerId, waitingInfo(m));
    }

    /** Uma linha por jogador com o IP de onde ele conectou. */
    private String[] playerLines(Match m, boolean withFooter) {
        List<String> lines = new ArrayList<String>();
        for (Mouse mo : m.mice) {
            lines.add(mo.name + (mo.id == playerId ? " (você)" : "") + ":  IP " + displayIp(mo));
        }
        if (withFooter) {
            lines.add("");
            lines.add(hosting ? "IP desta partida: " + hostIpList() + "  (porta " + port + ")"
                : "Conectado a " + host + ":" + port);
        }
        return lines.toArray(new String[0]);
    }

    private String[] waitingInfo(Match m) {
        List<String> lines = new ArrayList<String>();
        lines.add("Jogadores conectados: " + m.mice.size() + " (mínimo 2, máximo " + Match.MAX_MICE + ")");
        for (String p : playerLines(m, false)) lines.add(p);
        lines.add("");
        if (hosting) {
            lines.add("Seus amigos devem escolher \"Online: entrar em partida\"");
            lines.add("e digitar o seu IP: " + hostIpList() + "  (porta " + port + ")");
        } else {
            lines.add("Conectado a " + host + ":" + port + "   |   Seu IP: " + myAddress);
        }
        return lines.toArray(new String[0]);
    }

    private void drawBlank() {
        com.badlogic.gdx.utils.ScreenUtils.clear(0.07f, 0.08f, 0.12f, 1f);
        game.viewport.apply();
        game.shape.setProjectionMatrix(game.viewport.getCamera().combined);
        game.batch.setProjectionMatrix(game.viewport.getCamera().combined);
        game.shape.begin(ShapeRenderer.ShapeType.Filled);
        game.shape.setColor(0.07f, 0.08f, 0.12f, 1f);
        game.shape.rect(0, 0, MazeClient.WORLD_W, MazeClient.WORLD_H);
        game.shape.end();
    }

    private void closeSocket() {
        try { if (socket != null) socket.close(); } catch (IOException ignored) {}
    }

    @Override
    public void dispose() {
        closing = true;
        closeSocket();
        if (server != null) server.stop();
    }
}
