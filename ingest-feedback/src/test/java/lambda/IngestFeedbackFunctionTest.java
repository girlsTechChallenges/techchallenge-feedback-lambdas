package lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemResponse;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class IngestFeedbackFunctionTest {

  @Mock
  private DynamoDbClient mockDynamoDbClient;

  @Mock
  private Context mockContext;

  @Mock
  private LambdaLogger mockLogger;

  private IngestFeedbackFunction function;
  private final ObjectMapper mapper = new ObjectMapper();

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    when(mockContext.getLogger()).thenReturn(mockLogger);

    // Injetar o mock do DynamoDB client
    function = new IngestFeedbackFunction();
    try {
      var field = IngestFeedbackFunction.class.getDeclaredField("dynamoDbClient");
      field.setAccessible(true);
      field.set(function, mockDynamoDbClient);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  void testHandleRequest_Success() {
    // Arrange
    APIGatewayProxyRequestEvent request = createValidRequest();
    when(mockDynamoDbClient.putItem(any(PutItemRequest.class)))
            .thenReturn(PutItemResponse.builder().build());

    // Act
    APIGatewayProxyResponseEvent response = function.handleRequest(request, mockContext);

    // Assert
    assertEquals(200, response.getStatusCode());
    assertTrue(response.getBody().contains("seu feedback foi enviado com sucesso"));
    assertTrue(response.getBody().contains("feedbackId"));
    assertEquals("application/json", response.getHeaders().get("Content-Type"));

    verify(mockDynamoDbClient).putItem(any(PutItemRequest.class));
  }

  @Test
  void testHandleRequest_WithCognitoClaims() throws Exception {
    // Arrange
    APIGatewayProxyRequestEvent request = createValidRequestWithClaims();
    when(mockDynamoDbClient.putItem(any(PutItemRequest.class)))
            .thenReturn(PutItemResponse.builder().build());

    // Act
    APIGatewayProxyResponseEvent response = function.handleRequest(request, mockContext);

    // Assert
    assertEquals(200, response.getStatusCode());

    Map<String, Object> responseBody = mapper.readValue(response.getBody(), Map.class);
    assertTrue(responseBody.get("message").toString().contains("test@example.com"));
    assertNotNull(responseBody.get("feedbackId"));
    assertNotNull(responseBody.get("createdAt"));
  }

  @Test
  void testHandleRequest_NullBody() {
    // Arrange
    APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent();
    request.setBody(null);
    when(mockDynamoDbClient.putItem(any(PutItemRequest.class)))
            .thenReturn(PutItemResponse.builder().build());

    // Act
    APIGatewayProxyResponseEvent response = function.handleRequest(request, mockContext);

    // Assert
    assertEquals(200, response.getStatusCode());
    assertTrue(response.getBody().contains("sem email"));
  }

  @Test
  void testHandleRequest_InvalidJson() {
    // Arrange
    APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent();
    request.setBody("invalid-json");

    // Act
    APIGatewayProxyResponseEvent response = function.handleRequest(request, mockContext);

    // Assert
    assertEquals(400, response.getStatusCode());
    assertTrue(response.getBody().contains("Erro ao processar JSON de entrada"));
    verifyNoInteractions(mockDynamoDbClient);
  }

  @Test
  void testHandleRequest_DynamoDbError() {
    // Arrange
    APIGatewayProxyRequestEvent request = createValidRequest();
    when(mockDynamoDbClient.putItem(any(PutItemRequest.class)))
            .thenThrow(new RuntimeException("DynamoDB error"));

    // Act
    APIGatewayProxyResponseEvent response = function.handleRequest(request, mockContext);

    // Assert
    assertEquals(500, response.getStatusCode());
    assertTrue(response.getBody().contains("Falha ao salvar feedback no banco"));
  }

  @Test
  void testHandleRequest_EmptyBody() {
    // Arrange
    APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent();
    request.setBody("");

    // Act
    APIGatewayProxyResponseEvent response = function.handleRequest(request, mockContext);

    // Assert
    assertEquals(400, response.getStatusCode()); // Mudança aqui: de 200 para 400
    assertTrue(response.getBody().contains("Erro ao processar JSON de entrada"));
    verifyNoInteractions(mockDynamoDbClient); // Não deve interagir com DynamoDB
  }

  @Test
  void testHandleRequest_WithoutAuthorizer() {
    // Arrange
    APIGatewayProxyRequestEvent request = createValidRequest();
    APIGatewayProxyRequestEvent.ProxyRequestContext context =
            new APIGatewayProxyRequestEvent.ProxyRequestContext();
    request.setRequestContext(context);

    when(mockDynamoDbClient.putItem(any(PutItemRequest.class)))
            .thenReturn(PutItemResponse.builder().build());

    // Act
    APIGatewayProxyResponseEvent response = function.handleRequest(request, mockContext);

    // Assert
    assertEquals(200, response.getStatusCode());
    assertTrue(response.getBody().contains("sem email"));
  }

  @Test
  void testHandleRequest_WithPartialData() {
    // Arrange
    APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent();
    Map<String, Object> body = new HashMap<>();
    body.put("comment", "Apenas comentário");

    try {
      request.setBody(mapper.writeValueAsString(body));
    } catch (Exception e) {
      throw new RuntimeException(e);
    }

    when(mockDynamoDbClient.putItem(any(PutItemRequest.class)))
            .thenReturn(PutItemResponse.builder().build());

    // Act
    APIGatewayProxyResponseEvent response = function.handleRequest(request, mockContext);

    // Assert
    assertEquals(200, response.getStatusCode());
    assertTrue(response.getBody().contains("seu feedback foi enviado com sucesso"));

    // Capturar o request enviado para verificar os valores
    ArgumentCaptor<PutItemRequest> requestCaptor = ArgumentCaptor.forClass(PutItemRequest.class);
    verify(mockDynamoDbClient).putItem(requestCaptor.capture());

    PutItemRequest capturedRequest = requestCaptor.getValue();
    Map<String, software.amazon.awssdk.services.dynamodb.model.AttributeValue> item = capturedRequest.item();

    assertEquals("undefined", item.get("fullName").s());
    assertEquals("undefined", item.get("category").s());
    assertEquals("Apenas comentário", item.get("comment").s());
    assertEquals("FeedbacksTable", capturedRequest.tableName());
  }

  @Test
  void testBuildResponse_JsonSerializationError() {
    // Este teste verifica se o método buildResponse trata erros de serialização
    // Não podemos testar diretamente, mas podemos verificar que não quebra
    APIGatewayProxyRequestEvent request = createValidRequest();
    when(mockDynamoDbClient.putItem(any(PutItemRequest.class)))
            .thenReturn(PutItemResponse.builder().build());

    APIGatewayProxyResponseEvent response = function.handleRequest(request, mockContext);

    assertNotNull(response);
    assertNotNull(response.getBody());
    assertEquals("application/json", response.getHeaders().get("Content-Type"));
  }

  private APIGatewayProxyRequestEvent createValidRequest() {
    APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent();

    Map<String, Object> body = new HashMap<>();
    body.put("fullName", "João Silva");
    body.put("category", "suggestion");
    body.put("comment", "Ótimo sistema!");
    body.put("rating", 5);

    try {
      request.setBody(mapper.writeValueAsString(body));
    } catch (Exception e) {
      throw new RuntimeException(e);
    }

    return request;
  }

  private APIGatewayProxyRequestEvent createValidRequestWithClaims() {
    APIGatewayProxyRequestEvent request = createValidRequest();

    // Simular claims do Cognito
    Map<String, Object> claims = new HashMap<>();
    claims.put("email", "test@example.com");
    claims.put("name", "Test User");
    claims.put("sub", "123e4567-e89b-12d3-a456-426614174000");

    Map<String, Object> authorizer = new HashMap<>();
    authorizer.put("claims", claims);

    APIGatewayProxyRequestEvent.ProxyRequestContext requestContext =
            new APIGatewayProxyRequestEvent.ProxyRequestContext();
    requestContext.setAuthorizer(authorizer);

    request.setRequestContext(requestContext);

    return request;
  }
}