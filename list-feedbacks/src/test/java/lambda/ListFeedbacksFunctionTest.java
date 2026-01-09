package lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ListFeedbacksFunctionTest {

    @Mock
    private DynamoDbClient mockDynamoDB;
    
    @Mock
    private Context mockContext;
    
    @Mock
    private LambdaLogger mockLogger;
    
    private TestableListFeedbacksFunction function;

    @BeforeEach
    void setUp() {
        when(mockContext.getLogger()).thenReturn(mockLogger);

        // Configurar variáveis de ambiente para os testes
        System.setProperty("TABLE_NAME", "FeedbacksTable");
        System.setProperty("AWS_REGION", "us-east-1");
        System.setProperty("DEFAULT_PAGE_SIZE", "100");
        
        // Criar função testável com DynamoDB mockado
        function = new TestableListFeedbacksFunction(mockDynamoDB);
    }
    
    // Classe interna para injetar o mock
    private static class TestableListFeedbacksFunction extends ListFeedbacksFunction {
        private final DynamoDbClient testDdb;
        
        public TestableListFeedbacksFunction(DynamoDbClient ddb) {
            super();
            this.testDdb = ddb;
        }
        
        @Override
        public Map<String, Object> handleRequest(Map<String, Object> event, Context context) {
            try {
                java.lang.reflect.Field ddbField = ListFeedbacksFunction.class.getDeclaredField("ddb");
                ddbField.setAccessible(true);
                ddbField.set(this, testDdb);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            return super.handleRequest(event, context);
        }
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
        Map<String, Object> result = function.handleRequest(event, mockContext);

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
        Map<String, Object> result = function.handleRequest(event, mockContext);

        // Verificar
        assertNotNull(result);
        List<Map<String, Object>> items = (List<Map<String, Object>>) result.get("items");
        assertEquals(1, items.size());
        assertEquals("ALTA", items.get(0).get("urgencia"));
        assertEquals("ALTA", result.get("urgency"));
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
        Map<String, Object> result = function.handleRequest(event, mockContext);

        // Verificar formato de resposta do API Gateway
        assertNotNull(result);
        assertTrue(result.containsKey("statusCode"));
        assertTrue(result.containsKey("body"));
        assertTrue(result.containsKey("headers"));
        assertEquals(200, result.get("statusCode"));
        
        Map<String, String> headers = (Map<String, String>) result.get("headers");
        assertEquals("application/json", headers.get("Content-Type"));
        assertEquals("*", headers.get("Access-Control-Allow-Origin"));
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
        Map<String, Object> result = function.handleRequest(event, mockContext);

        // Verificar
        assertNotNull(result);
        List<Map<String, Object>> items = (List<Map<String, Object>>) result.get("items");
        assertEquals(0, items.size());
        assertEquals(0, result.get("count"));
    }
    
    @Test
    void handleRequestWithNoDatesShouldUseDefaults() {
        // Preparar resposta do DynamoDB
        QueryResponse queryResponse = QueryResponse.builder()
                .items(Collections.emptyList())
                .count(0)
                .build();

        when(mockDynamoDB.query(any(QueryRequest.class))).thenReturn(queryResponse);

        // Evento sem datas
        Map<String, Object> event = new HashMap<>();

        // Executar
        Map<String, Object> result = function.handleRequest(event, mockContext);

        // Verificar que usou valores padrão
        assertNotNull(result);
        assertEquals("2020-01-01T00:00:00Z", result.get("startDate"));
        assertEquals("2030-12-31T23:59:59Z", result.get("endDate"));
    }
    
    @Test
    void handleRequestWithEmptyDatesShouldUseDefaults() {
        // Preparar resposta do DynamoDB
        QueryResponse queryResponse = QueryResponse.builder()
                .items(Collections.emptyList())
                .count(0)
                .build();

        when(mockDynamoDB.query(any(QueryRequest.class))).thenReturn(queryResponse);

        // Evento com datas vazias
        Map<String, Object> event = new HashMap<>();
        event.put("startDate", "");
        event.put("endDate", "");

        // Executar
        Map<String, Object> result = function.handleRequest(event, mockContext);

        // Verificar que usou valores padrão
        assertNotNull(result);
        assertEquals("2020-01-01T00:00:00Z", result.get("startDate"));
        assertEquals("2030-12-31T23:59:59Z", result.get("endDate"));
    }
    
    @Test
    void handleRequestWithNextTokenShouldPaginate() {
        // Preparar primeira página de resultados
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("feedbackId", AttributeValue.builder().s("fb-789").build());
        item.put("pk", AttributeValue.builder().s("FEEDBACK").build());
        item.put("createdAt", AttributeValue.builder().s("2026-01-09T10:00:00Z").build());

        Map<String, AttributeValue> lastKey = new HashMap<>();
        lastKey.put("feedbackId", AttributeValue.builder().s("fb-789").build());
        lastKey.put("pk", AttributeValue.builder().s("FEEDBACK").build());

        QueryResponse queryResponse = QueryResponse.builder()
                .items(item)
                .count(1)
                .lastEvaluatedKey(lastKey)
                .build();

        when(mockDynamoDB.query(any(QueryRequest.class))).thenReturn(queryResponse);

        // Evento com nextToken
        Map<String, Object> nextToken = new HashMap<>();
        nextToken.put("feedbackId", "fb-123");
        nextToken.put("pk", "FEEDBACK");

        Map<String, Object> event = new HashMap<>();
        event.put("startDate", "2026-01-01");
        event.put("endDate", "2026-01-10");
        event.put("nextToken", nextToken);

        // Executar
        Map<String, Object> result = function.handleRequest(event, mockContext);

        // Verificar
        assertNotNull(result);
        assertNotNull(result.get("nextToken"));
        Map<String, AttributeValue> returnedNextToken = (Map<String, AttributeValue>) result.get("nextToken");
        assertEquals("fb-789", returnedNextToken.get("feedbackId").s());
    }
    
    @Test
    void handleRequestWithComplexAttributesShouldConvertCorrectly() {
        // Preparar item com diferentes tipos de atributos
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("feedbackId", AttributeValue.builder().s("fb-999").build());
        item.put("pk", AttributeValue.builder().s("FEEDBACK").build());
        item.put("createdAt", AttributeValue.builder().s("2026-01-09T12:00:00Z").build());
        item.put("nota", AttributeValue.builder().n("5").build());
        item.put("ativo", AttributeValue.builder().bool(true).build());
        
        // Lista de tags
        List<AttributeValue> tags = Arrays.asList(
            AttributeValue.builder().s("urgente").build(),
            AttributeValue.builder().s("importante").build()
        );
        item.put("tags", AttributeValue.builder().l(tags).build());
        
        // Mapa aninhado
        Map<String, AttributeValue> metadata = new HashMap<>();
        metadata.put("source", AttributeValue.builder().s("web").build());
        metadata.put("version", AttributeValue.builder().n("1").build());
        item.put("metadata", AttributeValue.builder().m(metadata).build());
        
        // Valor nulo
        item.put("deletedAt", AttributeValue.builder().nul(true).build());

        QueryResponse queryResponse = QueryResponse.builder()
                .items(item)
                .count(1)
                .build();

        when(mockDynamoDB.query(any(QueryRequest.class))).thenReturn(queryResponse);

        Map<String, Object> event = new HashMap<>();
        event.put("startDate", "2026-01-01");
        event.put("endDate", "2026-01-10");

        // Executar
        Map<String, Object> result = function.handleRequest(event, mockContext);

        // Verificar conversão de tipos
        assertNotNull(result);
        List<Map<String, Object>> items = (List<Map<String, Object>>) result.get("items");
        assertEquals(1, items.size());
        
        Map<String, Object> convertedItem = items.get(0);
        assertEquals("fb-999", convertedItem.get("feedbackId"));
        assertEquals("5", convertedItem.get("nota"));
        assertEquals(true, convertedItem.get("ativo"));
        assertNotNull(convertedItem.get("tags"));
        assertNotNull(convertedItem.get("metadata"));
        assertNull(convertedItem.get("deletedAt"));
    }
    
    @Test
    void handleRequestApiGatewayWithNullQueryParamsShouldUseDefaults() {
        // Preparar resposta do DynamoDB
        QueryResponse queryResponse = QueryResponse.builder()
                .items(Collections.emptyList())
                .count(0)
                .build();

        when(mockDynamoDB.query(any(QueryRequest.class))).thenReturn(queryResponse);

        // Evento do API Gateway sem queryStringParameters
        Map<String, Object> event = new HashMap<>();
        event.put("httpMethod", "GET");
        event.put("queryStringParameters", null);

        // Executar
        Map<String, Object> result = function.handleRequest(event, mockContext);

        // Verificar
        assertNotNull(result);
        assertEquals(200, result.get("statusCode"));
        assertTrue(result.containsKey("body"));
    }
    
    @Test
    void handleRequestWithExceptionShouldReturnError() {
        // Simular erro no DynamoDB
        when(mockDynamoDB.query(any(QueryRequest.class)))
            .thenThrow(DynamoDbException.builder()
                .message("Table not found")
                .build());

        Map<String, Object> event = new HashMap<>();
        event.put("startDate", "2026-01-01");
        event.put("endDate", "2026-01-10");

        // Executar e verificar que lança exceção
        assertThrows(RuntimeException.class, () -> {
            function.handleRequest(event, mockContext);
        });
        
        verify(mockLogger, atLeastOnce()).log(contains("Error"));
    }
    
    @Test
    void handleRequestApiGatewayWithExceptionShouldReturn500() {
        // Simular erro no DynamoDB
        when(mockDynamoDB.query(any(QueryRequest.class)))
            .thenThrow(DynamoDbException.builder()
                .message("Internal error")
                .build());

        // Evento do API Gateway
        Map<String, Object> event = new HashMap<>();
        event.put("httpMethod", "GET");
        event.put("queryStringParameters", new HashMap<>());

        // Executar
        Map<String, Object> result = function.handleRequest(event, mockContext);

        // Verificar resposta de erro
        assertNotNull(result);
        assertEquals(500, result.get("statusCode"));
        assertTrue(result.containsKey("body"));
        
        String body = (String) result.get("body");
        assertTrue(body.contains("error"));
    }
    
    @Test
    void handleRequestWithRequestContextShouldBeDetectedAsApiGateway() {
        // Preparar resposta do DynamoDB
        QueryResponse queryResponse = QueryResponse.builder()
                .items(Collections.emptyList())
                .count(0)
                .build();

        when(mockDynamoDB.query(any(QueryRequest.class))).thenReturn(queryResponse);

        // Evento com requestContext (outro formato do API Gateway)
        Map<String, Object> event = new HashMap<>();
        event.put("requestContext", Map.of("requestId", "test-123"));
        event.put("queryStringParameters", new HashMap<>());

        // Executar
        Map<String, Object> result = function.handleRequest(event, mockContext);

        // Verificar formato API Gateway
        assertNotNull(result);
        assertTrue(result.containsKey("statusCode"));
        assertTrue(result.containsKey("body"));
        assertEquals(200, result.get("statusCode"));
    }
}

