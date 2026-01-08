package lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;

public class InsertFeedbackFunction implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private final DynamoDbClient dynamoDbClient;
    private final ObjectMapper mapper = new ObjectMapper();

    public InsertFeedbackFunction() {
        this.dynamoDbClient = DynamoDbClient.builder()
                .region(Region.US_EAST_1)
                .build();
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent input, Context context) {
        Map<Object, Object> body = new HashMap<>();

        if (input.getBody() != null) {
            try {
                body = mapper.readValue(input.getBody(), Map.class);
            } catch (JsonProcessingException e) {
                return buildResponse(400, Map.of("error", "Erro ao processar JSON de entrada"));
            }
        }

        // --- Recupera claims do Cognito Authorizer ---
        Map<String, Object> claims = null;
        try {
            Map<String, Object> authorizer = (Map<String, Object>) input.getRequestContext().getAuthorizer();
            claims = (Map<String, Object>) authorizer.get("claims");
        } catch (Exception e) {
            // log opcional
        }

        String email = claims != null ? (String) claims.get("email") : "sem email";

        // --- Monta item DynamoDB ---
        Map<String, AttributeValue> item = new HashMap<>();
        String feedbackId = UUID.randomUUID().toString();
        String createdAt = DateTimeFormatter.ISO_INSTANT.format(Instant.now());
        
        item.put("feedbackId", AttributeValue.builder().s(feedbackId).build());
        item.put("pk", AttributeValue.builder().s("FEEDBACK").build());
        item.put("createdAt", AttributeValue.builder().s(createdAt).build());
        item.put("fullName", AttributeValue.builder().s((String) body.getOrDefault("fullName", "undefined")).build());
        item.put("category", AttributeValue.builder().s((String) body.getOrDefault("category", "undefined")).build());
        item.put("comment", AttributeValue.builder().s((String) body.getOrDefault("comment", "empty")).build());
        item.put("rating", AttributeValue.builder().n(String.valueOf(body.getOrDefault("rating", 0))).build());
        item.put("urgency", AttributeValue.builder().s((String) body.getOrDefault("urgency", "baixa")).build());
        item.put("nota", AttributeValue.builder().n(String.valueOf(body.getOrDefault("nota", 0))).build());
        item.put("descricao", AttributeValue.builder().s((String) body.getOrDefault("descricao", "")).build());

        try {
            PutItemRequest request = PutItemRequest.builder()
                    .tableName("FeedbacksTable")
                    .item(item)
                    .build();

            dynamoDbClient.putItem(request);
        } catch (Exception e) {
            return buildResponse(500, Map.of("error", "Falha ao salvar feedback no banco"));
        }

        // --- Monta resposta JSON no formato desejado ---
        Map<String, Object> responseBody = new HashMap<>();
        responseBody.put("message", "Olá " + email + " seu feedback foi enviado com sucesso");
        responseBody.put("feedbackId", feedbackId);
        responseBody.put("createdAt", createdAt);

        return buildResponse(200, responseBody);
    }

    private APIGatewayProxyResponseEvent buildResponse(int statusCode, Map<String, Object> body) {
        String jsonResponse;
        try {
            jsonResponse = mapper.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            jsonResponse = "{\"error\":\"Falha ao gerar resposta JSON\"}";
        }

        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");

        return new APIGatewayProxyResponseEvent()
                .withStatusCode(statusCode)
                .withHeaders(headers)
                .withBody(jsonResponse);
    }
}