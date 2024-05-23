import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

public class Server implements Runnable{
    private ServerSocket serverSocket;
    private List<Worker> workers;
    private final ApiConnect apiConnect;
    private static final Logger LOG = Logger.getLogger(Server.class.getName());
    private List<Worker> availableWorkers = new ArrayList<>();
    private static final String PASSWORD = "mdp";
    private final AtomicBoolean stopSignal = new AtomicBoolean(false);

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
        Instant start = Instant.now();
        byte[] work = apiConnect.generateWork(difficulty);
        if (work == null) {
            return;
        }
        isMining();

        int sizeInitialAvailableWorkers = availableWorkers.size();
        ExecutorService executor = Executors.newFixedThreadPool(sizeInitialAvailableWorkers);

        for (int i = 0; i < sizeInitialAvailableWorkers; i++) {
            final int workerId = i;
            executor.submit(() -> {
                Worker worker = availableWorkers.removeFirst();
                try {
                    Solution solution = worker.mine(work, Integer.parseInt(difficulty), workerId, sizeInitialAvailableWorkers, stopSignal);
                    stopSignal.set(true);
                    if (solution == null) {
                        return;
                    }
                    String json = "{\"d\": " + solution.difficulty() + ", \"n\": \"" + solution.nonce() + "\", \"h\": \"" + solution.hash() + "\"}";
                    System.out.println("Solution trouvée par worker " + workerId + "  : " + json);
                    apiConnect.validateWork(json);
                    timer(start, Instant.now());
                } catch (Exception e) {
                    LOG.warning("Erreur lors de la récupération de la solution: " + e.getMessage());
                }
            });
        }
        executor.shutdown();
    }
    private boolean verifyReady(String ready) {
        return Messages.READY.equals(ready);
    }

    /**
     * Calculer la durée d'exécution du minage
     *
     * @param start Instant de début
     * @param end Instant de fin

     **/
    private void timer(final Instant start, final Instant end) {
        Duration timeElapsed = Duration.between(start, end);

        long hours = timeElapsed.toHours();
        long minutes = timeElapsed.toMinutesPart();
        long seconds = timeElapsed.toSecondsPart();

        System.out.printf("Durée de l'exécution: %02d:%02d:%02d%n", hours, minutes, seconds);
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

