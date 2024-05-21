import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.logging.Logger;

public class Worker implements Runnable {
    private static final int SERVER_PORT = 1337;
    private final Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private static final Logger LOG = Logger.getLogger(Worker.class.getName());
    private String password = "mdp";

    public Worker(Socket socket) {
        this.socket = socket;
        try {
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);
        } catch (IOException e) {
            LOG.warning("Erreur lors de la création du worker: " + e.getMessage());
        }
    }

    @Override
    public void run() {
        try {
            String message;
            while ((message = in.readLine()) != null) {
                System.out.println("Message reçu du serveur: " + message);
                communication(message);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void communication(final String message) {
        if (message.equals("WHO_ARE_YOU_?")) {
            sendMessageToServer("ITS_ME");
        } else if (message.equals("GIMME_PASSWORD")) {
            sendMessageToServer(this.password);
        }
    }

    public void sendMessageToServer(String message) {
        System.out.println("Envoi du message au serveur: " + message);
        out.println(message);
    }

    public String receiveMessageFromWorker() throws IOException {
        return in.readLine();
    }

    public void closeConnection() {
        try {
            socket.close();
        } catch (IOException e) {
            LOG.warning("Erreur lors de la fermeture de la connexion: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        try {
            Socket socket = new Socket("localhost", SERVER_PORT);
            Worker worker = new Worker(socket);
            new Thread(worker).start();
        } catch (IOException e) {
            LOG.warning("Erreur lors de la création du socket: " + e.getMessage());
        }
    }
}
