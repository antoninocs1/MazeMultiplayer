# Labirinto do Ratinho — Multiplayer (LibGDX + Sockets TCP)

Jogo de **labirinto dinâmico**: cada ratinho começa no **canto superior esquerdo** e precisa chegar
ao **queijo na linha de chegada**, no **canto inferior direito**. Durante a corrida, **as paredes
mudam de lugar** a cada 4 segundos (destacadas em laranja), mas o labirinto sempre continua com
caminho até a chegada. Cada rodada gera um labirinto novo.

## Modos de jogo (menu inicial)

| Modo | Descrição |
|------|-----------|
| **Jogar sozinho** | Contra o relógio. O melhor tempo fica salvo. |
| **Jogar contra o computador** | Corrida contra um ratinho controlado pela IA. Dificuldade Fácil / Médio / Difícil (setas esquerda/direita no menu). |
| **Dois jogadores (mesmo teclado)** | Jogador 1: WASD, Jogador 2: setas. |
| **Online: criar partida** | Abre o servidor dentro do próprio jogo (porta 12345) e mostra o seu IP. |
| **Online: entrar em partida** | Digite o IP de quem criou a partida (ex.: `192.168.0.10` ou `192.168.0.10:12345`). |

Online aceita de **2 a 4 jogadores**. Quem entrar no meio de uma corrida espera a próxima rodada.
O primeiro a chegar ao queijo vence a rodada; o placar de vitórias aparece no topo da tela.

### Controles

| Ação | Teclas |
|------|--------|
| Mover | Setas ou WASD |
| Voltar ao menu | Esc |

## Como executar

Requisitos: JDK 17 ou superior.

```bash
# Jogo (cliente)
gradlew.bat lwjgl3:run          # Windows
./gradlew lwjgl3:run            # Linux/macOS

# Servidor dedicado, sem janela (opcional; o menu já consegue criar a partida)
gradlew.bat core:runServer
gradlew.bat core:runServer --args="12345"

# JAR executável para levar para outra máquina
gradlew.bat lwjgl3:jar          # gera lwjgl3/build/libs/maze-multiplayer-1.0.0.jar
java -jar maze-multiplayer-1.0.0.jar
```

No IntelliJ: rode `Lwjgl3Launcher.main()` (cliente) ou `GameServer.main()` (servidor dedicado).
Para abrir duas janelas no mesmo PC: *Run → Edit Configurations → Allow multiple instances*.

### Jogando em rede

1. Na máquina do anfitrião, escolha **Online: criar partida**. A tela mostra o IP para os amigos.
2. Libere a porta 12345 no firewall do anfitrião (PowerShell como Administrador, uma vez só):
   ```powershell
   New-NetFirewallRule -DisplayName "Labirinto 12345" -Direction Inbound -Protocol TCP -LocalPort 12345 -Action Allow
   ```
3. Nas outras máquinas, escolha **Online: entrar em partida**, digite o IP e pressione Enter.

Para jogar pela internet é preciso redirecionar a porta 12345 no roteador do anfitrião (port forwarding).

## Estrutura

```
core/src/main/java/com/meuprojeto/maze/
├── Maze.java             Grade, geração (backtracking) e mutação dinâmica sempre conectada
├── Mouse.java            Estado de um ratinho (humano ou IA)
├── Match.java            Regras: rodadas, contagem, movimento, IA, vencedor, serialização
├── MazeClient.java       Classe Game principal (recursos compartilhados)
├── MenuScreen.java       Menu de modos
├── LocalGameScreen.java  Sozinho / contra o computador / 2 jogadores no mesmo teclado
├── OnlineGameScreen.java Cliente TCP (e anfitrião com servidor embutido)
├── MazeRenderer.java     Desenho do labirinto, ratinhos, queijo, HUD e mensagens
├── DirInput.java         Leitura das teclas de direção
└── server/
    ├── GameServer.java   Servidor autoritativo (60 ticks/s)
    └── ClientHandler.java Uma conexão de jogador
lwjgl3/src/main/java/com/meuprojeto/maze/lwjgl3/Lwjgl3Launcher.java
```

`Maze`, `Mouse`, `Match` e o pacote `server` não usam LibGDX, então o servidor roda sem janela.

## Protocolo de rede (TCP, DataInput/DataOutputStream)

```text
AO CONECTAR   Servidor -> Cliente: int MAGIC (0x4D415A45 "MAZE"), int playerId (-1 = partida cheia)
QUANDO MUDA   Cliente  -> Servidor: byte direção (-1 parado, 0 cima, 1 direita, 2 baixo, 3 esquerda)
CADA TICK     Servidor -> Cliente: snapshot completo (Match.write):
              fase, timers, rodada, vencedor, melhor tempo,
              colunas, linhas, paredes (1 byte por célula),
              lista de ratinhos (id, nome, cor, posição, direção, vitórias...)
```

O servidor é a autoridade: ele move os ratinhos, muda o labirinto e decide o vencedor.
Os clientes só enviam a tecla pressionada e desenham o que recebem.

## Solução de problemas

| Sintoma | Causa provável | Solução |
|---------|----------------|---------|
| "Não foi possível conectar" | IP errado ou anfitrião ainda não criou a partida | Confira o IP mostrado na tela do anfitrião |
| Conecta no mesmo PC mas não em outro | Firewall bloqueando a porta | Libere a porta 12345 (ver acima) |
| "Não foi possível criar a partida" | Porta 12345 já em uso | Feche o outro servidor/jogo que está hospedando |
| "não é um servidor do Labirinto" | Conectou em um servidor antigo (ex.: Pong) | Atualize o servidor |
