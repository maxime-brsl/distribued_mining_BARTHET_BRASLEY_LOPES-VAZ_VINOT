import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

public class Server implements Runnable{
    private ServerSocket serverSocket;
    private List<Worker> workers;
    private final ApiConnect apiConnect;
    private static final Logger LOG = Logger.getLogger(Server.class.getName());
    private List<Worker> availableWorkers = new ArrayList<>();
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
        for (Worker worker : workers) {
            try {
                worker.sendMessageToServer("CANCELLED");
            } catch (Exception e) {
                LOG.warning("Erreur lors de l'envoi du message d'annulation au worker: " + e.getMessage());
            }
        }
        LOG.info("Toutes les tâches en cours ont été annulées.");
    }

    private boolean verifyIdentification(String identification) {
        return Messages.IDENTIFICATION.equals(identification);
    }
    public void getWorkersStatus() {
        for (int i = 0; i < workers.size(); i++) {
            Worker worker = workers.get(i);
            System.out.println("Worker " + i + " - is mining ? : " + worker.isMining());
        }
    }

    public void isMining() {
        for (Worker worker : workers) {
            if (!worker.isMining()) {
                availableWorkers.add(worker);
            }
        }
    }

    public void solveTask(final String difficulty) {
        System.out.println("Minage en cours... ");
        byte[] work = apiConnect.generateWork(difficulty);
        if (work == null) {
            return;
        }
        isMining();
        ExecutorService executor = Executors.newFixedThreadPool(availableWorkers.size());
        List<Future<Solution>> futures = new ArrayList<>();
        for (int i = 0; i < availableWorkers.size(); i++) {
            final int workerId = i;
            futures.add(executor.submit(() -> {
                Worker worker = availableWorkers.get(workerId);
                availableWorkers.remove(worker);
                sendMessageToWorker(worker, "MINE :" + difficulty);
                return worker.mine(work, Integer.parseInt(difficulty), workerId, availableWorkers.size());
            }));
        }
        executor.shutdown();
        for (Future<Solution> future : futures) {
            try {
                Solution solution = future.get();
                String json = "{\"d\": " + solution.difficulty() + ", \"n\": \"" + solution.nonce() + "\", \"h\": \"" + solution.hash() + "\"}";
                System.out.println(json);
                apiConnect.validateWork(json);
            } catch (Exception e) {
                LOG.warning("Erreur lors de la récupération de la solution: " + e.getMessage());
            }
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

