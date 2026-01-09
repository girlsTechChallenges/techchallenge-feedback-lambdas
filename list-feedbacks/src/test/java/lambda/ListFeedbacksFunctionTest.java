package lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ListFeedbacksFunctionTest {

    private DynamoDbClient mockDynamoDB;
    private Context mockContext;
    private LambdaLogger mockLogger;

    @BeforeEach
    void setUp() {
        mockDynamoDB = mock(DynamoDbClient.class);
        mockContext = mock(Context.class);
        mockLogger = mock(LambdaLogger.class);
        when(mockContext.getLogger()).thenReturn(mockLogger);

        // Configurar variáveis de ambiente para os testes
        System.setProperty("TABLE_NAME", "FeedbacksTable");
        System.setProperty("AWS_REGION", "us-east-1");
    }

    @Test
    void handleRequestShouldReturnFeedbacksList() {
        // Preparar dados de resposta do DynamoDB
        Map<String, AttributeValue> item1 = new HashMap<>();
        item1.put("feedbackId", AttributeValue.builder().s("fb-123").build());
        item1.put("pk", AttributeValue.builder().s("FEEDBACK").build());
        item1.put("createdAt", AttributeValue.builder().s("2026-01-08T10:00:00Z").build());
        item1.put("descricao", AttributeValue.builder().s("Ótimo serviço").build());
        item1.put("nota", AttributeValue.builder().s("5").build());
        item1.put("urgencia", AttributeValue.builder().s("BAIXA").build());

        Map<String, AttributeValue> item2 = new HashMap<>();
        item2.put("feedbackId", AttributeValue.builder().s("fb-456").build());
        item2.put("pk", AttributeValue.builder().s("FEEDBACK").build());
        item2.put("createdAt", AttributeValue.builder().s("2026-01-08T11:00:00Z").build());
        item2.put("descricao", AttributeValue.builder().s("Precisa melhorar").build());
        item2.put("nota", AttributeValue.builder().s("2").build());
        item2.put("urgencia", AttributeValue.builder().s("ALTA").build());

        QueryResponse queryResponse = QueryResponse.builder()
                .items(item1, item2)
                .count(2)
                .build();

        when(mockDynamoDB.query(any(QueryRequest.class))).thenReturn(queryResponse);

        // Preparar evento de entrada (chamada direta da Lambda)
        Map<String, Object> event = new HashMap<>();
        event.put("startDate", "2026-01-01");
        event.put("endDate", "2026-01-10");

        // Executar
        ListFeedbacksFunction testFunction = new ListFeedbacksFunction() {
            @Override
            public Map<String, Object> handleRequest(Map<String, Object> event, Context context) {
                // Substituir o DynamoDB pelo mock
                try {
                    java.lang.reflect.Field ddbField = ListFeedbacksFunction.class.getDeclaredField("ddb");
                    ddbField.setAccessible(true);
                    ddbField.set(this, mockDynamoDB);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                return super.handleRequest(event, context);
            }
        };

        Map<String, Object> result = testFunction.handleRequest(event, mockContext);

        // Verificar
        assertNotNull(result);
        assertTrue(result.containsKey("items"));
        assertTrue(result.containsKey("count"));
        
        List<Map<String, Object>> items = (List<Map<String, Object>>) result.get("items");
        assertEquals(2, items.size());
        assertEquals("fb-123", items.get(0).get("feedbackId"));
        assertEquals("fb-456", items.get(1).get("feedbackId"));
        
        verify(mockDynamoDB, times(1)).query(any(QueryRequest.class));
    }

    @Test
    void handleRequestWithUrgencyFilterShouldReturnFilteredResults() {
        // Preparar dados de resposta do DynamoDB (apenas feedbacks críticos)
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("feedbackId", AttributeValue.builder().s("fb-456").build());
        item.put("pk", AttributeValue.builder().s("FEEDBACK").build());
        item.put("createdAt", AttributeValue.builder().s("2026-01-08T11:00:00Z").build());
        item.put("descricao", AttributeValue.builder().s("Precisa melhorar").build());
        item.put("nota", AttributeValue.builder().s("2").build());
        item.put("urgencia", AttributeValue.builder().s("ALTA").build());

        QueryResponse queryResponse = QueryResponse.builder()
                .items(item)
                .count(1)
                .build();

        when(mockDynamoDB.query(any(QueryRequest.class))).thenReturn(queryResponse);

        // Preparar evento de entrada com filtro de urgência
        Map<String, Object> event = new HashMap<>();
        event.put("startDate", "2026-01-01");
        event.put("endDate", "2026-01-10");
        event.put("urgency", "ALTA");

        // Executar
        ListFeedbacksFunction testFunction = new ListFeedbacksFunction() {
            @Override
            public Map<String, Object> handleRequest(Map<String, Object> event, Context context) {
                try {
                    java.lang.reflect.Field ddbField = ListFeedbacksFunction.class.getDeclaredField("ddb");
                    ddbField.setAccessible(true);
                    ddbField.set(this, mockDynamoDB);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                return super.handleRequest(event, context);
            }
        };

        Map<String, Object> result = testFunction.handleRequest(event, mockContext);

        // Verificar
        assertNotNull(result);
        List<Map<String, Object>> items = (List<Map<String, Object>>) result.get("items");
        assertEquals(1, items.size());
        assertEquals("ALTA", items.get(0).get("urgencia"));
    }

    @Test
    void handleRequestFromApiGatewayShouldReturnFormattedResponse() {
        // Preparar resposta do DynamoDB
        QueryResponse queryResponse = QueryResponse.builder()
                .items(Collections.emptyList())
                .count(0)
                .build();

        when(mockDynamoDB.query(any(QueryRequest.class))).thenReturn(queryResponse);

        // Preparar evento do API Gateway
        Map<String, Object> queryParams = new HashMap<>();
        queryParams.put("startDate", "2026-01-01");
        queryParams.put("endDate", "2026-01-10");

        Map<String, Object> event = new HashMap<>();
        event.put("httpMethod", "GET");
        event.put("queryStringParameters", queryParams);

        // Executar
        ListFeedbacksFunction testFunction = new ListFeedbacksFunction() {
            @Override
            public Map<String, Object> handleRequest(Map<String, Object> event, Context context) {
                try {
                    java.lang.reflect.Field ddbField = ListFeedbacksFunction.class.getDeclaredField("ddb");
                    ddbField.setAccessible(true);
                    ddbField.set(this, mockDynamoDB);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                return super.handleRequest(event, context);
            }
        };

        Map<String, Object> result = testFunction.handleRequest(event, mockContext);

        // Verificar formato de resposta do API Gateway
        assertNotNull(result);
        assertTrue(result.containsKey("statusCode"));
        assertTrue(result.containsKey("body"));
        assertEquals(200, result.get("statusCode"));
    }

    @Test
    void handleRequestWithEmptyResultShouldReturnEmptyList() {
        // Preparar resposta vazia do DynamoDB
        QueryResponse queryResponse = QueryResponse.builder()
                .items(Collections.emptyList())
                .count(0)
                .build();

        when(mockDynamoDB.query(any(QueryRequest.class))).thenReturn(queryResponse);

        Map<String, Object> event = new HashMap<>();
        event.put("startDate", "2026-01-01");
        event.put("endDate", "2026-01-10");

        // Executar
        ListFeedbacksFunction testFunction = new ListFeedbacksFunction() {
            @Override
            public Map<String, Object> handleRequest(Map<String, Object> event, Context context) {
                try {
                    java.lang.reflect.Field ddbField = ListFeedbacksFunction.class.getDeclaredField("ddb");
                    ddbField.setAccessible(true);
                    ddbField.set(this, mockDynamoDB);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                return super.handleRequest(event, context);
            }
        };

        Map<String, Object> result = testFunction.handleRequest(event, mockContext);

        // Verificar
        assertNotNull(result);
        List<Map<String, Object>> items = (List<Map<String, Object>>) result.get("items");
        assertEquals(0, items.size());
        assertEquals(0, result.get("count"));
    }
}
