import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class LauncherSkeleton {

    private static final String BASE_URL = "https://projet-raizo-idmc.netlify.app/.netlify/functions";
    private static final String AUTH_TOKEN = "reclRPzXSOmGArkLi";

    public void run() throws Exception {

        // écoute les commandes
        boolean keepGoing = true;
        final BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        while(keepGoing) {
            final String commande = reader.readLine();
            if(commande == null) break;

            keepGoing = processCommand(commande.trim());
        }
    }

    private boolean processCommand(String cmd) throws Exception {
        if(("quit").equals(cmd)) {
            // TODO shutdown
            return false;
        }

        if(("cancel").equals(cmd)) {
            cancelTask();
        } else if(("status").equals(cmd)) {
            getWorkersStatus();
        } else if(("help").equals(cmd.trim())) {
            // Afficher l'aide
            System.out.println(" • status - display informations about connected workers");
            System.out.println(" • solve <d> - try to mine with given difficulty");
            System.out.println(" • cancel - cancel a task");
            System.out.println(" • help - describe available commands");
            System.out.println(" • quit - terminate pending work and quit");
        } else if(cmd.startsWith("solve")) {
            // Récupérer la difficulté spécifiée par l'utilisateur
            String[] parts = cmd.split(" ");
            if (parts.length < 2) {
                System.out.println("Usage: solve <difficulty>");
                return true;
            }
            int difficulty = Integer.parseInt(parts[1]);
            solveTask(difficulty);
        }

        return true;
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
                System.out.println("Task cancelled successfully.");
            } else {
                // Gérer les erreurs
                System.out.println("Failed to cancel task. Response code: " + responseCode);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void getWorkersStatus() {
        // Implémenter la logique pour obtenir les informations sur l'état des workers
    }

    private void solveTask(int difficulty) {
        // Implémenter la logique pour résoudre une tâche avec la difficulté spécifiée
    }

    public static void main(String[] args) throws Exception {
        new LauncherSkeleton().run();
    }
}
