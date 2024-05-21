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
    private static final Logger LOG = Logger.getLogger(Worker.class.getName());
    private static final int SERVER_PORT = 1337;
    private final Socket socket;
    private final BufferedReader in;
    private final PrintWriter out;
    private final String password = "mdp";
    private State state = State.WAITING;
    private MiningData miningData = new MiningData();

    public Worker(Socket socket) throws IOException {
        this.socket = socket;
        this.in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        this.out = new PrintWriter(socket.getOutputStream(), true);
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

    public static void main(String[] args) {
        try  {
            Socket socket = new Socket("localhost", SERVER_PORT);
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
