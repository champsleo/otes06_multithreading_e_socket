import java.io.*;
import java.net.*;
import java.util.Scanner;

public class Client {
    private static DataInputStream in;
    private static DataOutputStream out;
    private static String username;
    private static volatile boolean running = true;

    public static void main(String[] args) throws IOException {
        Scanner scanner = new Scanner(System.in);

        String host = args.length > 0 ? args[0] : "127.0.0.1";
        int port = args.length > 1 ? Integer.parseInt(args[1]) : 12345;

        Socket socket = new Socket(host, port);
        in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
        out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));

        while (true) {
            System.out.print("Digite seu nome de usuario: ");
            username = scanner.nextLine().trim();
            out.writeUTF("REGISTER");
            out.writeUTF(username);
            out.flush();

            String resp = in.readUTF();
            if (resp.equals("OK")) {
                String info = in.readUTF();
                System.out.println(info);
                break;
            } else {
                String err = in.readUTF();
                System.out.println("Erro: " + err);
            }
        }

        Thread listener = new Thread(new ServerListener(socket));
        listener.setDaemon(true);
        listener.start();

        printHelp();

        while (running) {
            if (!scanner.hasNextLine()) break;
            String line = scanner.nextLine().trim();
            if (line.isEmpty()) continue;

            try {
                if (line.equals("/sair")) {
                    out.writeUTF("QUIT");
                    out.flush();
                    running = false;
                } else if (line.equals("/users")) {
                    out.writeUTF("USERS");
                    out.flush();
                } else if (line.startsWith("/send message ")) {
                    handleSendMessage(line);
                } else if (line.startsWith("/send file ")) {
                    handleSendFile(line);
                } else {
                    System.out.println("Comando nao reconhecido. Use /users, /send message, /send file ou /sair.");
                }
            } catch (IOException e) {
                System.out.println("Erro ao enviar dados: " + e.getMessage());
                running = false;
            }
        }

        socket.close();
        System.out.println("Desconectado.");
        System.exit(0);
    }

    private static void handleSendMessage(String line) throws IOException {
        String rest = line.substring("/send message ".length());
        int sp = rest.indexOf(' ');
        if (sp == -1) {
            System.out.println("Uso: /send message <destinatario> <mensagem>");
            return;
        }
        String dest = rest.substring(0, sp);
        String message = rest.substring(sp + 1);
        out.writeUTF("MSG");
        out.writeUTF(dest);
        out.writeUTF(message);
        out.flush();
    }

    private static void handleSendFile(String line) throws IOException {
        String rest = line.substring("/send file ".length());
        int sp = rest.indexOf(' ');
        if (sp == -1) {
            System.out.println("Uso: /send file <destinatario> <caminho do arquivo>");
            return;
        }
        String dest = rest.substring(0, sp);
        String path = rest.substring(sp + 1);

        File file = new File(path);
        if (!file.exists() || !file.isFile()) {
            System.out.println("Arquivo nao encontrado: " + path);
            return;
        }

        String filename = file.getName();
        long size = file.length();

        out.writeUTF("FILE");
        out.writeUTF(dest);
        out.writeUTF(filename);
        out.writeLong(size);

        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = fis.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
        }
        out.flush();
        System.out.println("Arquivo '" + filename + "' enviado para " + dest + " (" + size + " bytes).");
    }

    private static void printHelp() {
        System.out.println("----------------------------------------------------------");
        System.out.println("Comandos disponiveis:");
        System.out.println("  /users                                  - lista usuarios conectados");
        System.out.println("  /send message <destinatario> <mensagem> - envia mensagem de texto");
        System.out.println("  /send file <destinatario> <caminho>     - envia um arquivo");
        System.out.println("  /sair                                   - encerra a conexao");
        System.out.println("----------------------------------------------------------");
    }

    static class ServerListener implements Runnable {
        private final Socket socket;

        ServerListener(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try {
                while (running) {
                    String type = in.readUTF();
                    switch (type) {
                        case "MSG":
                            handleIncomingMessage();
                            break;
                        case "FILE":
                            handleIncomingFile();
                            break;
                        case "USERLIST":
                            handleUserList();
                            break;
                        case "ERROR":
                            String err = in.readUTF();
                            System.out.println("[Servidor] Erro: " + err);
                            break;
                        case "INFO":
                            String info = in.readUTF();
                            System.out.println("[Servidor] " + info);
                            break;
                        default:
                            break;
                    }
                }
            } catch (EOFException | SocketException e) {
                if (running) {
                    System.out.println("\nConexao com o servidor perdida.");
                    running = false;
                }
            } catch (IOException e) {
                if (running) {
                    System.out.println("\nErro na leitura do servidor: " + e.getMessage());
                    running = false;
                }
            }
        }

        private void handleIncomingMessage() throws IOException {
            String sender = in.readUTF();
            String message = in.readUTF();
            System.out.println(sender + ": " + message);
        }

        private void handleUserList() throws IOException {
            String list = in.readUTF();
            if (list.isEmpty()) {
                System.out.println("Nenhum outro usuario conectado.");
            } else {
                System.out.println("Usuarios conectados: " + list);
            }
        }

        private void handleIncomingFile() throws IOException {
            String sender = in.readUTF();
            String filename = in.readUTF();
            long size = in.readLong();

            File outFile = new File(filename);
            try (FileOutputStream fos = new FileOutputStream(outFile)) {
                byte[] buffer = new byte[8192];
                long remaining = size;
                while (remaining > 0) {
                    int toRead = (int) Math.min(buffer.length, remaining);
                    int read = in.read(buffer, 0, toRead);
                    if (read == -1) break;
                    fos.write(buffer, 0, read);
                    remaining -= read;
                }
            }
            System.out.println("Arquivo recebido de " + sender + ": " + filename
                    + " (" + size + " bytes) salvo em " + outFile.getAbsolutePath());
        }
    }
}