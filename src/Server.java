import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public class Server implements Runnable {
    private ServerSocket serverSocket;
    private List<Worker> workers;
    private static final Logger LOG = Logger.getLogger(Server.class.getName());
    private static final String PASSWORD = "mdp";
    private static final String IDENTIFICATION = "ITS_ME";

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
                if (verifyIdentification(receivedIdentification)) {
                    sendMessageToWorker(worker, "GIMME_PASSWORD");
                    String receivedPassword = worker.receiveMessageFromWorker();
                    if (verifyPassword(receivedPassword)) {
                        sendMessageToWorker(worker, "HELLO_YOU");
                        workers.add(worker);
                        String receivedReady = worker.receiveMessageFromWorker();
                        if (verifyReady(receivedReady)) {
                            sendMessageToWorker(worker, "OK");
                        }
                    } else {
                        sendMessageToWorker(worker, "YOU_DONT_FOOL_ME");
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
        return new Worker(workerSocket);
    }

    public void initProtocole(final Worker worker) {
        sendMessageToWorker(worker, "WHO_ARE_YOU_?");
    }

    public void sendMessageToWorker(final Worker worker, String message) {
        worker.sendMessageToServer(message);
    }

    public boolean verifyPassword(String password) {
        System.out.println("Message received : " + password);
        return password.equals("PASSWD " + PASSWORD);
    }

    public boolean verifyIdentification(String identification) {
        System.out.println("Message received : " + identification);
        return IDENTIFICATION.equals(identification);
    }

    public boolean verifyReady(String ready) {
        System.out.println("Message received : " + ready);
        return "READY".equals(ready);
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
