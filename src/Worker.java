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
    private boolean isMining;

    public Worker(final Socket socket) {
        isMining = false;
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

    public void sendMessageToServer(final String message) {
        out.println(message);
    }

    /**
     * Miner un bloc de données avec une difficulté donnée
     *
     * @param data data à miner
     * @param difficulty difficulté de minage
     * @param workerId ID du worker
     * @param jump valeur d'incrément
     * @param stopSignal signal d'arrêt
     * @return Solution trouvée
     **/
    public Solution mine(final byte[] data, final int difficulty, final int workerId, final int jump, final AtomicBoolean stopSignal) {
        //Utilise l'ID du worker l'utiliser comme nonce initial
        byte[] nonce = BigInteger.valueOf(workerId).toByteArray();
        String nonceFinal = "";

        //Jump est la valeur d'incrément pour le nonce obtenu par la taille de la liste des workers disponibles
        byte[] jumpBytes = BigInteger.valueOf(jump).toByteArray();

        //C'est le prefix que l'on cherche sur notre hash final
        String prefix = "0".repeat(difficulty);
        String hash = hashSHA256(concatenateBytes(data, nonce));

        //Le worker est en train de miner, il ne sera donc plus disponible
        isMining = true;
        while (!hash.startsWith(prefix)) {
            //Si le signal d'arrêt est activé, on arrête le minage
            if (stopSignal.get()) {
                isMining = false;
                return null;
            }
            //Recalcule du hash avec le nonce actuel
            hash = hashSHA256(concatenateBytes(data, nonce));
            nonceFinal = HexFormat.of().formatHex(nonce);
            //On affiche le nonce actuel en hexadécimal, car les valeurs peuvent être très grande
            out.println(nonceFinal);
            //On incrémente le nonce
            nonce = incrementBytes(nonce, jumpBytes);
        }

        //Le worker n'est plus en train de miner, il est donc disponible
        isMining = false;
        return new Solution(hash, nonceFinal.replaceFirst("^0+", ""), difficulty);
    }

    /**
     * Incrémenter un tableau de bytes par un autre tableau de bytes
     *
     * @param nonce tableau original
     * @param jump valeur d'incrément
     * @return tableau incrémenté
     **/
    private byte[] incrementBytes(final byte[] nonce, final byte[] jump) {
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
     * Vérifier si le worker est en train de miner
     *
     * @return true si le worker est en train de miner, false sinon
     **/
    public boolean isMining() {
        return isMining;
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
