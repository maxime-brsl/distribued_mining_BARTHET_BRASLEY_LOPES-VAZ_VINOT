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
                System.out.println("Message received : " + message);
                handleMessage(message);
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
            case Messages.PROGRESS -> handleProgress(message);
            case Messages.SOLVED -> handleSolved(message);
            case Messages.CANCELLED -> handleCancelled(message);

            default -> {
                if (message.contains("NONCE")) {
                    handleNonce(message);
                } else if (message.contains("PAYLOAD")) {
                    handlePayload(message);
                }else if (message.contains("SOLVE")) {
                    handleSolved(message);
                }else {
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

    public void handleNonce(String message) {
        // check si la chaîne à le bon format
        // process le nonce
    }

    public void handlePayload(String message) {
        // check si la chaîne à le bon format
        // process le payload
    }

    public void handleSolved(String message) {
        // check si la chaîne à le bon format
        // process le solve
    }

    public void handleProgress(String message) {
        // process le progress
    }

    public void handleCancelled(String message) {
        // process le cancelled
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
    public Solution mine(final byte[] data, final int difficulty, final int workerId, final int jump, final AtomicBoolean stopSignal) {
        byte[] nonce = BigInteger.valueOf(workerId).toByteArray();
        String nonceFinal = "";
        byte[] jumpBytes = BigInteger.valueOf(jump).toByteArray();
        String prefix = "0".repeat(difficulty);
        String hash = hashSHA256(concatenateBytes(data, nonce));
        setWorkerState(State.MINING);
        while (!hash.startsWith(prefix)) {
            if (stopSignal.get()) {
                setWorkerState(State.READY);
                return null;
            }
            hash = hashSHA256(concatenateBytes(data, nonce));
            nonceFinal = HexFormat.of().formatHex(nonce);
            out.println(nonceFinal);
            nonce = incrementBytes(nonce, jumpBytes);
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

    /**
     * Récupérer l'état du worker
     *
     * @return état du workerst
     **/
    public State getState() {
        return this.state;
    }

    private void setWorkerState(State state) {
        System.out.println("Worker state : " + state);
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
