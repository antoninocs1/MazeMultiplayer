package com.meuprojeto.maze.server;

import com.meuprojeto.maze.Match;
import com.meuprojeto.maze.Mouse;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Servidor autoritativo do Labirinto do Ratinho.
 *
 * - Aceita de 2 a 4 jogadores (quem entra no meio de uma corrida espera a próxima rodada).
 * - Roda a simulação ({@link Match}) a 60 ticks/s e envia o snapshot completo para todos.
 * - Pode rodar sozinho ({@link #main}) ou embutido no cliente (opção "Criar partida").
 *
 * Protocolo (DataInput/DataOutputStream, TCP):
 *   Servidor -> Cliente (ao conectar): int MAGIC, int playerId  (-1 = servidor cheio)
 *   Servidor -> Cliente (cada tick):   snapshot de {@link Match#write}
 *   Cliente  -> Servidor (ao mudar):   byte direção (-1, 0..3)
 */
public class GameServer {

    public static final int DEFAULT_PORT = 12345;
    /** Identifica o protocolo; muda quando o formato do snapshot muda (versões diferentes não se misturam). */
    public static final int MAGIC = 0x4D415A34; // "MAZ4"
    private static final int TICKS_PER_SECOND = 60;

    private final int port;
    private final Match match = new Match(2);
    private final List<ClientHandler> clients = new CopyOnWriteArrayList<ClientHandler>();
    private ServerSocket serverSocket;
    private volatile boolean running;

    public GameServer(int port) {
        this(port, true);
    }

    /** @param hazards liga o gato e os túneis. */
    public GameServer(int port, boolean hazards) {
        this.port = port;
        match.hazards = hazards;
    }

    public void start() throws IOException {
        serverSocket = new ServerSocket(port);
        running = true;

        Thread accept = new Thread(this::acceptLoop, "ServerAccept");
        accept.setDaemon(true);
        accept.start();

        Thread loop = new Thread(this::gameLoop, "ServerGameLoop");
        loop.setDaemon(true);
        loop.start();

        log("Labirinto iniciado na porta " + port + ". Endereços: " + localAddresses());
    }

    public void stop() {
        running = false;
        try { if (serverSocket != null) serverSocket.close(); } catch (IOException ignored) {}
        for (ClientHandler c : clients) c.close();
        clients.clear();
    }

    private void acceptLoop() {
        while (running) {
            try {
                Socket socket = serverSocket.accept();
                String address = describeAddress(socket);
                int id;
                synchronized (match) {
                    id = freeId();
                    if (id >= 0) match.addMouse(id, "Jogador " + (id + 1), false, 0).address = address;
                }
                if (id < 0) {
                    rejectFull(socket);
                    continue;
                }
                ClientHandler handler = new ClientHandler(this, socket, id);
                handler.sendHello();
                clients.add(handler);
                handler.start();
                log("Jogador " + (id + 1) + " conectado: " + address);
            } catch (IOException e) {
                if (running) log("Erro ao aceitar conexão: " + e.getMessage());
            }
        }
    }

    /**
     * IP de quem conectou. Quem joga no próprio computador do servidor (o anfitrião) chega
     * como 127.0.0.1; nesse caso mostramos o IP de rede desta máquina, que é o que os outros veem.
     */
    private static String describeAddress(Socket socket) {
        InetAddress a = socket.getInetAddress();
        if (a.isLoopbackAddress() || a.isAnyLocalAddress()) {
            return localAddresses().get(0) + " (anfitrião)";
        }
        String ip = a.getHostAddress();
        if (ip.startsWith("::ffff:")) ip = ip.substring(7);
        return ip;
    }

    private void rejectFull(Socket socket) {
        try {
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            out.writeInt(MAGIC);
            out.writeInt(-1);
            out.flush();
        } catch (IOException ignored) {
        } finally {
            try { socket.close(); } catch (IOException ignored) {}
        }
        log("Conexão recusada: servidor cheio.");
    }

    private int freeId() {
        for (int id = 0; id < Match.MAX_MICE; id++) {
            if (match.getMouse(id) == null) return id;
        }
        return -1;
    }

    private void gameLoop() {
        final double nsPerTick = 1_000_000_000.0 / TICKS_PER_SECOND;
        final float dt = 1f / TICKS_PER_SECOND;
        long last = System.nanoTime();
        double pending = 0;
        Match.Phase lastPhase = null;
        ByteArrayOutputStream buffer = new ByteArrayOutputStream(1024);

        while (running) {
            long now = System.nanoTime();
            pending += (now - last) / nsPerTick;
            last = now;

            if (pending >= 1.0) {
                byte[] snapshot;
                synchronized (match) {
                    while (pending >= 1.0) {
                        match.update(dt);
                        pending -= 1.0;
                    }
                    if (match.phase != lastPhase) {
                        logPhase(match);
                        lastPhase = match.phase;
                    }
                    buffer.reset();
                    try {
                        match.write(new DataOutputStream(buffer));
                    } catch (IOException ignored) {
                        // ByteArrayOutputStream não lança IOException
                    }
                    snapshot = buffer.toByteArray();
                }
                for (ClientHandler c : clients) c.send(snapshot);
            }

            try { Thread.sleep(1); } catch (InterruptedException e) { return; }
        }
    }

    private void logPhase(Match m) {
        switch (m.phase) {
            case WAITING:
                log("Aguardando jogadores (" + m.mice.size() + "/" + m.getMinPlayers() + ")...");
                break;
            case COUNTDOWN:
                log("Rodada " + m.round + " vai começar com " + m.mice.size() + " jogador(es).");
                break;
            case RUNNING:
                log("Rodada " + m.round + ": valendo!");
                break;
            case FINISHED:
                Mouse w = m.getMouse(m.winnerId);
                if (w != null) {
                    log(String.format("Rodada %d: %s venceu em %.1f s (total de vitórias: %d).",
                        m.round, w.name, w.finishTime, w.wins));
                }
                break;
        }
    }

    void onInput(int playerId, int dir) {
        synchronized (match) {
            match.setInput(playerId, dir);
        }
    }

    void onDisconnect(ClientHandler c) {
        if (clients.remove(c)) {
            synchronized (match) {
                match.removeMouse(c.getPlayerId());
            }
            log("Jogador " + (c.getPlayerId() + 1) + " desconectado.");
        }
    }

    private static void log(String msg) {
        System.out.println("[Servidor] " + msg);
    }

    private static final String[] VIRTUAL_HINTS = {
        "virtualbox", "vmware", "hyper-v", "vethernet", "wsl", "docker", "loopback",
        "tap-", "tun", "vpn", "bluetooth", "pseudo", "zerotier", "hamachi", "tailscale",
    };

    /**
     * Endereços IPv4 desta máquina (para informar aos amigos), do mais provável ao menos provável:
     * primeiro os de rede local (192.168.x.x, 10.x.x.x, 172.16-31.x.x) de placas reais (Wi-Fi/cabo),
     * depois os de adaptadores virtuais (VirtualBox, Hyper-V, VPN...).
     */
    public static List<String> localAddresses() {
        List<String> real = new ArrayList<String>();
        List<String> other = new ArrayList<String>();
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback() || ni.isVirtual()) continue;
                String name = (ni.getDisplayName() + " " + ni.getName()).toLowerCase();
                boolean virtual = false;
                for (String hint : VIRTUAL_HINTS) if (name.contains(hint)) virtual = true;
                for (InetAddress a : Collections.list(ni.getInetAddresses())) {
                    if (!(a instanceof Inet4Address) || a.isLinkLocalAddress()) continue;
                    String ip = a.getHostAddress();
                    if (!virtual && a.isSiteLocalAddress()) {
                        if (ip.startsWith("192.168.")) real.add(0, ip); else real.add(ip);
                    } else {
                        other.add(ip);
                    }
                }
            }
        } catch (SocketException ignored) {
        }
        List<String> list = new ArrayList<String>(real);
        list.addAll(other);
        if (list.isEmpty()) list.add("127.0.0.1");
        return list;
    }

    /** Servidor dedicado: {@code java ... GameServer [porta] [sem-gato]}. */
    public static void main(String[] args) throws Exception {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : DEFAULT_PORT;
        boolean hazards = args.length < 2 || !args[1].equalsIgnoreCase("sem-gato");
        new GameServer(port, hazards).start();
        Thread.currentThread().join();
    }
}
