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

/**
 * Classe Server
 * Représente le serveur qui gère les workers et leur assigne des tâches
 **/
public class Server implements Runnable{
    private static final Logger LOG = Logger.getLogger(Server.class.getName());
    private ServerSocket serverSocket;
    private List<Worker> workers;
    private List<Worker> availableWorkers = new ArrayList<>();
    private static final String PASSWORD = "mdp";
    private final ApiConnect apiConnect;
    // Variable partagée entre les threads pour arrêter le minage
    // C'est une variable atomique pour éviter les problèmes de concurrence
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
    
    /**
     * Annule toutes les tâches en cours en utilisant le signal d'arrêt
     **/
    public void cancelTask() {
        for (Worker worker : workers) {
            try {
                worker.sendMessageToServer("CANCELLED");
                stopSignal.set(true);
            } catch (Exception e) {
                LOG.warning("Erreur lors de l'envoi du message d'annulation au worker: " + e.getMessage());
            }
            LOG.info("Toutes les tâches en cours ont été annulées.");
        }
    }

    private boolean verifyIdentification(String identification) {
        return Messages.IDENTIFICATION.equals(identification);
    }
        
    /**
     * Affiche le statut de chaque worker dans la console
     **/
    public void getWorkersStatus() {
        for (int i = 0; i < workers.size(); i++) {
            Worker worker = workers.get(i);
            System.out.println("Worker " + i + " - is mining ? : " + worker.isMining());
        }
        for (int i = 0; i < workers.size(); i++) {
            Worker worker = workers.get(i);
            System.out.println("Worker " + i + " - is mining ? : " + worker.isMining());
        }
    }

    /**
     * Met à jour la liste des workers disponibles
     **/
    public void updateAvailaibleWorkers() {
        for (Worker worker : workers) {
            if (!worker.isMining()) {
                availableWorkers.add(worker);
            }
        }
    }

    public void isMining() {
        for (Worker worker : workers) {
            if (!worker.isMining()) {
                availableWorkers.add(worker);
            }
        }
    }

    /**
     * Lance le minage avec une difficulté donnée
     *
     * @param difficulty difficulté de minage
     **/
    public void solveTask(final String difficulty) {
        System.out.println("Minage en cours... ");

        //Enregistre l'instant de départ du minage pour le calcul du temps écoulé
        Instant start = Instant.now();

        byte[] work = apiConnect.generateWork(difficulty);
        if (work == null) {
            return;
        }
        updateAvailaibleWorkers();

        //On récupère le nombre de workers disponibles
        int sizeInitialAvailableWorkers = availableWorkers.size();

        //Crée un groupe de threads avec un nombre de threads égal au nombre de workers disponibles
        ExecutorService executor = Executors.newFixedThreadPool(sizeInitialAvailableWorkers);

        for (int i = 0; i < sizeInitialAvailableWorkers; i++) {
            final int workerId = i;
            //Pour chaque worker disponible, on crée un thread qui va miner
            executor.submit(() -> {
                //Comme on utilise un worker, on le retire de la liste des workers disponibles et on le stocke pour le faire miner
                Worker worker = availableWorkers.remove(0);
                try {
                    Solution solution = worker.mine(work, Integer.parseInt(difficulty), workerId, sizeInitialAvailableWorkers, stopSignal);

                    //Si on a trouvé une solution, on arrête les autres workers
                    stopSignal.set(true);
                    if (solution == null) {
                        return;
                    }
                    //On formate la solution dans le bon format JSON pour l'envoyer à l'API
                    String json = "{\"d\": " + solution.difficulty() + ", \"n\": \"" + solution.nonce() + "\", \"h\": \"" + solution.hash() + "\"}";
                    System.out.println("Solution trouvée par worker " + workerId + "  : " + json);
                    apiConnect.validateWork(json);
                    timer(start, Instant.now());
                } catch (Exception e) {
                    LOG.warning("Erreur lors de la récupération de la solution: " + e.getMessage());
                }
            });
        }
        //On arrête l'ensemble des threads
        executor.shutdown();
        //On remet le signal d'arrêt à faux pour les prochains minages
        stopSignal.set(false);
    }

    /**
    * Remet le signal d'arrêt à faux
    **/
    /*
    public void setStopSignalFalse() {
        stopSignal.set(false);
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
        stopSignal.set(false);
    }
    */
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

