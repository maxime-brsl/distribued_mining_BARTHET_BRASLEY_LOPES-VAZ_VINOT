import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
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

    public Solution mine(String data, int difficulty) {
        System.out.println("Mining... ");
        byte[] dataBytes = data.getBytes();
        String prefix = "0".repeat(difficulty);

        int nonce = 0;
        String hash;
        do {
            hash = hashSHA256(concatenateBytes(dataBytes, intToBytes(nonce)));
            nonce++;
        } while (!Objects.requireNonNull(hash).startsWith(prefix));
        return new Solution(hash, Integer.toHexString(nonce), difficulty);
    }

    private String concatenateBytes(byte[] a, byte[] b) {
        byte[] result = new byte[a.length + b.length];
        System.arraycopy(a, 0, result, 0, a.length);
        System.arraycopy(b, 0, result, a.length, b.length);
        return bytesToHex(result);
    }

    private byte[] intToBytes(int value) {
        byte[] result = new byte[4];
        for (int i = 0; i < 4; i++) {
            result[i] = (byte) (value >> (24 - i * 8));
        }
        return result;
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder();
        for (byte aByte : bytes) {
            String hex = Integer.toHexString(0xff & aByte);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    }

    private static String hashSHA256(String data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(data.getBytes());

            // Convertir les octets du hachage en une chaîne hexadécimale
            StringBuilder hexString = new StringBuilder();
            for (byte hashByte : hashBytes) {
                String hex = Integer.toHexString(0xff & hashByte);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            return null;
        }
    }
}
