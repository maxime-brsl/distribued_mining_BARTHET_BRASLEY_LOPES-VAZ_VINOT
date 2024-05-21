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
    private String state = "WAITING";

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
                System.out.println("Message received : " + message);
                communication(message);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void communication(final String message) {
        switch (message) {
            case "WHO_ARE_YOU_?" -> sendMessageToServer("ITS_ME");
            case "GIMME_PASSWORD" -> sendMessageToServer("PASSWD " + this.password);
            case "HELLO_YOU" -> {
                this.state = "READY";
                sendMessageToServer("READY");
            }
            case "YOU_DONT_FOOL_ME" -> closeConnection();
            default -> System.out.println("Commande inconnue : " + message);
        }
    }

    public void sendMessageToServer(String message) {
        out.println(message);
        System.out.println("Message sent : " + message);
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
