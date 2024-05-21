import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public class Server implements Runnable {
    private ServerSocket serverSocket;
    private List<Worker> workers;
    private static final Logger LOG = Logger.getLogger(Server.class.getName());
    private static final String PASSWORD = "mdp";  // Mot de passe attendu
    private static final String IDENTIFICATION = "ITS_ME";  // Réponse attendue

    public Server(int port) {
        try {
            serverSocket = new ServerSocket(port);
            workers = new ArrayList<>();
        } catch (IOException e) {
            LOG.warning("Erreur lors de la création du serveur: " + e.getMessage());
        }
    }

    public void start() {
        System.out.println("Serveur démarré");
        while (true) {
            try {
                Worker worker = acceptNewWorker();
                initProtocole(worker);
                String receivedIdentification = worker.receiveMessageFromWorker();
                System.out.println("Réponse du worker à WHO_ARE_YOU_?: " + receivedIdentification);
                if (verifyIdentification(receivedIdentification)) {
                    sendMessageToWorker(worker, "GIMME_PASSWORD");
                    String receivedPassword = worker.receiveMessageFromWorker();
                    System.out.println("Mot de passe reçu du worker: " + receivedPassword);
                    if (verifyPassword(receivedPassword)) {
                        System.out.println("Worker authentifié avec succès.");
                    } else {
                        System.out.println("Authentification échouée pour le worker.");
                        worker.closeConnection();
                    }
                } else {
                    System.out.println("Échec de l'identification du worker.");
                    worker.closeConnection();
                }
            } catch (IOException e) {
                LOG.warning("Erreur lors de la connexion du worker: " + e.getMessage());
            }
        }
    }

    private Worker acceptNewWorker() throws IOException {
        Socket workerSocket = serverSocket.accept();
        System.out.println("Nouveau worker connecté: " + workerSocket);
        Worker worker = new Worker(workerSocket);
        this.workers.add(worker);
        return worker;
    }

    public void initProtocole(final Worker worker) {
        sendMessageToWorker(worker, "WHO_ARE_YOU_?");
    }

    public void sendMessageToWorker(final Worker worker, String message) {
        worker.sendMessageToServer(message);
    }

    public boolean verifyPassword(String password) {
        return PASSWORD.equals(password);
    }

    public boolean verifyIdentification(String identification) {
        return IDENTIFICATION.equals(identification);
    }

    @Override
    public void run() {
        start();
    }

    public static void main(String[] args) {
        Server server = new Server(1337);
        new Thread(server).start();
    }
}
