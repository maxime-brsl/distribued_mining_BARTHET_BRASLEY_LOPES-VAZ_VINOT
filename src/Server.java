import java.io.IOException;
import java.net.*;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public class Server implements Runnable{
    private static final String BASE_URL = "https://projet-raizo-idmc.netlify.app/.netlify/functions";
    private static final String AUTH_TOKEN = "reclRPzXSOmGArkLi";
    private ServerSocket serverSocket;
    private List<Worker> workers;
    private static final Logger LOG = Logger.getLogger(Server.class.getName());

    public Server(int port) {
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
        try {
            URL url = URI.create(BASE_URL + "/cancel_work").toURL();
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setRequestMethod("GET");
            con.setRequestProperty("Authorization", "Bearer " + AUTH_TOKEN);

            int responseCode = con.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                //TODO : Annuler la tâche sur le worker
                System.out.println("Tache annulée avec succès.");
            } else {
                // Gérer les erreurs
                LOG.warning("Error pour annuler la tâche. Code de réponse: " + responseCode);
            }
        } catch (IOException e) {
            LOG.warning("Erreur: " + e.getMessage());
        }
    }

    public void getWorkersStatus() {
        //TODO : Récupérer le status des workers
    }

    public void solveTask(final int difficulty) {
        //TODO : Résoudre la tâche
    }

    @Override
    public void run() {
        start();
    }
}
