import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.math.BigInteger;
import java.net.Socket;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.logging.Logger;
import javax.net.ssl.*;
import java.io.*;
import java.security.KeyStore;

/**
 * Classe Worker
 * Représente un worker qui peut miner un bloc
 **/
public class Worker implements Runnable {

    private static final int PORT = 1337;
    private static final String HOST = "localhost";
    private static final Logger LOG = Logger.getLogger(Worker.class.getName());
    private BufferedReader in;
    private PrintWriter out;
    private SSLSocket socket;
    private String password;
    private State state;
    private byte[] data;
    private int difficulty = -1;
    private int start = -1;
    private int increment = -1;
    private String nonceFinal = "";
    private volatile Thread miningThread;

    private static final String TRUSTSTORE_PATH = "clienttruststore.jks";
    private static final String TRUSTSTORE_PASSWORD = "mysecret2";

    public Worker(final SSLSocket socket) {
        setWorkerState(State.WAITING);
        this.socket = socket;
        try {
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);
        } catch (IOException e) {
            LOG.warning("Erreur lors de la création du worker: " + e.getMessage());
        }
    }

    public void demandeMotDePasse(String message) {
        String mdpRecu = message.substring(6); // Extraire le mot de passe de la réponse
        this.password = mdpRecu;
    }

    public static SSLSocket makeSSlSocket(){
        try {
            // Charger le truststore
            KeyStore trustStore = KeyStore.getInstance("JKS");
            try (FileInputStream trustStoreStream = new FileInputStream(TRUSTSTORE_PATH)) {
                trustStore.load(trustStoreStream, TRUSTSTORE_PASSWORD.toCharArray());
            }

            // Configurer TrustManagerFactory
            TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            trustManagerFactory.init(trustStore);

            // Configurer SSLContext
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustManagerFactory.getTrustManagers(), null);

            // Créer SSLSocket
            SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();
            SSLSocket socketSecure = (SSLSocket) sslSocketFactory.createSocket(HOST, PORT);
            return socketSecure;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public void cacherMDP(String message){
        if (message.startsWith(Messages.MDP_IS)){
            System.out.println("Message received : password's server");
        } else {
            System.out.println("Message received : " + message);
        }
    }

    @Override
    public void run() {
        try {
            String message;
            while ((message = in.readLine()) != null) {
                if (message.contains("Minage du bloc: ")) {
                    System.out.println(message);
                } else {
                    cacherMDP(message);
                    handleMessage(message);
                }
            }
        } catch (IOException e) {
            LOG.warning("Erreur lors de la lecture du message: " + e.getMessage());
        } finally {
            closeConnection();
        }
    }

    /**
     * Centralise le traitement des messages reçus du serveur et renvoie la réponse appropriée suivant le protocole
     * @param message message reçu
     */
    private void handleMessage(final String message) {
        switch (message) {
            case Messages.WHO_ARE_YOU -> sendMessageToServer(Messages.IDENTIFICATION);
            case Messages.GIMME_PASSWORD -> sendMessageToServer("PASSWD " + password);
            case Messages.HELLO_YOU -> handleHelloYou();
            case Messages.YOU_DONT_FOOL_ME -> handleYouDontFoolMe();
            case Messages.OK -> setWorkerState(State.READY);
            case Messages.PROGRESS -> handleProgress();
            case Messages.SOLVED -> handleSolved();
            case Messages.CANCELLED -> handleCancelled();
            default -> handleOthersMessages(message);
        }
    }


    private void handleOthersMessages(final String message) {
        if (message.startsWith(Messages.NONCE)) {
            handleNonce(message);
        } else if (message.startsWith(Messages.PAYLOAD)) {
            handlePayload(message);
        } else if (message.startsWith(Messages.SOLVE)) {
            handleSolve(message);
        } else if (message.startsWith(Messages.MDP_IS)) {
            this.demandeMotDePasse(message);
        } else {
            System.out.println("Message non reconnu : " + message);
        }
    }

    private void handleHelloYou() {
        setWorkerState(State.READY);
        sendMessageToServer(Messages.READY);
    }

    private void handleYouDontFoolMe() {
        setWorkerState(State.DISCONNECTED);
        closeConnection();
    }

    /**
     * Vérifie le format du message NONCE et initialise les attributs de minage
     * @param message message reçu
     **/
    public void handleNonce(final String message) {
        try {
            String[] parts = message.split(" ");
            if (parts.length != 3) {
                System.out.println("Format incorrect pour le message NONCE");
                return;
            }
            this.start = Integer.parseInt(parts[1]);
            this.increment = Integer.parseInt(parts[2]);
            startMiningIfReady();
        } catch (NumberFormatException e) {
            System.out.println("Erreur lors de la conversion des paramètres NONCE : " + e.getMessage());
        }
    }

    /**
     * Vérifie le format du message PAYLOAD et initialise les attributs de minage
     * @param message message reçu
     **/
    public void handlePayload(final String message) {
        try {
            // Vérification du format du message
            String[] parts = message.split(" ", 2); // Split en deux parties : instruction et paramètre
            if (parts.length != 2) {
                System.out.println("Format incorrect pour le message PAYLOAD");
                return;
            }
            data = parts[1].getBytes();
            startMiningIfReady();
        } catch (Exception e) {
            System.out.println("Erreur lors du traitement du message PAYLOAD : " + e.getMessage());
        }

    }

    /**
     * Vérifie le format du message SOLVE et initialise la difficulté de minage
     * @param message message reçu
     **/
    public void handleSolve(final String message) {
        try {
            // Vérification du format du message
            String[] parts = message.split(" ");
            if (parts.length != 2) {
                System.out.println("Format incorrect pour le message SOLVE");
                return;
            }
            this.difficulty = Integer.parseInt(parts[1]);
            startMiningIfReady();
        } catch (NumberFormatException e) {
            System.out.println("Erreur lors de la conversion de la difficulté : " + e.getMessage());
        } catch (Exception e) {
            System.out.println("Erreur lors du traitement du message SOLVE : " + e.getMessage());
        }
    }

    /**
     * Démarrer le minage si tous les messages du protocole ont été reçues
     **/
    public void startMiningIfReady() {
        if (returnIfMiningDataIsReady()) {
            if (miningThread == null || !miningThread.isAlive()) {
                // Minage dans un thread pour pouvoir lancer d'autres commandes en parallèle
                miningThread = new Thread(() -> {
                    Solution solution = mine();
                    if (solution == null) {
                        return;
                    }
                    // Envoi du message FOUND au serveur avec le hash et le nonce (protocole)
                    sendMessageToServer(Messages.FOUND + " " + solution.hash() + " " + solution.nonce());
                    cleanMiningDataAttributes();
                    sendMessageToServer(Messages.READY);
                });
                miningThread.start();
            }
        }
    }

    /**
     * Retourne si NONCE, PAYLOAD et SOLVE ont été traité et que le minage peut démarrer
     **/
    public boolean returnIfMiningDataIsReady() {
        return (this.data != null) && (this.difficulty != -1) && (this.start != -1) && (this.increment != -1);
    }

    public void cleanMiningDataAttributes(){
        this.data = null;
        this.difficulty = -1;
        this.start = -1;
        this.increment = -1;
    }

    public void handleSolved() {
        if (miningThread != null && miningThread.isAlive()) {
            miningThread.interrupt();
        }
        cleanMiningDataAttributes();
        sendMessageToServer(Messages.READY);
    }

    public void handleProgress() {
        if (state==State.MINING) {
            sendMessageToServer("TESTING " + nonceFinal);
        } else {
            sendMessageToServer("NOPE");
        }
    }

    public void handleCancelled() {
        if (miningThread != null && miningThread.isAlive()) {
            miningThread.interrupt();
        }
        sendMessageToServer(Messages.READY);
    }

    public void closeConnection() {
        try {
            if (!socket.isClosed()) {
                socket.close();
                LOG.info("Connexion fermée.");
            }
        } catch (IOException e) {
            LOG.warning("Erreur lors de la fermeture de la connexion: " + e.getMessage());
        }
    }

    /**
     * Miner un bloc de données avec une difficulté donnée
     *
     * @return Solution trouvée
     **/
    public Solution mine() {
        byte[] nonce = BigInteger.valueOf(this.start).toByteArray();
        byte[] jumpBytes = BigInteger.valueOf(this.increment).toByteArray();
        String prefix = "0".repeat(this.difficulty);
        String hash = hashSHA256(concatenateBytes(this.data, nonce));
        setWorkerState(State.MINING);
        while (!Thread.currentThread().isInterrupted() && !hash.startsWith(prefix)) {
                hash = hashSHA256(concatenateBytes(this.data, nonce));
                nonceFinal = HexFormat.of().formatHex(nonce);
                System.out.println("Minage du bloc: " + nonceFinal);
                nonce = incrementBytes(nonce, jumpBytes);
        }
        if (Thread.currentThread().isInterrupted()) {
            setWorkerState(State.READY);
            System.out.println("Le travail a été annulé");
            return null;
        }
        setWorkerState(State.READY);
        return new Solution(hash, nonceFinal.replaceFirst("^0+", ""), difficulty);
    }

    /**
     * Incrémenter un tableau de bytes par un autre tableau de bytes
     *
     * @param nonce tableau original
     * @param jump valeur d'incrément
     * @return tableau incrémenté
     **/
    private byte[] incrementBytes(final byte[] nonce,final byte[] jump) {
        BigInteger nonceBigInt = new BigInteger(1, nonce);
        BigInteger jumpBigInt = new BigInteger(1, jump);
        nonceBigInt = nonceBigInt.add(jumpBigInt);
        return nonceBigInt.toByteArray();
    }

    /**
    * Concaténer deux tableaux de bytes
    *
    * @param a premier tableau
    * @param b deuxième tableau
    *
    * @return tableau concaténé
    **/
    private byte[] concatenateBytes(final byte[] a, final byte[] b) {
        byte[] result = new byte[a.length + b.length];
        System.arraycopy(a, 0, result, 0, a.length);
        System.arraycopy(b, 0, result, a.length, b.length);
        return result;
    }

    /**
     * Hasher une chaine de caractère avec l'algorithme SHA-256
     *
     * @param data chaine à hasher en bytes
     * @return le hash en hexadécimal
     **/
    private String hashSHA256(final byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    public void sendMessageToServer(final String message) {
        out.println(message);
        cacherMDP(message);
    }

    public String displayReceivedMessageFromWorker() throws IOException {
        String message = in.readLine();
        cacherMDP(message);
        return message;
    }

    public State getState() {
        return this.state;
    }

    private void setWorkerState(final State state) {
        this.state = state;
    }

    public static void main(String[] args) {
        SSLSocket socket = makeSSlSocket();
        Worker worker = new Worker(socket);
        new Thread(worker).start();
    }
}
