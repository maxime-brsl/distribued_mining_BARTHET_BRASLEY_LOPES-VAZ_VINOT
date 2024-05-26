import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.*;
import java.security.cert.CertificateException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;
import javax.net.ssl.*;
import java.io.*;

/**
 * Classe Server
 * Représente le serveur qui gère les workers et leur assigne des tâches
 **/
public class Server implements Runnable{

    private static final Logger LOG = Logger.getLogger(Server.class.getName());
    private ServerSocket serverSocket;
    private SSLServerSocket secureserverSocket;
    private List<Worker> workers;
    private final List<Worker> availableWorkers = new ArrayList<>();
    private String PASSWORD;
    private final ApiConnect apiConnect;
    private String hash;
    private String nonce;

    private static final String KEYSTORE_PATH = "serverkeystore.jks";

    private static final String KEYSTORE_PASSWORD = "mysecret1";

    private SSLServerSocketFactory sslServerSocketFactory;

    private boolean boucle;

    public Server(final int port) {
        apiConnect = new ApiConnect();
        this.initialSSL(port);
        boucle = true;
        try {
            this.PASSWORD = generatePassword(8);
            workers = new ArrayList<>();
        } catch (Exception e) {
            LOG.warning("Erreur lors de la création du serveur: " + e.getMessage());
        }
    }

    private void initialSSL(int port){
        try {
            // Charger le keystore
            KeyStore keyStore = KeyStore.getInstance("JKS");
            try (FileInputStream keyStoreStream = new FileInputStream(KEYSTORE_PATH)) {
                keyStore.load(keyStoreStream, KEYSTORE_PASSWORD.toCharArray());
            }

            // Configurer KeyManagerFactory
            KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            keyManagerFactory.init(keyStore, KEYSTORE_PASSWORD.toCharArray());

            // Configurer SSLContext
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(keyManagerFactory.getKeyManagers(), null, null);

            // Créer SSLServerSocket
            sslServerSocketFactory = sslContext.getServerSocketFactory();
            secureserverSocket = (SSLServerSocket) sslServerSocketFactory.createServerSocket(port);
            System.out.println("Serveur en attente de connexions sécurisées...");


        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String generatePassword(int longueur){
        String caracterePossible = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        Random random = new Random();
        String mdp = "";
        for (int i = 0; i < longueur; i++) {
            // Ajouter un caractère au hasard
            int index = random.nextInt(caracterePossible.length());
            char caractere = caracterePossible.charAt(index);
            mdp = mdp + caractere;
        }
        return mdp;
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
        SSLSocket workerSocket = (SSLSocket) secureserverSocket.accept();
        System.out.println("Nouveau worker connecté: " + workerSocket);
        return new Worker(workerSocket);
    }

    /**
     * Ajoute le worker à la liste des workers disponibles pour le minage si il est authentifié
     * @param worker worker à gérer
     **/
    private void handleWorker(final Worker worker) throws IOException {
        initProtocol(worker);
        // Envoyer mot de passe

        if (authenticateWorker(worker)) {
            processWorker(worker);
        } else {
            worker.closeConnection();
        }
    }

    private void envoieMdp(SSLSocket sslSocket) {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(sslSocket.getInputStream()));
             PrintWriter out = new PrintWriter(sslSocket.getOutputStream(), true)) {

            String received = in.readLine();
            System.out.println("Message reçu : " + received);

            out.println("MDP_IS=" + PASSWORD);

            boucle = false;

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void initProtocol(final Worker worker) {
        sendMessageToWorker(worker, Messages.WHO_ARE_YOU);
    }

    // Vérification si le worker utilise le bon protocole et a le bon mot de passe
    private boolean authenticateWorker(final Worker worker) throws IOException {
        String receivedIdentification = worker.displayReceivedMessageFromWorker();
        if (verifyIdentification(receivedIdentification)) {
            sendMDPToWorker(worker, Messages.MDP_IS + PASSWORD);
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

    private boolean verifyIdentification(final String identification) {
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

    private void sendMDPToWorker(final Worker worker, String message) {
        worker.sendMessageToServer(message);
    }

    private void sendMessageToAllWorkers(final String message) {
        for (Worker worker : workers) {
            sendMessageToWorker(worker, message);
        }
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
     * Ajoute à la liste workers tous les workers qui ne minent pas actuellement
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
                    // Envoi des messages au worker
                    // Ordre insignifiant grâce à la fonction check() du worker
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
                        sendMessageToAllWorkers(Messages.SOLVED);
                    }
                } catch (Exception e) {
                    LOG.warning("Erreur lors de la récupération de la solution: " + e.getMessage());
                }
            });
        }
        //On arrête l'ensemble des threads
        executor.shutdown();
    }

    private boolean verifyReady(final String ready) {
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

