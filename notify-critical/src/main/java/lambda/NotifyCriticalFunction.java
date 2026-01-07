package lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.apache.hc.core5.http.io.entity.StringEntity;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Objects;

public class NotifyCriticalFunction implements RequestHandler<Map<String,Object>, String> {

    @Override
    public String handleRequest(Map<String,Object> input, Context context) {
        Map<String,Object> detail = (Map<String,Object>) input.get("detail");

        FeedbackEvent event = new FeedbackEvent(
                (String) detail.get("feedbackId"),
                (String) detail.get("fullName"),
                (String) detail.get("category"),
                (String) detail.get("comment"),
                ((Number) detail.get("rating")).intValue(),
                (Boolean) detail.get("isCritical")
        );

        context.getLogger().log("Iniciando notify-critical para feedbackId=" + event.feedbackId());

        if (!Objects.equals(event.category(), "Critical")) {
            context.getLogger().log("Feedback não é crítico. Nenhum e-mail enviado.");
            return "Feedback não é crítico. Nenhum e-mail enviado.";
        }

        String to = "paivaag.developer@gmail.com";
        context.getLogger().log("Destinatário definido: " + to);

        String now = LocalDateTime.now(ZoneId.of("America/Sao_Paulo"))
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        String json = """
        {
          "from": {
            "email": "hello@demomailtrap.co",
            "name": "TechChallenge"
          },
          "to": [
            { "email": "%s" }
          ],
          "subject": "🚨 Feedback Recebido",
          "text": "ID: %s\\nNome: %s\\nCategoria: %s\\nComentario: %s\\nNota: %d\\nData: %s",
          "category": "Feedback Alert"
        }
        """.formatted(
                to,
                event.feedbackId(),
                event.fullName(),
                event.category(),
                event.comment(),
                event.rating(),
                now
        );

        context.getLogger().log("Payload JSON montado: " + json);

        try (CloseableHttpClient client = HttpClients.createDefault()) {
            HttpPost post = new HttpPost("https://send.api.mailtrap.io/api/send");
            post.setHeader("Authorization", "Bearer " + "c8f907eda4864e9689e66051ce5e0bcc");
            post.setHeader("Content-Type", "application/json; charset=UTF-8");
            post.setEntity(new StringEntity(json));

            context.getLogger().log("Enviando requisição para Mailtrap...");

            HttpClientResponseHandler<String> responseHandler = (ClassicHttpResponse response) -> {
                int status = response.getCode();
                context.getLogger().log("Resposta HTTP recebida: " + status);
                if (status >= 200 && status < 300) {
                    return "E-mail enviado via API Mailtrap.";
                } else {
                    return "Falha ao enviar e-mail. Código HTTP: " + status;
                }
            };

            String result = client.execute(post, responseHandler);
            context.getLogger().log("Resultado final: " + result);
            return result;
        } catch (Exception e) {
            context.getLogger().log("Erro ao enviar via API: " + e.getMessage());
            return "Erro: " + e.getMessage();
        }
    }
}