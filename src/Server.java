import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.logging.Logger;

public class Server implements Runnable{
    private ServerSocket serverSocket;
    private List<Worker> workers;
    private final ApiConnect apiConnect;
    private static final Logger LOG = Logger.getLogger(Server.class.getName());
    private List<Worker> availableWorkers = new ArrayList<>();

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

    @Override
    public void run() {
        start();
    }
}
