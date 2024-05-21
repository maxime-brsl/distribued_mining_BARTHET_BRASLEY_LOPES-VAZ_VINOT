import java.util.Scanner;
import java.util.logging.Logger;

import static java.lang.System.exit;

public class LauncherServer {

    private static final int SERVER_PORT = 1337;
    private static final Logger LOG = Logger.getLogger(LauncherServer.class.getName());
    private final Scanner scanner = new Scanner(System.in);
    private static final Server server = new Server(SERVER_PORT);

    public void run() {
        new Thread(server).start();
        // écoute les commandes
        while (true) {
            System.out.print("> ");
            final String commande = scanner.nextLine();
            if (("quit").equals(commande)) {
                exit(0);
            } else {
                processCommand(commande.trim());
            }
        }
    }

    private void processCommand(String cmd) {
        try {
            switch (cmd) {
                case "cancel":
                    // Annuler la tâche
                    break;
                case "status":
                    // Afficher les informations sur les travailleurs connectés
                    break;
                case "help":
                    // Afficher l'aide
                    displayHelp();
                    break;
                case "solve":
                    // Résoudre le problème
                    break;
                default:
                    LOG.info("Commande inconnue");
            }
        } catch (Exception e) {
            LOG.warning("Erreur: " + e.getMessage());
        }
    }

    private void displayHelp() {
        System.out.println("Commandes disponibles:");
        System.out.println("cancel: Annuler la tâche");
        System.out.println("status: Afficher les informations sur les travailleurs connectés");
        System.out.println("help: Afficher l'aide");
        System.out.println("solve: Résoudre le problème");
    }

    public static void main(String[] args) throws Exception {
        new LauncherServer().run();
    }
}
