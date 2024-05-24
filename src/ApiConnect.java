import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.logging.Logger;

/**
 * Classe ApiConnect
 * Permet les intéractions avec l'API
 */
public class ApiConnect {
    private static final Logger LOG = Logger.getLogger(ApiConnect.class.getName());
    private static final String BASE_URL = "https://projet-raizo-idmc.netlify.app/.netlify/functions";
    private static final String AUTH_TOKEN = "reclRPzXSOmGArkLi";

    /**
     * Se connecter à l'API
     *
     * @param function fonction à appeler
     * @param bodyData données à envoyer
     * @return réponse de l'API
     */
    public String connectToApi(final String function, final String bodyData) {
        try {
            URL url = URI.create(BASE_URL + function).toURL();
            HttpURLConnection con = getUrlConnection(bodyData, url);
            int responseCode = con.getResponseCode();
            // Si la réponse est OK ou Created dans le cas de la génération de travail
            if (responseCode == HttpURLConnection.HTTP_OK || responseCode == 201) {
                try (BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()))) {
                    StringBuilder response = new StringBuilder();
                    String inputLine;

                    // Lire la réponse
                    while ((inputLine = in.readLine()) != null) {
                        response.append(inputLine);
                    }
                    // Retourner la réponse sous forme de chaîne

                    return response.toString();
                }
            } else {
                return String.valueOf(responseCode);
            }
        } catch (IOException e) {
            LOG.warning("Erreur: " + e.getMessage());
            return null;
        }
    }

    /**
     * Initialise et configure une connexion HTTP en fonction de l'URL et des données fournies.
     *
     * @param bodyData Les données à envoyer dans le corps de la requête. Si nulles ou vides, une requête GET est effectuée.
     * @param url L'URL à laquelle se connecter.
     * @return HttpURLConnection initialisé et configuré.
     */
    private static HttpURLConnection getUrlConnection(final String bodyData, final URL url) throws IOException {
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setRequestProperty("Authorization", "Bearer " + AUTH_TOKEN);

        if (bodyData == null || bodyData.isEmpty()) {
            con.setRequestMethod("GET");
            con.setDoOutput(false);
        } else {
            con.setRequestMethod("POST");
            con.setRequestProperty("Content-Type", "application/json");
            con.setRequestProperty("Accept", "application/json");
            con.setDoOutput(true);

            try (OutputStream os = con.getOutputStream()) {
                byte[] input = bodyData.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }
        }

        return con;
    }

    /**
     * Affiche proprement les messages d'erreur en fonction du code de réponse
     *
     * @param code code de réponse
     * @return message d'erreur
     */
    public String codeResponse(final String code) {
        return switch (code) {
            case "400" -> "Erreur: Requête invalide";
            case "404" -> "Erreur: Ressource non trouvée";
            case "409" -> "Erreur: Conflit, la solution a déjà été validé pour ce travail";
            case "500" -> "Erreur: Erreur interne du serveur";
            default -> null;
        };
    }

    /**
     * Générer un travail à partir de l'API
     *
     * @param difficulty difficulté du travail
     * @return travail généré
     */
    public byte[] generateWork(final String difficulty) {
        String work = connectToApi("/generate_work?d=" + difficulty, null);
        if (codeResponse(work) != null) {
            System.out.println(codeResponse(work));
            return null;
        } else {
            System.out.println("Travail récupéré !");

            //On récupère un format JSON, on extrait donc la donnée de travail
            int startIndex = work.indexOf("\"data\":\"") + 8;
            int endIndex = work.indexOf("\"", startIndex);
            return work.substring(startIndex, endIndex).getBytes();
        }
    }

    /**
     * Valider un travail à partir de l'API
     *
     * @param workToValidate travail à valider
     */
    public void validateWork(final String workToValidate) {
        String response = connectToApi("/validate_work", workToValidate);
        if (codeResponse(response) != null) {
            System.out.println(codeResponse(response));
        } else {
            System.out.println("Travail validé !");
        }
    }
}
