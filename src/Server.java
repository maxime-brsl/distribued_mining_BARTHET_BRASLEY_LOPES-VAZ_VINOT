import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public class Server implements Runnable{
    private ServerSocket serverSocket;
    private List<Worker> workers;
    private final ApiConnect apiConnect;
    private static final Logger LOG = Logger.getLogger(Server.class.getName());

    public Server(final int port) {
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
        //TODO : Annuler la tache en cours du worker souhaité
    }

    public void getWorkersStatus() {
        //TODO : Récupérer le status des workers
    }

    public void solveTask(final String difficulty) {
        byte[] work = apiConnect.generateWork(difficulty);
        if (work == null) {
            return;
        }
        for (int i = 0; i < workers.size(); i++) {
            Solution solution = workers.get(i).mine(work, Integer.parseInt(difficulty), i, workers.size());
            String json = "{\"d\": " + solution.getDifficulty() + ", \"n\": \"" + solution.getNonce() + "\", \"h\": \"" + solution.getHash() + "\"}";
            System.out.println(json);
            apiConnect.validateWork(json);
        }
    }

    @Override
    public void run() {
        start();
    }
}
