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

/***
 * Lambda function to ingest feedback data and store it in DynamoDB.
 */
public class IngestFeedbackFunction implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private final DynamoDbClient dynamoDbClient;
    private final ObjectMapper mapper = new ObjectMapper();

    public IngestFeedbackFunction() {
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
                context.getLogger().log("Error parser JSON: " + e.getMessage());
                return new APIGatewayProxyResponseEvent()
                        .withStatusCode(400)
                        .withBody("Erro ao processar JSON de entrada");
            }
        }

        Map<String, AttributeValue> item = new HashMap<>();
        item.put("feedbackId", AttributeValue.builder().s(UUID.randomUUID().toString()).build());
        item.put("fullName", AttributeValue.builder().s((String) body.getOrDefault("fullName", "undefined")).build());
        item.put("category", AttributeValue.builder().s((String) body.getOrDefault("category", "undefined")).build());
        item.put("comment", AttributeValue.builder().s((String) body.getOrDefault("comment", "empty")).build());
        item.put("rating", AttributeValue.builder().n(String.valueOf(body.getOrDefault("rating", 0))).build());

        String createdAt = DateTimeFormatter.ISO_INSTANT.format(Instant.now());
        item.put("createdAt", AttributeValue.builder().s(createdAt).build());

        PutItemRequest request = PutItemRequest.builder()
                .tableName("FeedbacksTable")
                .item(item)
                .build();

        dynamoDbClient.putItem(request);

        return new APIGatewayProxyResponseEvent()
                .withStatusCode(200)
                .withBody("Feedback salvo com sucesso para: " + request);
    }
}