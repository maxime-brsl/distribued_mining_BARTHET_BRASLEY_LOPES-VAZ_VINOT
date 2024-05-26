import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

/**
 * Classe Server
 * Représente le serveur qui gère les workers et leur assigne des tâches
 **/
public class Server implements Runnable{
    private static final Logger LOG = Logger.getLogger(Server.class.getName());
    private ServerSocket serverSocket;
    private List<Worker> workers;
    private final List<Worker> availableWorkers = new ArrayList<>();
    private static final String PASSWORD = "mdp";
    private final ApiConnect apiConnect;
    private String hash;
    private String nonce;

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

    private void handleWorker(final Worker worker) throws IOException {
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

    private boolean verifyIdentification(String identification) {
        return Messages.IDENTIFICATION.equals(identification);
    }

    private boolean verifyPassword(final String password) {
        return password.equals("PASSWD " + PASSWORD);
    }

    private void processWorker(final Worker worker) throws IOException {
        sendMessageToWorker(worker, Messages.HELLO_YOU);
        workers.add(worker);
        String receivedReady = worker.displayReceivedMessageFromWorker();
        if (verifyReady(receivedReady)) {
            sendMessageToWorker(worker, Messages.OK);
        } else {
            worker.closeConnection();
        }
    }

    private void sendMessageToWorker(final Worker worker, String message) {
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

    public void getWorkersStatus() throws IOException {
        if (workersConnectedIsEmpty()) {
            System.out.println("Aucun worker connecté");
            return;
        }
        for (Worker worker : workers) {
            sendMessageToWorker(worker, Messages.PROGRESS);
            worker.displayReceivedMessageFromWorker();
        }
    }

    /**
     * Met à jour la liste des workers disponibles
     **/
    public void updateAvailaibleWorkers() {
        for (Worker worker : workers) {
            if (worker.getState() != State.MINING) {
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
        String work = apiConnect.generateWork(difficulty);
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

                    sendMessageToWorker(worker, Messages.NONCE+" "+workerId+" "+sizeInitialAvailableWorkers);
                    sendMessageToWorker(worker, Messages.PAYLOAD+" "+work);
                    sendMessageToWorker(worker, Messages.SOLVE+" "+difficulty);

                    System.out.println("Minage en cours... ");

                    //Enregistre l'instant de départ du minage pour le calcul du temps écoulé
                    Instant start = Instant.now();

                    //Gestion du FOUND
                    String receivedFound = worker.displayReceivedMessageFromWorker();
                    if (receivedFound.startsWith(Messages.FOUND)){
                        try {
                            String[] parts = receivedFound.split(" ");
                            if (parts.length != 3) {
                                System.out.println("Format incorrect pour le message FOUND");
                                return;
                            }
                            this.hash = parts[1];
                            this.nonce = parts[2];
                            apiConnect.validateWork("{\"d\": " + difficulty + ", \"n\": \"" + nonce + "\", \"h\": \"" + hash + "\"}");
                        } catch (Exception e) {
                            System.out.println("Erreur lors du traitement du message FOUND : " + e.getMessage());
                        }
                        //fin du timer, après résolution et vérification de la solution
                        displayElapsedTime(start, Instant.now());
                    }
                } catch (Exception e) {
                    LOG.warning("Erreur lors de la récupération de la solution: " + e.getMessage());
                }
            });
        }
        //On arrête l'ensemble des threads
        executor.shutdown();
    }

    private boolean verifyReady(String ready) {
        return Messages.READY.equals(ready);
    }

    /**
     * Calculer et affiche la durée d'exécution du minage
     *
     * @param start Instant de début
     * @param end Instant de fin
     **/
    private void displayElapsedTime(final Instant start, final Instant end) {
        Duration timeElapsed = Duration.between(start, end);

        long hours = timeElapsed.toHours();
        long minutes = timeElapsed.toMinutesPart();
        long seconds = timeElapsed.toSecondsPart();

        System.out.printf("Durée de l'exécution: %02d:%02d:%02d%n", hours, minutes, seconds);
    }

    public boolean workersConnectedIsEmpty() {
        return workers.isEmpty();
    }

    @Override
    public void run() {
        start();
    }
}

