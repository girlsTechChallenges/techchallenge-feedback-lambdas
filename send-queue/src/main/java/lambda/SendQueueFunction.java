package lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.DynamodbEvent;
import com.amazonaws.services.lambda.runtime.events.models.dynamodb.AttributeValue;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.eventbridge.EventBridgeClient;
import software.amazon.awssdk.services.eventbridge.model.PutEventsRequest;
import software.amazon.awssdk.services.eventbridge.model.PutEventsRequestEntry;
import software.amazon.awssdk.services.eventbridge.model.PutEventsResponse;

import java.time.Instant;
import java.util.Map;

/**
 * Lambda function to process DynamoDB stream events and send them to EventBridge.
 */

public class SendQueueFunction implements RequestHandler<DynamodbEvent, String> {

    private final EventBridgeClient eventBridgeClient;

    public SendQueueFunction() {
        this.eventBridgeClient = EventBridgeClient.builder()
                .region(Region.US_EAST_1)
                .build();
    }

    @Override
    public String handleRequest(DynamodbEvent event, Context context) {
        for (DynamodbEvent.DynamodbStreamRecord record : event.getRecords()) {
            if ("INSERT".equals(record.getEventName())) {
                String detailJson = getDetailJson(record);

                PutEventsRequestEntry entry = PutEventsRequestEntry.builder()
                        .source("feedback.created")
                        .detailType("FeedbackCreated")
                        .detail(detailJson)
                        .eventBusName("default")
                        .build();

                PutEventsRequest request = PutEventsRequest.builder()
                        .entries(entry)
                        .build();

                context.getLogger().log("Sending event to EventBridge" + request);

                PutEventsResponse response = eventBridgeClient.putEvents(request);
                context.getLogger().log("Evento publicado: " + response.toString());
            }
        }
        return "Eventos processados com sucesso";
    }

    private String getDetailJson(DynamodbEvent.DynamodbStreamRecord record) {
        Map<String, AttributeValue> newImage = record.getDynamodb().getNewImage();

        String feedbackId = newImage.get("feedbackId").getS();
        String fullName   = newImage.get("fullName").getS();
        String category   = newImage.get("category").getS();
        String comment    = newImage.get("comment").getS();
        int rating        = Integer.parseInt(newImage.get("rating").getN());

        boolean isCritical = "Critical".equalsIgnoreCase(category) || rating <= 2;
        String createdAt   = Instant.now().toString();

        return String.format(
                "{\"feedbackId\":\"%s\",\"fullName\":\"%s\",\"category\":\"%s\",\"comment\":\"%s\",\"rating\":%d,\"isCritical\":%s,\"createdAt\":\"%s\"}",
                feedbackId, fullName, category, comment, rating, isCritical, createdAt
        );
    }
}