import java.io.IOException;
import java.net.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;

public class Server implements Runnable {
    private ServerSocket serverSocket;
    private List<Worker> workers;
    private ApiConnect apiConnect;
    private static final Logger LOG = Logger.getLogger(Server.class.getName());
    private static final String PASSWORD = "mdp";

    public Server(int port) {
        apiConnect = new ApiConnect();
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
                handleWorker(worker);
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

    private void handleWorker(Worker worker) throws IOException {
        initProtocol(worker);
        if (authenticateWorker(worker)) {
            processWorker(worker);
        } else {
            worker.closeConnection();
        }
    }

    private void initProtocol(final Worker worker) {
        sendMessageToWorker(worker, Messages.WHO_ARE_YOU);
    }

    // Vérification si le worker utilise le bon protocole et a le bon mot de passe
    private boolean authenticateWorker(Worker worker) throws IOException {
        String receivedIdentification = worker.displayReceivedMessageFromWorker();
        if (verifyIdentification(receivedIdentification)) {
            sendMessageToWorker(worker, Messages.GIMME_PASSWORD);
            String receivedPassword = worker.displayReceivedMessageFromWorker();
            if (verifyPassword(receivedPassword)) {
                return true;
            } else {
                sendMessageToWorker(worker, Messages.YOU_DONT_FOOL_ME);
                return false;
            }
        }
        return false;
    }


    private void processWorker(Worker worker) throws IOException {
        sendMessageToWorker(worker, Messages.HELLO_YOU);
        workers.add(worker);
        String receivedReady = worker.displayReceivedMessageFromWorker();
        if (verifyReady(receivedReady)) {
            sendMessageToWorker(worker, Messages.OK);
            // TODO: gestion de ce qu'on fait avec le worker
            sendMessageToWorker(worker, Messages.NONCE);
        } else {
            worker.closeConnection();
        }
    }

    private void sendMessageToWorker(final Worker worker, String message) {
        worker.sendMessageToServer(message);
    }

    private boolean verifyPassword(String password) {
        return password.equals("PASSWD " + PASSWORD);
    }

    public void cancelTask() {
//        if (apiConnect.cancelTask()) {
//            System.out.println("Tache annulée avec succès.");
//            //TODO : Annuler la tâche au worker
//        } else {
//            LOG.warning("Erreur lors de l'annulation de la tâche.");
//        }
    }

    private boolean verifyIdentification(String identification) {
        return Messages.IDENTIFICATION.equals(identification);
    }

    public String solveTask(final String difficulty) {
        String data = apiConnect.generateWork(difficulty);
        int startIndex = data.indexOf("\"data\":\"") + 8;
        int endIndex = data.indexOf("\"", startIndex);
        String workData = data.substring(startIndex, endIndex);
        byte[] workBytes = workData.getBytes();
        System.out.println("Travail généré ! ");
        for (Worker worker : workers) {
            Solution solution = worker.mine(workData, Integer.parseInt(difficulty));
            String json = "{\"d\": " + solution.getDifficulty() + ", \"n\": \"" + solution.getNonce() + "\", \"h\": \"" + solution.getHash() + "\"}";
            System.out.println(apiConnect.validateWork(json));
        }
        return workData;
    }
    private boolean verifyReady(String ready) {
        return Messages.READY.equals(ready);
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

