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
    private String password;

    public Worker(Socket socket) {
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
                System.out.println(message);
                initProtocole(message);
            }
        } catch (IOException e) {
            LOG.warning("Erreur lors de la lecture du message: " + e.getMessage());
        }
    }

    public void initProtocole(final String message) {
        if (message.equals("WHO_ARE_YOU_?")) {
            sendMessageToServer("ITS_ME");
        }
        if (message.equals("GIMME_PASSWORD")) {
            sendMessageToServer("mdp");
        }
    }

    public void sendMessageToServer(String message) {
        out.println(message);
    }

    /**
     * Miner un bloc de données avec une difficulté donnée
     *
     * @param data data à miner
     * @param difficulty difficultée de minage
     * @return Solution trouvée
     **/
    public Solution mine(byte[] data, int difficulty) {
        System.out.println("Minage en cours... ");
        String prefix = "0".repeat(difficulty);

        int nonce = 0;
        String hash = hashSHA256(concatenateBytes(data, BigInteger.valueOf(nonce).toByteArray()));
        while (!(Objects.requireNonNull(hash).startsWith(prefix))) {
            nonce++;
            hash = hashSHA256(concatenateBytes(data, BigInteger.valueOf(nonce).toByteArray()));
            System.out.println(hash + " " + nonce);
        }
        return new Solution(hash, Integer.toHexString(nonce), difficulty);
    }

    private byte[] concatenateBytes(byte[] a, byte[] b) {
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
    private String hashSHA256(byte[] input) {
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
}
