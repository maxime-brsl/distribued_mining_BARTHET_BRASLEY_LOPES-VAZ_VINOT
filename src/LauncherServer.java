import java.util.Scanner;
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
                server.setStopSignalFalse();
            } else if (("status").equals(cmd)) {
                server.getWorkersStatus();
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
                    String difficulty = parts[1];
                    server.solveTask(difficulty);
                }
            } else {
                LOG.info("Commande inconnue");
            }
        } catch (Exception e) {
            LOG.warning("Aucun worker connecté");
        }
    }

    private void displayHelp() {
        System.out.println("Commandes disponibles:");
        System.out.println("cancel: Annuler la tâche");
        System.out.println("status: Afficher les informations sur les travailleurs connectés");
        System.out.println("help: Afficher l'aide");
        System.out.println("solve: Résoudre le problème");
    }

    public static void main(String[] args) {
        new LauncherServer().run();
    }
}
