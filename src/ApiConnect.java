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
            int responseCode = con.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK || responseCode == 201) {
                try (BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream(), "UTF-8"))) {
                    StringBuilder response = new StringBuilder();
                    String inputLine;

                    while ((inputLine = in.readLine()) != null) {
                        response.append(inputLine);
                    }

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

    private static HttpURLConnection getUrlConnection(String bodyData, URL url) throws IOException {
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

    public String codeResponse(String code) {
        return switch (code) {
            case "400" -> "Erreur: Requête invalide";
            case "404" -> "Erreur: Ressource non trouvée";
            case "409" -> "Erreur: Conflit, la solution a déjà été validé pour ce travail";
            case "500" -> "Erreur: Erreur interne du serveur";
            default -> null;
        };
    }

    public byte[] generateWork(String difficulty) {
        String work = connectToApi("/generate_work?d=" + difficulty, null);
        if (codeResponse(work) != null) {
            System.out.println(codeResponse(work));
            return null;
        } else {
            System.out.println("Travail récupéré !");
            int startIndex = work.indexOf("\"data\":\"") + 8;
            int endIndex = work.indexOf("\"", startIndex);
            return work.substring(startIndex, endIndex).getBytes();
        }
    }

    public void validateWork(String workToValidate) {
        String response = connectToApi("/validate_work", workToValidate);
        if (codeResponse(response) != null) {
            System.out.println(codeResponse(response));
        } else {
            System.out.println("Travail validé !");
        }
    }
}
