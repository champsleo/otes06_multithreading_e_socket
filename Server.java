import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;

public class Server {
    private static final int DEFAULT_PORT = 12345;
    private static final String LOG_FILE = "connections.log";
    private static final ConcurrentHashMap<String, ClientHandler> clients = new ConcurrentHashMap<>();
    private static final Object logLock = new Object();

    public static void main(String[] args) throws IOException {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : DEFAULT_PORT;
        ServerSocket serverSocket = new ServerSocket(port);
        System.out.println("Servidor iniciado na porta " + port);
        System.out.println("Log de conexoes: " + new File(LOG_FILE).getAbsolutePath());

        while (true) {
            Socket socket = serverSocket.accept();
            ClientHandler handler = new ClientHandler(socket);
            Thread t = new Thread(handler);
            t.start();
        }
    }

    static void logConnection(String ip) {
        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        String line = "IP: " + ip + " - Conectado em: " + timestamp;
        synchronized (logLock) {
            try (FileWriter fw = new FileWriter(LOG_FILE, true);
                 PrintWriter pw = new PrintWriter(fw)) {
                pw.println(line);
            } catch (IOException e) {
                System.out.println("Erro ao gravar log: " + e.getMessage());
            }
        }
        System.out.println(line);
    }

    static boolean registerClient(String username, ClientHandler handler) {
        return clients.putIfAbsent(username, handler) == null;
    }

    static void removeClient(String username) {
        if (username != null) {
            clients.remove(username);
        }
    }

    static ClientHandler getClient(String username) {
        return clients.get(username);
    }

    static Set<String> listUsers() {
        return clients.keySet();
    }
}
