import java.io.IOException;
import java.net.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;

public class Server implements Runnable{
    private static final String BASE_URL = "https://projet-raizo-idmc.netlify.app/.netlify/functions";
    private static final String AUTH_TOKEN = "reclRPzXSOmGArkLi";
    private ServerSocket serverSocket;
    private List<Worker> workers;
    private ApiConnect apiConnect;
    private static final Logger LOG = Logger.getLogger(Server.class.getName());

    public Server(int port) {
        apiConnect = new ApiConnect();
        try {
            serverSocket = new ServerSocket(port);
            workers = new ArrayList<>();
        } catch (IOException e) {
            LOG.warning("Erreur lors de la création du serveur: " + e.getMessage());
        }
    }

    public void start() {
        System.out.println("Serveur démarré");
        while (true) {
            try {
                Socket workerSocket = serverSocket.accept();
                System.out.println("Nouveau worker connecté: " + workerSocket);
                Worker worker = new Worker(workerSocket);
                workers.add(worker);
                new Thread(worker).start();
                initProtocole(worker);
                sendMessageToWorker(worker, "GIMME_PASSWORD");
            } catch (IOException e) {
                LOG.warning("Erreur lors de la connexion du worker: " + e.getMessage());
            }
        }
    }

    public void initProtocole(final Worker worker) {
        worker.sendMessageToServer("WHO_ARE_YOU_?");
    }

    public void sendMessageToWorker(final Worker worker, String message) {
        worker.sendMessageToServer(message);
    }

    public void cancelTask() {
//        if (apiConnect.cancelTask()) {
//            System.out.println("Tache annulée avec succès.");
//            //TODO : Annuler la tâche au worker
//        } else {
//            LOG.warning("Erreur lors de l'annulation de la tâche.");
//        }
    }

    public void getWorkersStatus() {
        //TODO : Récupérer le status des workers
    }

    public String solveTask(final String difficulty) {
        String data = apiConnect.generateWork(difficulty);
        int startIndex = data.indexOf("\"data\":\"") + 8;
        int endIndex = data.indexOf("\"", startIndex);
        String workData = data.substring(startIndex, endIndex);
        byte[] workBytes = workData.getBytes();
        System.out.println("Travail généré ! ");
        for (Worker worker: workers) {
            Solution solution = worker.mine(workBytes, Integer.parseInt(difficulty));
            String json = "{\"d\": " + solution.getDifficulty() + ", \"n\": \"" + solution.getNonce() + "\", \"h\": \"" + solution.getHash() + "\"}";
            System.out.println(apiConnect.validateWork(json));
        }
        return workData;
    }

    @Override
    public void run() {
        start();
    }
}
