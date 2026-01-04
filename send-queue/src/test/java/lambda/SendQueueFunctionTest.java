package lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.events.DynamodbEvent;
import com.amazonaws.services.lambda.runtime.events.models.dynamodb.AttributeValue;
import com.amazonaws.services.lambda.runtime.events.models.dynamodb.StreamRecord;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.eventbridge.EventBridgeClient;
import software.amazon.awssdk.services.eventbridge.model.PutEventsRequest;
import software.amazon.awssdk.services.eventbridge.model.PutEventsResponse;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SendQueueFunctionTest {

    @Test
    void handleRequest_shouldPublishEvent() throws Exception {
        EventBridgeClient mockClient = mock(EventBridgeClient.class);
        when(mockClient.putEvents(any(PutEventsRequest.class))).thenReturn(PutEventsResponse.builder().build());

        SendQueueFunction function = new SendQueueFunction();
        Field field = SendQueueFunction.class.getDeclaredField("eventBridgeClient");
        field.setAccessible(true);
        field.set(function, mockClient);

        var event = getDynamodbEvent();

        Context mockContext = mock(Context.class);
        LambdaLogger logger = mock(LambdaLogger.class);
        when(mockContext.getLogger()).thenReturn(logger);

        String result = function.handleRequest(event, mockContext);

        assertEquals("Eventos processados com sucesso", result);

        ArgumentCaptor<PutEventsRequest> captor = ArgumentCaptor.forClass(PutEventsRequest.class);
        verify(mockClient, times(1)).putEvents(captor.capture());

        String detail = captor.getValue().entries().getFirst().detail();
        assertTrue(detail.contains("\"fullName\":\"João Silva\""));
        assertTrue(detail.contains("\"rating\":1"));
        assertTrue(detail.contains("\"isCritical\":true"));
    }

    private DynamodbEvent getDynamodbEvent() {
        DynamodbEvent event = new DynamodbEvent();
        DynamodbEvent.DynamodbStreamRecord record = new DynamodbEvent.DynamodbStreamRecord();
        StreamRecord streamRecord = new StreamRecord();

        Map<String, AttributeValue> newImage = new HashMap<>();
        AttributeValue id = new AttributeValue();
        id.setS("id-1");
        AttributeValue fullName = new AttributeValue();
        fullName.setS("João Silva");
        AttributeValue category = new AttributeValue();
        category.setS("UX");
        AttributeValue comment = new AttributeValue();
        comment.setS("Ótimo");
        AttributeValue rating = new AttributeValue();
        rating.setN("1"); // crítico

        newImage.put("feedbackId", id);
        newImage.put("fullName", fullName);
        newImage.put("category", category);
        newImage.put("comment", comment);
        newImage.put("rating", rating);

        streamRecord.setNewImage(newImage);
        record.setDynamodb(streamRecord);
        record.setEventName("INSERT");
        event.setRecords(Collections.singletonList(record));
        return event;
    }
}