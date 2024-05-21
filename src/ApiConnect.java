import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.logging.Logger;

public class ApiConnect {
    private static final String BASE_URL = "https://projet-raizo-idmc.netlify.app/.netlify/functions";
    private static final String AUTH_TOKEN = "reclRPzXSOmGArkLi";
    private static final Logger LOG = Logger.getLogger(ApiConnect.class.getName());

    public String connectToApi(String function, String bodyData) {
        try {
            URL url = URI.create(BASE_URL + function).toURL();
            HttpURLConnection con = getUrlConnection(bodyData, url);
            System.out.println(bodyData);
            int responseCode = con.getResponseCode();
            System.out.println(con.getResponseMessage());
            if (responseCode == HttpURLConnection.HTTP_OK) {
                try (BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream(), StandardCharsets.UTF_8))) {
                    StringBuilder response = new StringBuilder();
                    String inputLine;

                    while ((inputLine = in.readLine()) != null) {
                        response.append(inputLine);
                    }

                    return response.toString();
                }
            } else {
                LOG.warning("Erreur: Réponse HTTP " + responseCode);
                return null;
            }
        } catch (IOException e) {
            LOG.warning("Erreur: " + e.getMessage());
            return null;
        }
    }

    private static HttpURLConnection getUrlConnection(String bodyData, URL url) throws IOException {
        HttpURLConnection con = (HttpURLConnection) url.openConnection();

        // Configurer l'en-tête d'autorisation
        con.setRequestProperty("Authorization", "Bearer " + AUTH_TOKEN);

        // Configurer la méthode HTTP
        if (bodyData == null || bodyData.isEmpty()) {
            con.setRequestMethod("GET");
            con.setDoOutput(false);
        } else {
            con.setRequestMethod("POST");
            con.setRequestProperty("Content-Type", "application/json");
            con.setRequestProperty("Accept", "application/json");
            con.setDoOutput(true);

            // Écrire les données du corps dans la connexion
            try (OutputStream os = con.getOutputStream()) {
                byte[] input = bodyData.getBytes("utf-8");
                os.write(input, 0, input.length);
            }
        }

        return con;
    }


    public String cancelTask() {
        return connectToApi("/cancel", null);
    }

    public String generateWork(String difficulty) {
        return connectToApi("/generate_work?d=" + difficulty, null);
    }

    public String validateWork(String workToValidate) {
        System.out.println("Validation du travail: " + workToValidate);
        return connectToApi("/validate_work", workToValidate);
    }
}
