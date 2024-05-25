import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.math.BigInteger;
import java.net.Socket;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

/**
 * Classe Worker
 * Représente un worker qui peut miner un bloc
 **/
public class Worker implements Runnable {
    private static final Logger LOG = Logger.getLogger(Worker.class.getName());
    private BufferedReader in;
    private PrintWriter out;
    private final Socket socket;
    private final String password = "mdp";
    private State state;
    private byte[] data;
    private int difficulty = -1;
    private int start = -1;
    private int increment = -1;
    private String nonceFinal = "";
    private volatile Thread miningThread;


    public Worker(final Socket socket) {
        setWorkerState(State.WAITING);
        this.socket = socket;
        try {
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);
        } catch (IOException e) {
            LOG.warning("Erreur lors de la création du worker: " + e.getMessage());
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
                    System.out.println("Message received : " + message);
                    handleMessage(message);
                }
            }
        } catch (IOException e) {
            LOG.warning("Erreur lors de la lecture du message: " + e.getMessage());
        } finally {
            closeConnection();
        }
    }

    private void handleMessage(final String message) {
        switch (message) {
            case Messages.WHO_ARE_YOU -> sendMessageToServer(Messages.IDENTIFICATION);
            case Messages.GIMME_PASSWORD -> sendMessageToServer("PASSWD " + password);
            case Messages.HELLO_YOU -> {
                setWorkerState(State.READY);
                sendMessageToServer(Messages.READY);
            }
            case Messages.YOU_DONT_FOOL_ME -> {
                setWorkerState(State.DISCONNECTED);
                closeConnection();
            }
            case Messages.OK -> setWorkerState(State.READY);
            case Messages.PROGRESS -> handleProgress();
            case Messages.SOLVED -> handleSolved(message);
            case Messages.CANCELLED -> handleCancelled();

            default -> {
                if (message.contains("NONCE")) {
                    handleNonce(message);
                } else if (message.contains("PAYLOAD")) {
                    handlePayload(message);
                }else if (message.contains("SOLVE")) {
                    handleSolve(message);
                }else if (message.contains("SOLVED")) {
                    handleSolved(message);
                } else if (message.contains("Minage du bloc: ")) {
                    // do nothing
                }else{
                    System.out.println("Message non reconnu : " + message);
                }
            }
        }
    }

    public void sendMessageToServer(final String message) {
        out.println(message);
        System.out.println("Message sent : " + message);
    }

    public String displayReceivedMessageFromWorker() throws IOException {
        String message = in.readLine();
        System.out.println("Message received : " + message);
        return message;
    }


    /**
     * Traiter le message NONCE
     * Vérifier le format du message et extraire les données
     *
     * @param message message NONCE
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

            check();

        } catch (NumberFormatException e) {
            System.out.println("Erreur lors de la conversion des paramètres NONCE : " + e.getMessage());
        }
    }


    public void handlePayload(final String message) {
        try {
            // Vérification du format du message
            String[] parts = message.split(" ", 2); // Split en deux parties : instruction et paramètre
            if (parts.length != 2) {
                System.out.println("Format incorrect pour le message PAYLOAD");
                return;
            }

            // Récupération des données
            data = parts[1].getBytes();

            // Traitement des données ici...
            System.out.println("Données reçues : " + data);

            check();

        } catch (Exception e) {
            System.out.println("Erreur lors du traitement du message PAYLOAD : " + e.getMessage());
        }

    }

    public void handleSolve(final String message) {
        try {
            // Vérification du format du message
            String[] parts = message.split(" ");
            if (parts.length != 2) {
                System.out.println("Format incorrect pour le message SOLVE");
                return;
            }
            // Récupération de la difficulté
             this.difficulty = Integer.parseInt(parts[1]);

            check();

        } catch (NumberFormatException e) {
            System.out.println("Erreur lors de la conversion de la difficulté : " + e.getMessage());
        } catch (Exception e) {
            System.out.println("Erreur lors du traitement du message SOLVE : " + e.getMessage());
        }

    }

    public void check() {
        if (!returnIfMiningDataIsReady()) {
            return; // Exit if mining data is not ready
        }

        if (miningThread == null || !miningThread.isAlive()) {
            miningThread = new Thread(() -> {
                Solution solution = mine(data, difficulty, start, increment);
                if (solution == null) {
                    return;
                }
                sendMessageToServer(Messages.FOUND + " " + solution.hash() + " " + solution.nonce());
                cleanMiningDataAttributes();
            });
            miningThread.start();
        }
    }

    public boolean returnIfMiningDataIsReady() {
        return (data != null) && (difficulty != -1) && (start != -1) && (increment != -1);
    }

    public void cleanMiningDataAttributes(){
        this.data = null;
        this.difficulty = -1;
        this.start = -1;
        this.increment = -1;
    }

    public void handleSolved(final String message) {
        // check si la chaîne à le bon format
        // process le solve
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
     * @param data data à miner
     * @param difficulty difficulté de minage
     * @return Solution trouvée
     **/
    public Solution mine(final byte[] data, final int difficulty, final int workerId, final int jump) {
        byte[] nonce = BigInteger.valueOf(workerId).toByteArray();
        byte[] jumpBytes = BigInteger.valueOf(jump).toByteArray();
        String prefix = "0".repeat(difficulty);
        String hash = hashSHA256(concatenateBytes(data, nonce));
        setWorkerState(State.MINING);
        while (!Thread.currentThread().isInterrupted() && !hash.startsWith(prefix)) {
                hash = hashSHA256(concatenateBytes(data, nonce));
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
    private byte[] incrementBytes(byte[] nonce, byte[] jump) {
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

    public State getState() {
        return this.state;
    }

    private void setWorkerState(State state) {
        this.state = state;
    }

    public static void main(String[] args) {
        try {
            Socket socket = new Socket("localhost", 1337);
            Worker worker = new Worker(socket);
            new Thread(worker).start();
        } catch (IOException e) {
            LOG.warning("Erreur lors de la création du socket: " + e.getMessage());
        }
    }
}
