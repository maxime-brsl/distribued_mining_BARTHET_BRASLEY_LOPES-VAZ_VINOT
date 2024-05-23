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
                Socket workerSocket = serverSocket.accept();
                System.out.println("Nouveau worker connecté: " + workerSocket);
                Worker worker = new Worker(workerSocket);
                workers.add(worker);
                new Thread(worker).start();
                initProtocole(worker);
                sendMessageToWorker(worker, "GIMME_PASSWORD");
            } catch (IOException e) {
                LOG.warning("Erreur lors de la connexion du worker: " + e.getMessage());
            }
        }
    }

    public void initProtocole(final Worker worker) {
        worker.sendMessageToServer("WHO_ARE_YOU_?");
    }

    public void sendMessageToWorker(final Worker worker, String message) {
        worker.sendMessageToServer(message);
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
                    Instant end = Instant.now();
                    timer(start, end);
                } catch (Exception e) {
                    LOG.warning("Erreur lors de la récupération de la solution: " + e.getMessage());
                }
            });
        }
        executor.shutdown();
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
}
