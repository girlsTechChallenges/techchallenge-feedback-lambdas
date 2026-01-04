package lambda;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemResponse;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class IngestFeedbackFunctionTest {

  @Test
  public void handleRequestShouldSaveFeedbackAndReturn200() throws Exception {
    DynamoDbClient mockDdb = mock(DynamoDbClient.class);
    when(mockDdb.putItem(any(PutItemRequest.class))).thenReturn(PutItemResponse.builder().build());

    IngestFeedbackFunction function = new IngestFeedbackFunction();

    Field field = IngestFeedbackFunction.class.getDeclaredField("dynamoDbClient");
    field.setAccessible(true);
    field.set(function, mockDdb);

    String body = "{\"fullName\":\"João Silva\",\"category\":\"UX\",\"comment\":\"Ótimo\",\"rating\":5}";
    APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent().withBody(body);

    APIGatewayProxyResponseEvent response = function.handleRequest(request, null);

    assertEquals(200, response.getStatusCode().intValue());
    assertTrue(response.getBody().contains("João Silva"));

    verify(mockDdb, times(1)).putItem(any(PutItemRequest.class));
  }
}