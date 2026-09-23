# Trabalho 1 - multithreading e socket

**Disciplina:** Programação para dispositivos móveis

**Trabalho:** Implementação de aplicativo cliente-servidor para troca de mensagens de texto e arquivos utilizando sockets TCP

**Alunos:** Enzo Holtz, João Vytor Dutra, Leonardo Deschamps e Nivea Maria

---

## 1. Introdução

Este trabalho implementa um sistema de mensagens instantâneas no modelo
cliente-servidor, utilizando sockets TCP (`java.net.Socket` e
`java.net.ServerSocket`). O sistema é composto por dois programas
independentes:

- **Servidor** (`Server.java` + `ClientHandler.java`): aceita conexões de
  múltiplos clientes simultaneamente e é responsável exclusivamente por
  **rotear** mensagens e arquivos entre eles, sem interpretar o conteúdo
  transmitido.
- **Cliente** (`Client.java`): programa executado por cada usuário, responsável
  por se conectar ao servidor, enviar comandos e exibir/gravar o que recebe.

---

## 2. Arquitetura

### 2.1 Visão geral

```
Cliente A  ---TCP--->  Servidor  ---TCP--->  Cliente B
(Alice)                (roteador)             (Bob)
```

O servidor mantém uma tabela em memória (`ConcurrentHashMap<String, ClientHandler>`)
associando cada **nome de usuário** ao objeto responsável por sua conexão. Ao
receber uma mensagem ou arquivo, o servidor consulta essa tabela pelo nome do
destinatário e repassa os dados diretamente para o socket daquele cliente,
sem armazenar ou processar o conteúdo da mensagem.

### 2.2 Concorrência

Cada cliente conectado é atendido por uma *thread* dedicada no servidor
(`ClientHandler`, que implementa `Runnable`). Isso permite que múltiplos
clientes troquem mensagens simultaneamente sem bloquear uns aos outros. O
acesso à tabela de clientes conectados é *thread-safe* (`ConcurrentHashMap`),
e o encaminhamento de dados para um mesmo destinatário é sincronizado
(`synchronized`) para evitar que duas mensagens de remetentes diferentes se
intercalem no mesmo socket de saída.

No lado do cliente, duas *threads* atuam em paralelo:

1. A *thread* principal lê comandos digitados pelo usuário no teclado e os
   envia ao servidor.
2. Uma *thread* secundária (`ServerListener`) fica bloqueada aguardando dados
   do servidor (mensagens, arquivos ou respostas a comandos) e os exibe/grava
   assim que chegam — permitindo receber mensagens a qualquer momento, mesmo
   enquanto o usuário está digitando.

### 2.3 Protocolo de aplicação

Um socket TCP fornece apenas um fluxo contínuo de bytes, sem noção de onde
uma mensagem termina e outra começa. Por isso, foi definido um protocolo de
aplicação simples sobre `DataInputStream`/`DataOutputStream`, usando:

- `writeUTF(String)` / `readUTF()`: grava/lê uma string precedida por seu
  tamanho em bytes, delimitando automaticamente cada campo textual;
- `writeLong(long)` / `readLong()`: grava/lê o tamanho de um arquivo em
  bytes, antes do envio do conteúdo binário.

Cada mensagem trocada começa com um **tipo de operação** (uma string),
seguido dos campos específicos daquela operação:

| Tipo (cliente → servidor) | Campos seguintes | Finalidade |
|---|---|---|
| `REGISTER` | `nomeDeUsuario` | Registra o nome do cliente no servidor |
| `USERS` | — | Solicita a lista de usuários conectados |
| `MSG` | `destinatario`, `mensagem` | Envia mensagem de texto |
| `FILE` | `destinatario`, `nomeArquivo`, `tamanho` (long) + bytes do arquivo | Envia um arquivo |
| `QUIT` | — | Encerra a conexão |

| Tipo (servidor → cliente) | Campos seguintes | Finalidade |
|---|---|---|
| `OK` | `mensagem` | Confirmação de registro |
| `USERLIST` | `lista` (separada por vírgula) | Resposta ao comando `/users` |
| `MSG` | `remetente`, `mensagem` | Mensagem recebida de outro cliente |
| `FILE` | `remetente`, `nomeArquivo`, `tamanho` (long) + bytes | Arquivo recebido de outro cliente |
| `ERROR` | `motivo` | Erro (ex.: usuário inexistente, nome já em uso) |

Essa abordagem evita o problema clássico de misturar leitura de texto
(orientada a linhas, como `BufferedReader`) com leitura de dados binários no
mesmo fluxo, que corromperia a transferência de arquivos caso o *buffer* de
leitura de texto consumisse bytes além do delimitador esperado.

### 2.4 Log de conexões

O servidor grava, em `connections.log`, uma linha para cada conexão TCP
aceita (evento `accept()`), contendo o endereço IP de origem e o instante
(data e hora) da conexão, no formato:

```
IP: 192.168.1.15 - Conectado em: 2026-09-23 17:56:54
```

O registro ocorre no momento em que a conexão é aceita, independentemente do
sucesso posterior do registro do nome de usuário, satisfazendo o requisito de
manter um histórico de todos os clientes que se conectaram ao servidor.

---

## 3. Requisitos atendidos

| Requisito do enunciado | Onde está implementado |
|---|---|
| Comunicação entre clientes por meio do servidor | `ClientHandler.handleMessage()` / `handleFile()` |
| Mensagem direcionada a um único destinatário | Campo `destinatario` no protocolo `MSG`/`FILE` |
| Comando `/users` lista clientes conectados | `Client` (parsing) + `ClientHandler.handleUsers()` |
| Servidor apenas roteia, sem processar o conteúdo | `ClientHandler` repassa bytes sem interpretá-los |
| Envio/recebimento de texto e arquivos | Comandos `MSG` e `FILE` no protocolo |
| Comando `/sair` a qualquer momento | `Client` envia `QUIT`; `ClientHandler` encerra o laço |
| Log de conexões com IP e data/hora | `Server.logConnection()` |
| `/send message <destinatario> <mensagem>` | `Client.handleSendMessage()` |
| Mensagem exibida no `System.out` do destinatário com nome do remetente | `ServerListener.handleIncomingMessage()` |
| `/send file <destinatario> <caminho>` | `Client.handleSendFile()` |
| Leitura dos bytes do arquivo e envio via socket | `Client.handleSendFile()` (via `FileInputStream`) |
| Gravação com nome original no diretório corrente do destinatário | `ServerListener.handleIncomingFile()` (via `FileOutputStream`) |

---

## 4. Requisitos para execução

- **Java Development Kit (JDK)** versão 17 ou superior instalado em todas as
  máquinas (servidor e clientes).
- Todas as máquinas devem estar na mesma rede (ou o servidor deve ser
  acessível pela rede/internet a partir das máquinas clientes).
- Porta TCP **12345** (padrão) liberada no firewall da máquina do servidor.

Verificar a instalação do Java:
```bash
java -version
javac -version
```

---

## 5. Compilação

Os quatro arquivos-fonte devem estar na mesma pasta:

```
Server.java
ClientHandler.java
Client.java
```

Na pasta do projeto, compile todos os arquivos de uma vez:

```bash
javac *.java
```

Isso gera os arquivos `Server.class`, `ClientHandler.class` e `Client.class`
(e uma classe interna `Client$ServerListener.class`).

---

## 6. Execução

### 6.1 Servidor

Em uma máquina que atuará como servidor, execute:

```bash
java Server [porta]
```

O parâmetro `porta` é opcional (padrão: `12345`). Exemplo:

```bash
java Server 12345
```

Saída esperada:
```
Servidor iniciado na porta 12345
Log de conexoes: /caminho/atual/connections.log
```

O terminal deve permanecer aberto enquanto o servidor estiver em operação.
Para encerrar, utilize `Ctrl+C`.

### 6.2 Cliente

Em cada máquina/terminal que representará um usuário, execute:

```bash
java Client <ip_do_servidor> <porta>
```

Exemplo, conectando a um servidor no IP `192.168.1.10`:

```bash
java Client 192.168.1.10 12345
```

Para testes na mesma máquina do servidor, utilize `127.0.0.1` como IP.

Ao iniciar, o cliente solicita um nome de usuário (deve ser único entre os
clientes conectados no momento):

```
Digite seu nome de usuario: alice
Registrado como alice
```

### 6.3 Comandos disponíveis no cliente

| Comando | Descrição |
|---|---|
| `/users` | Lista os demais usuários conectados no momento |
| `/send message <destinatario> <mensagem>` | Envia uma mensagem de texto a um destinatário específico |
| `/send file <destinatario> <caminho_do_arquivo>` | Envia um arquivo a um destinatário específico |
| `/sair` | Encerra a conexão com o servidor |

Exemplo de sessão (cliente `alice`, destinatário `bob`):

```
/users
/send message bob Ola Bob, tudo bem?
/send file bob relatorio.pdf
/sair
```

No terminal do destinatário (`bob`), a mensagem recebida é exibida como:

```
alice: Ola Bob, tudo bem?
```

E o arquivo recebido é salvo automaticamente, com o nome original, no
diretório em que o processo `java Client ...` de `bob` foi executado:

```
Arquivo recebido de alice: relatorio.pdf (245678 bytes) salvo em /caminho/atual/relatorio.pdf
```

---

## 7. Validação do funcionamento

Para uma verificação rápida de que a implementação está correta, é possível
reproduzir o seguinte roteiro de teste em três terminais na mesma máquina:

1. **Terminal 1:** `java Server 12345`
2. **Terminal 2:** `java Client 127.0.0.1 12345` → registrar como `alice`
3. **Terminal 3:** `java Client 127.0.0.1 12345` → registrar como `bob`
4. No terminal de `alice`: `/users` (deve listar `bob`)
5. No terminal de `alice`: `/send message bob Teste de mensagem`
   (deve aparecer no terminal de `bob`)
6. No terminal de `alice`: `/send file bob <caminho de um arquivo existente>`
   (o arquivo deve ser criado na pasta de execução de `bob`, com o mesmo
   nome e conteúdo do original)
7. Verificar o arquivo `connections.log`, gerado na pasta de execução do
   servidor, contendo o IP e o instante de cada conexão aceita.

---

## 8. Limitações conhecidas

- O protocolo não implementa criptografia; as mensagens e arquivos trafegam
  em texto/bytes não cifrados, adequado ao escopo do trabalho.
- Não há persistência de mensagens: um cliente só recebe mensagens enviadas
  enquanto está conectado.
- O nome de usuário deve ser único apenas entre clientes conectados
  simultaneamente; não há autenticação ou cadastro persistente de usuários.

---

## 9. Estrutura dos arquivos entregues

```
Server.java          - Ponto de entrada do servidor; mantém a tabela de
                        clientes conectados e o log de conexões
ClientHandler.java   - Uma instância por cliente conectado; implementa o
                        roteamento de mensagens e arquivos
Client.java          - Ponto de entrada do cliente; interpreta comandos do
                        usuário e exibe/grava dados recebidos do servidor
README.md            - Este documento
```