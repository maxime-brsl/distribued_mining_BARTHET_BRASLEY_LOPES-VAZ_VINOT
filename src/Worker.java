import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;
import java.util.logging.Logger;

public class Worker implements Runnable {
    private final Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private static final Logger LOG = Logger.getLogger(Worker.class.getName());
    private String password;

    public Worker(Socket socket) {
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
                System.out.println(message);
                initProtocole(message);
            }
        } catch (IOException e) {
            e.printStackTrace();
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

    public static void main(String[] args) {
        try {
            Socket socket = new Socket("localhost", 1337);
            Worker worker = new Worker(socket);
            new Thread(worker).start();
        } catch (IOException e) {
            LOG.warning("Erreur lors de la création du socket: " + e.getMessage());
        }
    }

    public Solution mine(byte[] data, int difficulty) {
        System.out.println("Mining... ");
        String prefix = "0".repeat(difficulty);

        int nonce = 0;
        String hash;
        do {
            hash = hashSHA256(concatenateBytes(data, intToBytes(nonce)));
            nonce++;
        } while (!Objects.requireNonNull(hash).startsWith(prefix));
        // Adjust the nonce back to the correct value
        nonce--;
        return new Solution(hash, Integer.toHexString(nonce), difficulty);
    }

    private byte[] concatenateBytes(byte[] a, byte[] b) {
        byte[] result = new byte[a.length + b.length];
        System.arraycopy(a, 0, result, 0, a.length);
        System.arraycopy(b, 0, result, a.length, b.length);
        return result;
    }

    private byte[] intToBytes(int value) {
        return ByteBuffer.allocate(4).putInt(value).array();
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder(2 * bytes.length);
        for (byte b : bytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }

    private String hashSHA256(byte[] input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input);
            return bytesToHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}
