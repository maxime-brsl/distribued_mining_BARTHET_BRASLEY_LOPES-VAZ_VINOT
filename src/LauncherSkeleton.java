import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.Socket;
import java.net.URL;
import java.util.Scanner;
import java.util.logging.Logger;

public class LauncherSkeleton {

    private static final String BASE_URL = "https://projet-raizo-idmc.netlify.app/.netlify/functions";
    private static final String AUTH_TOKEN = "reclRPzXSOmGArkLi";
    private static final int SERVER_PORT = 1337;
    private static final String SERVER_HOST = "localhost";
    private static final Logger LOG = Logger.getLogger(LauncherSkeleton.class.getName());
    private final Scanner scanner = new Scanner(System.in);

    public void run() throws Exception {

        // écoute les commandes
        boolean keepGoing = true;
        while (keepGoing) {
            System.out.print("> ");
            final String commande = scanner.nextLine();

            if (("quit").equals(commande)) {
                keepGoing = false;
            } else {
                processCommand(commande.trim());
            }
        }
    }

    private void processCommand(String cmd) {
        try {
            if (("cancel").equals(cmd)) {
                cancelTask();
            } else if (("status").equals(cmd)) {
                getWorkersStatus();
            } else if (("help").equals(cmd.trim())) {
                // Afficher l'aide
                System.out.println(" • status - afficher des informations sur les travailleurs connectés");
                System.out.println(" • solve <d> - essayer de miner avec la difficulté spécifiée");
                System.out.println(" • cancel - annuler une tache");
                System.out.println(" • help - décrire les commandes disponibles");
                System.out.println(" • quit - mettre fin au programme et quitter");
            } else if (cmd.startsWith("solve")) {
                // Récupérer la difficulté spécifiée par l'utilisateur
                String[] parts = cmd.split(" ");
                if (parts.length < 2) {
                    LOG.info("Erreur: difficulté manquante");
                } else {
                    int difficulty = Integer.parseInt(parts[1]);
                    solveTask(difficulty);
                }
            }
        } catch (Exception e) {
            LOG.warning("Erreur: " + e.getMessage());
        }
    }

    private void cancelTask() {
        try {
            URL url = new URL(BASE_URL + "/cancel_work");
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setRequestMethod("GET");
            con.setRequestProperty("Authorization", "Bearer " + AUTH_TOKEN);

            int responseCode = con.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                // Tâche annulée avec succès
                System.out.println("Tache annulée avec succès.");
            } else {
                // Gérer les erreurs
                LOG.warning("Error pour annuler la tâche. Code de réponse: " + responseCode);
            }
        } catch (IOException e) {
            LOG.warning("Erreur: " + e.getMessage());
        }
    }

    private void getWorkersStatus() {
        // Implémenter la logique pour obtenir les informations sur l'état des workers
    }

    private void solveTask(int difficulty) {
        try {
            Socket socket = new Socket(SERVER_HOST, SERVER_PORT);

            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            out.println("solve " + difficulty);

            String response;
            while ((response = in.readLine()) != null) {
                System.out.println("Reponse du serveur: " + response);
                System.out.print("> ");
                String commande = scanner.nextLine();
                processCommand(commande.trim());
            }
            socket.close();
        } catch (IOException e) {
            LOG.warning("Erreur: " + e.getMessage());
        }
    }

    public static void main(String[] args) throws Exception {
        new LauncherSkeleton().run();
    }
}
