import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.math.BigInteger;
import java.net.Socket;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.logging.Logger;

public class Worker implements Runnable {
    private BufferedReader in;
    private PrintWriter out;
    private static final Logger LOG = Logger.getLogger(Worker.class.getName());
    private static final int SERVER_PORT = 1337;
    private Socket socket;
    private final String password = "mdp";
    private State state = State.WAITING;
    private MiningData miningData = new MiningData();

    public Worker(final Socket socket) {
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
            LOG.severe("Erreur lors de la communication avec le serveur : " + e.getMessage());
        } finally {
            closeConnection();
        }
    }

    private void handleMessage(final String message) {
        switch (message) {
            case Messages.WHO_ARE_YOU -> sendMessageToServer(Messages.IDENTIFICATION);
            case Messages.GIMME_PASSWORD -> sendMessageToServer("PASSWD " + password);
            case Messages.HELLO_YOU -> {
                state = State.READY;
                sendMessageToServer(Messages.READY);
            }
            case Messages.YOU_DONT_FOOL_ME -> {
                state = State.DISCONNECTED;
                closeConnection();
            }
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

    public void sendMessageToServer(String message) {
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
     * @param difficulty difficultée de minage
     * @return Solution trouvée
     **/
    public Solution mine(final byte[] data, final int difficulty, final int workerId, final int jump) {
        System.out.println("Minage en cours... ");
        String prefix = "0".repeat(difficulty);

        int nonce = workerId;
        String hash = hashSHA256(concatenateBytes(data, BigInteger.valueOf(nonce).toByteArray()));
        while (!(Objects.requireNonNull(hash).startsWith(prefix))) {
            nonce+=jump;
            hash = hashSHA256(concatenateBytes(data, BigInteger.valueOf(nonce).toByteArray()));
            System.out.println(hash + " " + nonce);
        }
        return new Solution(hash, Integer.toHexString(nonce), difficulty);
    }

    /*
    * Concaténer deux tableaux de bytes
    *
    * @param a premier tableau
    * @param b deuxième tableau
    *
    * @return tableau concaténé
    */
    private byte[] concatenateBytes(final byte[] a, final byte[] b) {
        byte[] result = new byte[a.length + b.length];
        System.arraycopy(a, 0, result, 0, a.length);
        System.arraycopy(b, 0, result, a.length, b.length);
        return result;
    }

    /**
     * Hasher une chaine de caractère avec l'algorithme SHA-256
     *
     * @param input chaine à hasher en bytes
     * @return le hash en hexadécimal
     **/
    private String hashSHA256(final byte[] input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input);
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
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

    // chatGPT --> à remodifier
    private enum State {
        WAITING, READY, DISCONNECTED
    }
}
