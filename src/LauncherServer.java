import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

import static java.lang.System.exit;

/**
 * Classe LauncherServer
 * Permet de lancer le serveur et de faire l'interface utilisateur pour les commandes
 */
public class LauncherServer {
    private static final Logger LOG = Logger.getLogger(LauncherServer.class.getName());
    private static final Server server = new Server(1337);
    private final Scanner scanner = new Scanner(System.in);
    // ExecutorService pour exécuter les commandes en parallèle et éviter de bloquer le serveur, c'est un groupe de threads
    private final ExecutorService executorService = Executors.newCachedThreadPool();

    public void run() {
        new Thread(server).start();
        while (true) {
            final String commande = scanner.nextLine();
            if (("quit").equals(commande)) {
                exit(0);
            } else {
                executorService.submit(() -> processCommand(commande.trim()));
            }
        }
    }

    private void processCommand(final String cmd) {
        try {
            if (("cancel").equals(cmd)) {
                server.cancelTask();
            } else if (("status").equals(cmd)) {
                server.getWorkersStatus();
            } else if (("help").equals(cmd.trim())) {
                // Afficher l'aide
                displayHelp();
            } else if (cmd.startsWith("solve")) {
                handleSolveCommand(cmd);
            } else {
                LOG.info("Commande inconnue");
            }
        } catch (Exception e) {
            LOG.warning("Erreur lors de l'exécution de la commande: " + e.getMessage());
        }
    }

    private void displayHelp() {
        System.out.println(" • status - afficher des informations sur les travailleurs connectés");
        System.out.println(" • solve <d> - essayer de miner avec la difficulté spécifiée");
        System.out.println(" • cancel - annuler une tache");
        System.out.println(" • help - décrire les commandes disponibles");
        System.out.println(" • quit - mettre fin au programme et quitter");
    }

    private void handleSolveCommand(final String cmd) {
        // Récupérer la difficulté spécifiée par l'utilisateur
        String[] parts = cmd.split(" ");
        if (parts.length < 2) {
            LOG.info("Erreur: difficulté manquante");
        } else if (!parts[1].matches("\\d+")) {
            LOG.info("Erreur: la difficulté doit être un nombre");
        } else if (parts.length > 2) {
            LOG.info("Erreur: un seul paramètre possible pour cette commande");
        } else {
            String difficulty = parts[1];
            server.solveTask(difficulty);
        }
    }

    public static void main(String[] args) {
        new LauncherServer().run();
    }
}
