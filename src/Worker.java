import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Logger;

public class Worker implements Runnable {
    private final Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private final BlockingQueue<String> queue;
    private static final Logger LOG = Logger.getLogger(Worker.class.getName());
    private String password;

    public Worker(Socket socket) {
        this.socket = socket;
        queue = new LinkedBlockingQueue<>();
        try {
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);
        } catch (IOException e) {
            LOG.warning("Erreur lors de la création du worker: " + e.getMessage());
        }
    }

    public void sendMessage(String message) {
        queue.offer(message);
    }

    @Override
    public void run() {
        try {
            out.println("WHO_ARE_YOU_?");
            String inputLine;
            while ((inputLine = in.readLine()) != null) {
                String[] parts = inputLine.split(" ");
                String command = parts[0];
                switch (command) {
                    case "ITS_ME":
                        out.println("GIMME_PASSWORD");
                        break;
                    case "PASSWD":
                        if (parts.length > 1) {
                            password = parts[1];
                            out.println("OK");
                            out.println("HELLO_YOU");
                        } else {
                            out.println("YOU_DONT_FOOL_ME");
                            socket.close();
                            return;
                        }
                        break;
                    case "READY":
                        out.println("PROGRESS");
                        break;
                    case "FOUND":
                        if (parts.length > 2) {
                            String hash = parts[1];
                            String nonce = parts[2];
                            // Vérifier la validité du hash et du nonce
                            out.println("SOLVED");
                        }
                        break;
                    case "PROGRESS":
                        // Simulation de la progression, renvoyer TESTING avec un nonce aléatoire pour l'instant
                        out.println("TESTING " + Integer.toHexString((int) (Math.random() * 1000)));
                        break;
                    default:
                        // Ne rien faire pour les autres commandes
                        break;
                }
                if ("WHO_ARE_YOU_?".equals(command)) {
                    out.println("ITS_ME");
                } else if ("GIMME_PASSWORD".equals(command)) {
                    out.println("PASSWD azerty");
                }
            }
            socket.close();
        } catch (IOException e) {
            LOG.warning("Erreur lors de l'exécution du worker: " + e.getMessage());
        }
    }
}
