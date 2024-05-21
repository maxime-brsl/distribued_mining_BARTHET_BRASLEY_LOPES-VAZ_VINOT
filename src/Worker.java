import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.logging.Logger;

public class Worker implements Runnable {
    private static final Logger LOG = Logger.getLogger(Worker.class.getName());
    private static final int SERVER_PORT = 1337;

    private final Socket socket;
    private final BufferedReader in;
    private final PrintWriter out;
    private final String password = "mdp";

    private State state = State.WAITING;

    public Worker(Socket socket) throws IOException {
        this.socket = socket;
        this.in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        this.out = new PrintWriter(socket.getOutputStream(), true);
    }

    @Override
    public void run() {
        try {
            String message;
            while ((message = in.readLine()) != null) {
                System.out.println("Message received : " + message);
                handleMessage(message);
            }
        } catch (IOException e) {
            LOG.severe("Erreur lors de la communication avec le serveur : " + e.getMessage());
        } finally {
            closeConnection();
        }
    }

    private void handleMessage(final String message) {
        switch (message) {
            case Messages.WHO_ARE_YOU -> sendMessageToServer(Messages.IDENTIFICATION);
            case Messages.GIMME_PASSWORD -> sendMessageToServer("PASSWD " + password);
            case Messages.HELLO_YOU -> {
                state = State.READY;
                sendMessageToServer(Messages.READY);
            }
            case Messages.YOU_DONT_FOOL_ME -> {
                state = State.DISCONNECTED;
                closeConnection();
            }
            default -> LOG.warning("Commande inconnue : " + message);
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
            if (!socket.isClosed()) {
                socket.close();
                LOG.info("Connexion fermée.");
            }
        } catch (IOException e) {
            LOG.warning("Erreur lors de la fermeture de la connexion: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        try  {
            Socket socket = new Socket("localhost", SERVER_PORT);
            Worker worker = new Worker(socket);
            new Thread(worker).start();
        } catch (IOException e) {
            LOG.warning("Erreur lors de la création du socket: " + e.getMessage());
        }
    }

    private enum State {
        WAITING, READY, DISCONNECTED
    }
}
