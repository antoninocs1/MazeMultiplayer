package com.meuprojeto.maze;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.ScreenUtils;
import com.meuprojeto.maze.server.GameServer;

/** Menu inicial: escolha do modo de jogo. */
public class MenuScreen extends ScreenAdapter {
    private static final int SOLO = 0, CPU = 1, TWO = 2, HOST = 3, JOIN = 4, HAZARDS = 5, EXIT = 6;
    private static final int COUNT = 7;

    private final String[] help = {
        "Encontre a saída o mais rápido possível. Mova com as setas ou WASD.",
        "Apostem corrida: você contra um ratinho do computador. Esquerda/direita mudam a dificuldade.",
        "Dois jogadores no mesmo teclado: Jogador 1 usa WASD, Jogador 2 usa as setas.",
        "", // preenchido no construtor com o IP deste computador
        "Digite o IP de quem criou a partida (ex.: 192.168.0.10) e pressione Enter.",
        "Túneis e ratoeiras desde a 1ª rodada; gato e biscoito de força a partir da 2ª. Todos os modos.",
        "",
    };

    private static final Color BG = new Color(0.07f, 0.08f, 0.12f, 1f);
    private static final Color GOLD = new Color(1f, 0.85f, 0.30f, 1f);
    private static final Color DIM = new Color(0.70f, 0.73f, 0.80f, 1f);
    private static final Color SEL_BG = new Color(0.20f, 0.32f, 0.55f, 1f);

    private final MazeClient game;
    private final MazeRenderer renderer;
    private int selected;
    private int aiLevel;
    private boolean hazards;
    private String ip;
    private float time;
    private final String myIps;

    public MenuScreen(MazeClient game) {
        this.game = game;
        this.renderer = new MazeRenderer(game);
        this.selected = game.prefs.getInteger("menu_selected", SOLO);
        this.aiLevel = game.prefs.getInteger("ai_level", 1);
        this.hazards = game.prefs.getBoolean("hazards", true);
        this.ip = game.prefs.getString("server_ip", "localhost");
        if (selected < 0 || selected >= COUNT) selected = SOLO;

        java.util.List<String> ips = GameServer.localAddresses();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ips.size() && i < 2; i++) {
            if (i > 0) sb.append("  ou  ");
            sb.append(ips.get(i));
        }
        myIps = sb.toString();
        help[HOST] = "Abre uma partida aqui (até 4 jogadores). Seu IP: " + myIps + "  (porta " + GameServer.DEFAULT_PORT + ")";
    }

    /** true enquanto o jogador digita o IP (aí a tecla M não liga/desliga o som). */
    public boolean isTypingIp() {
        return selected == JOIN;
    }

    @Override
    public void show() {
        Gdx.input.setInputProcessor(new InputAdapter() {
            @Override
            public boolean keyDown(int key) {
                boolean editing = selected == JOIN;
                if (key == Input.Keys.UP || (!editing && key == Input.Keys.W)) {
                    selected = (selected + COUNT - 1) % COUNT;
                    game.sfx.play(Sfx.Id.MENU_MOVE);
                } else if (key == Input.Keys.DOWN || (!editing && key == Input.Keys.S)) {
                    selected = (selected + 1) % COUNT;
                    game.sfx.play(Sfx.Id.MENU_MOVE);
                } else if (selected == CPU && (key == Input.Keys.LEFT || key == Input.Keys.A)) {
                    aiLevel = Math.max(0, aiLevel - 1);
                    game.sfx.play(Sfx.Id.MENU_MOVE, 0.8f);
                } else if (selected == CPU && (key == Input.Keys.RIGHT || key == Input.Keys.D)) {
                    aiLevel = Math.min(Match.AI_LEVEL_NAMES.length - 1, aiLevel + 1);
                    game.sfx.play(Sfx.Id.MENU_MOVE, 1.2f);
                } else if (selected == HAZARDS && (key == Input.Keys.LEFT || key == Input.Keys.A
                    || key == Input.Keys.RIGHT || key == Input.Keys.D)) {
                    toggleHazards();
                } else if (key == Input.Keys.ENTER || key == Input.Keys.NUMPAD_ENTER
                    || (!editing && key == Input.Keys.SPACE)) {
                    activate();
                } else if (editing && key == Input.Keys.BACKSPACE) {
                    if (!ip.isEmpty()) ip = ip.substring(0, ip.length() - 1);
                } else if (key == Input.Keys.ESCAPE) {
                    Gdx.app.exit();
                } else {
                    return false;
                }
                return true;
            }

            @Override
            public boolean keyTyped(char c) {
                if (selected != JOIN) return false;
                if ((Character.isLetterOrDigit(c) || c == '.' || c == '-' || c == ':') && ip.length() < 40) {
                    ip += c;
                    game.sfx.play(Sfx.Id.MENU_MOVE, 1.5f);
                    return true;
                }
                return false;
            }
        });
    }

    @Override
    public void hide() {
        Gdx.input.setInputProcessor(null);
    }

    private void toggleHazards() {
        hazards = !hazards;
        game.prefs.putBoolean("hazards", hazards);
        game.prefs.flush();
        game.sfx.play(hazards ? Sfx.Id.MEOW : Sfx.Id.MENU_SELECT);
    }

    private void activate() {
        if (selected == HAZARDS) {
            toggleHazards();
            return;
        }
        game.prefs.putInteger("menu_selected", selected);
        game.prefs.putInteger("ai_level", aiLevel);
        game.prefs.putString("server_ip", ip);
        game.prefs.flush();
        if (selected != EXIT && !(selected == JOIN && ip.trim().isEmpty())) game.sfx.play(Sfx.Id.MENU_SELECT);

        switch (selected) {
            case SOLO:
                game.changeScreen(new LocalGameScreen(game, LocalGameScreen.Mode.SOLO, 0));
                break;
            case CPU:
                game.changeScreen(new LocalGameScreen(game, LocalGameScreen.Mode.VS_CPU, aiLevel));
                break;
            case TWO:
                game.changeScreen(new LocalGameScreen(game, LocalGameScreen.Mode.TWO_PLAYERS, 0));
                break;
            case HOST:
                game.changeScreen(new OnlineGameScreen(game, "localhost", GameServer.DEFAULT_PORT, true));
                break;
            case JOIN: {
                String host = ip.trim();
                if (host.isEmpty()) return;
                int port = GameServer.DEFAULT_PORT;
                int colon = host.lastIndexOf(':');
                if (colon > 0) {
                    try {
                        port = Integer.parseInt(host.substring(colon + 1));
                    } catch (NumberFormatException ignored) {
                    }
                    host = host.substring(0, colon);
                }
                game.changeScreen(new OnlineGameScreen(game, host, port, false));
                break;
            }
            case HAZARDS:
                break;
            case EXIT:
                Gdx.app.exit();
                break;
        }
    }

    private String label(int i) {
        switch (i) {
            case SOLO: return "Jogar sozinho (contra o relógio)";
            case CPU:  return "Jogar contra o computador     < " + Match.AI_LEVEL_NAMES[aiLevel] + " >";
            case TWO:  return "Dois jogadores (mesmo teclado)";
            case HOST: return "Online: criar partida";
            case JOIN:
                String cursor = ((int) (time * 2f)) % 2 == 0 && selected == JOIN ? "_" : " ";
                return "Online: entrar em partida     IP: " + ip + cursor;
            case HAZARDS: return "Gato, túneis e ratoeiras     < " + (hazards ? "Ligado" : "Desligado") + " >";
            default:   return "Sair";
        }
    }

    @Override
    public void render(float delta) {
        time += delta;
        ScreenUtils.clear(0f, 0f, 0f, 1f);
        game.viewport.apply();
        ShapeRenderer s = game.shape;
        s.setProjectionMatrix(game.viewport.getCamera().combined);
        game.batch.setProjectionMatrix(game.viewport.getCamera().combined);

        float w = MazeClient.WORLD_W, h = MazeClient.WORLD_H;
        float itemsTop = h - 180f;
        float spacing = 40f;

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        s.begin(ShapeRenderer.ShapeType.Filled);
        s.setColor(BG);
        s.rect(0, 0, w, h);
        // faixa do item selecionado
        s.setColor(SEL_BG);
        s.rect(w / 2f - 330f, itemsTop - selected * spacing - 28f, 660f, 35f);
        // decoração: ratinho correndo atrás do queijo
        float runX = (time * 140f) % (w + 120f) - 60f;
        renderer.drawCheese(s, runX + 60f, 70f, 1.2f);
        renderer.drawMouse(s, runX, 70f, Maze.RIGHT, MazeRenderer.MOUSE_COLORS[0], 1.6f);
        if (hazards) renderer.drawCatShape(s, runX - 90f, 70f, Maze.RIGHT, 1.6f);
        renderer.drawMouse(s, w / 2f - 300f, h - 75f, Maze.RIGHT, MazeRenderer.MOUSE_COLORS[1], 1.8f);
        renderer.drawCheese(s, w / 2f + 300f, h - 75f, 1.8f);
        s.end();

        game.batch.begin();
        game.text("LABIRINTO DO RATINHO", w / 2f, h - 50f, 3f, GOLD, Align.center);
        game.text("Saia do canto superior esquerdo e chegue ao queijo no canto inferior direito.",
            w / 2f, h - 115f, 1.15f, DIM, Align.center);
        game.text("Cuidado: as paredes mudam de lugar durante a corrida!", w / 2f, h - 140f, 1.15f, DIM, Align.center);

        for (int i = 0; i < COUNT; i++) {
            game.text(label(i), w / 2f - 310f, itemsTop - i * spacing, 1.4f,
                i == selected ? Color.WHITE : DIM, Align.left);
        }
        game.text(help[selected], w / 2f, 122f, 1.1f, GOLD, Align.center);
        game.text("Cima/Baixo: escolher    Enter: confirmar    M: som " + (game.sfx.isEnabled() ? "ligado" : "desligado")
            + "    T: tema " + Theme.choiceName(game.themeChoice).replace(" (muda a cada rodada)", "")
            + "    Esc: sair", w / 2f, 24f, 1f, DIM, Align.center);
        game.batch.end();
    }
}
