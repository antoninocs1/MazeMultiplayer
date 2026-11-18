package com.meuprojeto.maze.server;

import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;

/**
 * Atende UM jogador conectado.
 *   - Thread de leitura: recebe a direção desejada (1 byte: -1 parado, 0 cima, 1 direita, 2 baixo, 3 esquerda).
 *   - {@link #send(byte[])}: chamado pelo loop do servidor para enviar o snapshot.
 */
public class ClientHandler extends Thread {
    private final GameServer server;
    private final Socket socket;
    private final int playerId;
    private final DataInputStream in;
    private final DataOutputStream out;

    public ClientHandler(GameServer server, Socket socket, int playerId) throws IOException {
        super("Client-" + playerId);
        setDaemon(true);
        this.server = server;
        this.socket = socket;
        this.playerId = playerId;
        socket.setTcpNoDelay(true);
        this.in = new DataInputStream(socket.getInputStream());
        this.out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
    }

    public int getPlayerId() {
        return playerId;
    }

    /** Primeira mensagem: identificador do protocolo e o ID do jogador. */
    public synchronized void sendHello() throws IOException {
        out.writeInt(GameServer.MAGIC);
        out.writeInt(playerId);
        out.flush();
    }

    @Override
    public void run() {
        try {
            while (true) {
                int dir = in.readByte();
                server.onInput(playerId, dir);
            }
        } catch (IOException e) {
            // desconectou
        } finally {
            close();
            server.onDisconnect(this);
        }
    }

    /** Envia um snapshot já serializado. Retorna false se a conexão caiu. */
    public synchronized boolean send(byte[] data) {
        try {
            out.write(data);
            out.flush();
            return true;
        } catch (IOException e) {
            close();
            return false;
        }
    }

    public void close() {
        try { socket.close(); } catch (IOException ignored) {}
    }
}
