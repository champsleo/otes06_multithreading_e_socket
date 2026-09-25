import java.io.*;
import java.net.*;

public class ClientHandler implements Runnable {
    private final Socket socket;
    private DataInputStream in;
    private DataOutputStream out;
    private String username;

    public ClientHandler(Socket socket) {
        this.socket = socket;
    }

    public DataOutputStream getOutputStream() {
        return out;
    }

    @Override
    public void run() {
        try {
            in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
            out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));

            String ip = socket.getInetAddress().getHostAddress();
            Server.logConnection(ip);

            while (true) {
                String type = in.readUTF();
                if (type.equals("REGISTER")) {
                    String name = in.readUTF();
                    if (name == null || name.trim().isEmpty()) {
                        sendError("Nome de usuario invalido.");
                        continue;
                    }
                    if (Server.registerClient(name, this)) {
                        this.username = name;
                        out.writeUTF("OK");
                        out.writeUTF("Registrado como " + name);
                        out.flush();
                        System.out.println("Cliente registrado: " + name + " (" + ip + ")");
                        break;
                    } else {
                        sendError("Nome de usuario ja esta em uso.");
                    }
                } else {
                    sendError("Voce precisa se registrar primeiro (REGISTER).");
                }
            }

            boolean running = true;
            while (running) {
                String type = in.readUTF();
                switch (type) {
                    case "USERS":
                        handleUsers();
                        break;
                    case "MSG":
                        handleMessage();
                        break;
                    case "FILE":
                        handleFile();
                        break;
                    case "QUIT":
                        running = false;
                        break;
                    default:
                        sendError("Comando desconhecido: " + type);
                }
            }

        } catch (EOFException | SocketException e) {
        } catch (IOException e) {
            System.out.println("Erro na conexao com " + username + ": " + e.getMessage());
        } finally {
            cleanup();
        }
    }

    private void handleUsers() throws IOException {
        StringBuilder sb = new StringBuilder();
        for (String u : Server.listUsers()) {
            if (!u.equals(username)) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(u);
            }
        }
        out.writeUTF("USERLIST");
        out.writeUTF(sb.toString());
        out.flush();
    }

    private void handleMessage() throws IOException {
        String dest = in.readUTF();
        String message = in.readUTF();
        ClientHandler destHandler = Server.getClient(dest);
        if (destHandler == null) {
            sendError("Usuario '" + dest + "' nao encontrado ou desconectado.");
            return;
        }
        synchronized (destHandler) {
            DataOutputStream destOut = destHandler.getOutputStream();
            destOut.writeUTF("MSG");
            destOut.writeUTF(username);
            destOut.writeUTF(message);
            destOut.flush();
        }
    }

    private void handleFile() throws IOException {
        String dest = in.readUTF();
        String filename = in.readUTF();
        long size = in.readLong();

        ClientHandler destHandler = Server.getClient(dest);

        if (destHandler == null) {
            skipBytes(size);
            sendError("Usuario '" + dest + "' nao encontrado ou desconectado.");
            return;
        }

        synchronized (destHandler) {
            DataOutputStream destOut = destHandler.getOutputStream();
            destOut.writeUTF("FILE");
            destOut.writeUTF(username);
            destOut.writeUTF(filename);
            destOut.writeLong(size);

            byte[] buffer = new byte[8192];
            long remaining = size;
            while (remaining > 0) {
                int toRead = (int) Math.min(buffer.length, remaining);
                int read = in.read(buffer, 0, toRead);
                if (read == -1) break;
                destOut.write(buffer, 0, read);
                remaining - = read;
            }
            destOut.flush();
        }
        System.out.println("Arquivo '" + filename + "' encaminhado de " + username + " para " + dest);
    }

    private void skipBytes(long size) throws IOException {
        byte[] buffer = new byte[8192];
        long remaining = size;
        while (remaining > 0) {
            int toRead = (int) Math.min(buffer.length, remaining);
            int read = in.read(buffer, 0, toRead);
            if (read == -1) break;
            remaining - = read;
        }
    }

    private void sendError(String msg) throws IOException {
        out.writeUTF("ERROR");
        out.writeUTF(msg);
        out.flush();
    }

    private void cleanup() {
        Server.removeClient(username);
        try {
            socket.close();
        } catch (IOException ignored) {
        }
        if (username != null) {
            System.out.println("Cliente desconectado: " + username);
        }
    }
}