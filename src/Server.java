import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public class Server implements Runnable {
public class Server implements Runnable{
    private ServerSocket serverSocket;
    private List<Worker> workers;
    private final ApiConnect apiConnect;
    private static final Logger LOG = Logger.getLogger(Server.class.getName());
    private static final String PASSWORD = "mdp";

    public Server(final int port) {
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
        //TODO : Annuler la tache en cours du worker souhaité
    }

    private boolean verifyIdentification(String identification) {
        return Messages.IDENTIFICATION.equals(identification);
    }

    public void solveTask(final String difficulty) {
        byte[] work = apiConnect.generateWork(difficulty);
        if (work == null) {
            return;
        }
        for (int i = 0; i < workers.size(); i++) {
            Solution solution = workers.get(i).mine(work, Integer.parseInt(difficulty), i, workers.size());
            String json = "{\"d\": " + solution.difficulty() + ", \"n\": \"" + solution.nonce() + "\", \"h\": \"" + solution.hash() + "\"}";
            System.out.println(json);
            apiConnect.validateWork(json);
        }
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

