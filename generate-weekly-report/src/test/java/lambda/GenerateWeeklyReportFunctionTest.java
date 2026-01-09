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
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GenerateWeeklyReportFunctionTest {

    @Mock
    private S3Client mockS3;
    
    @Mock
    private DynamoDbClient mockDynamoDB;
    
    @Mock
    private Context mockContext;
    
    @Mock
    private LambdaLogger mockLogger;
    
    private TestableGenerateWeeklyReportFunction function;

    @BeforeEach
    void setUp() {
        when(mockContext.getLogger()).thenReturn(mockLogger);

        // Configurar variáveis de ambiente
        System.setProperty("REPORTS_BUCKET", "test-reports-bucket");
        System.setProperty("TABLE_NAME", "test-feedbacks-table");
        System.setProperty("AWS_REGION", "us-east-1");
        
        // Criar função testável com mocks injetados
        function = new TestableGenerateWeeklyReportFunction(mockS3, mockDynamoDB);
    }
    
    // Classe interna para injetar os mocks
    private static class TestableGenerateWeeklyReportFunction extends GenerateWeeklyReportFunction {
        private final S3Client testS3;
        private final DynamoDbClient testDynamoDB;
        
        public TestableGenerateWeeklyReportFunction(S3Client s3, DynamoDbClient dynamoDB) {
            super();
            this.testS3 = s3;
            this.testDynamoDB = dynamoDB;
        }
        
        @Override
        public String handleRequest(Map<String, Object> input, Context context) {
            try {
                java.lang.reflect.Field s3Field = GenerateWeeklyReportFunction.class.getDeclaredField("s3");
                s3Field.setAccessible(true);
                s3Field.set(this, testS3);

                java.lang.reflect.Field dynamoDBField = GenerateWeeklyReportFunction.class.getDeclaredField("dynamoDB");
                dynamoDBField.setAccessible(true);
                dynamoDBField.set(this, testDynamoDB);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            return super.handleRequest(input, context);
        }
    }

    @Test
    void handleRequestShouldGenerateReportAndUploadToS3() {
        // Preparar dados de entrada - mock do DynamoDB
        Map<String, AttributeValue> item1 = new HashMap<>();
        item1.put("feedbackId", AttributeValue.builder().s("fb-123").build());
        item1.put("rating", AttributeValue.builder().n("5").build());
        item1.put("urgency", AttributeValue.builder().s("baixa").build());
        item1.put("createdAt", AttributeValue.builder().s("2026-01-08T10:00:00Z").build());
        item1.put("comment", AttributeValue.builder().s("Ótimo serviço").build());

        Map<String, AttributeValue> item2 = new HashMap<>();
        item2.put("feedbackId", AttributeValue.builder().s("fb-456").build());
        item2.put("rating", AttributeValue.builder().n("2").build());
        item2.put("urgency", AttributeValue.builder().s("alta").build());
        item2.put("createdAt", AttributeValue.builder().s("2026-01-08T11:00:00Z").build());
        item2.put("comment", AttributeValue.builder().s("Precisa melhorar").build());

        ScanResponse scanResponse = ScanResponse.builder()
            .items(item1, item2)
            .count(2)
            .build();

        when(mockDynamoDB.scan(any(ScanRequest.class))).thenReturn(scanResponse);

        // Mock do S3 - bucket existe
        when(mockS3.headBucket(any(HeadBucketRequest.class)))
            .thenReturn(HeadBucketResponse.builder().build());

        // Mock do upload
        when(mockS3.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
            .thenReturn(PutObjectResponse.builder().build());

        Map<String, Object> input = new HashMap<>();

        // Executar
        String result = function.handleRequest(input, mockContext);

        // Verificar
        assertNotNull(result);
        assertTrue(result.startsWith("weekly-report-"));
        assertTrue(result.endsWith(".txt"));
        
        verify(mockDynamoDB, times(1)).scan(any(ScanRequest.class));
        verify(mockS3, times(1)).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    void handleRequestWithEmptyFeedbacksShouldStillGenerateReport() {
        // Mock do DynamoDB vazio
        ScanResponse scanResponse = ScanResponse.builder()
            .items(Collections.emptyList())
            .count(0)
            .build();

        when(mockDynamoDB.scan(any(ScanRequest.class))).thenReturn(scanResponse);

        // Mock do S3
        when(mockS3.headBucket(any(HeadBucketRequest.class)))
            .thenReturn(HeadBucketResponse.builder().build());
        when(mockS3.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
            .thenReturn(PutObjectResponse.builder().build());

        Map<String, Object> input = new HashMap<>();

        // Executar
        String result = function.handleRequest(input, mockContext);

        // Verificar
        assertNotNull(result);
        verify(mockS3, times(1)).putObject(any(PutObjectRequest.class), any(RequestBody.class));
        verify(mockLogger, atLeastOnce()).log(contains("Nenhum feedback encontrado"));
    }

    @Test
    void handleRequestShouldCreateBucketIfNotExists() {
        // Mock do DynamoDB
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("feedbackId", AttributeValue.builder().s("fb-123").build());
        item.put("rating", AttributeValue.builder().n("3").build());
        item.put("urgency", AttributeValue.builder().s("media").build());
        item.put("createdAt", AttributeValue.builder().s("2026-01-08T10:00:00Z").build());

        ScanResponse scanResponse = ScanResponse.builder()
            .items(item)
            .count(1)
            .build();

        when(mockDynamoDB.scan(any(ScanRequest.class))).thenReturn(scanResponse);

        // Mock do S3 - bucket não existe
        when(mockS3.headBucket(any(HeadBucketRequest.class)))
            .thenThrow(NoSuchBucketException.builder().message("Bucket not found").build());
        
        when(mockS3.createBucket(any(CreateBucketRequest.class)))
            .thenReturn(CreateBucketResponse.builder().build());

        when(mockS3.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
            .thenReturn(PutObjectResponse.builder().build());

        Map<String, Object> input = new HashMap<>();

        // Executar
        String result = function.handleRequest(input, mockContext);

        // Verificar
        assertNotNull(result);
        verify(mockS3, times(1)).createBucket(any(CreateBucketRequest.class));
        verify(mockS3, times(1)).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    void handleRequestShouldCalculateCorrectStatistics() {
        // Mock do DynamoDB com feedbacks variados
        List<Map<String, AttributeValue>> items = new ArrayList<>();
        
        for (int i = 1; i <= 5; i++) {
            Map<String, AttributeValue> item = new HashMap<>();
            item.put("feedbackId", AttributeValue.builder().s("fb-" + i).build());
            item.put("rating", AttributeValue.builder().n(String.valueOf(i)).build());
            item.put("urgency", AttributeValue.builder().s(
                i <= 2 ? "alta" : (i == 3 ? "media" : "baixa")
            ).build());
            item.put("createdAt", AttributeValue.builder().s(
                "2026-01-08T" + String.format("%02d", 10 + i) + ":00:00Z"
            ).build());
            item.put("comment", AttributeValue.builder().s("Feedback " + i).build());
            items.add(item);
        }

        ScanResponse scanResponse = ScanResponse.builder()
            .items(items)
            .count(5)
            .build();

        when(mockDynamoDB.scan(any(ScanRequest.class))).thenReturn(scanResponse);

        // Mock do S3
        when(mockS3.headBucket(any(HeadBucketRequest.class)))
            .thenReturn(HeadBucketResponse.builder().build());
        when(mockS3.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
            .thenReturn(PutObjectResponse.builder().build());

        Map<String, Object> input = new HashMap<>();

        // Executar
        String result = function.handleRequest(input, mockContext);

        // Verificar
        assertNotNull(result);
        verify(mockS3, times(1)).putObject(any(PutObjectRequest.class), any(RequestBody.class));
        verify(mockLogger, atLeastOnce()).log(contains("Total de feedbacks encontrados: 5"));
    }
    
    @Test
    void handleRequestShouldHandleDynamoDBError() {
        // Mock do DynamoDB lançando exceção
        when(mockDynamoDB.scan(any(ScanRequest.class)))
            .thenThrow(DynamoDbException.builder().message("Table not found").build());

        Map<String, Object> input = new HashMap<>();

        // Verificar que lança exceção
        assertThrows(RuntimeException.class, () -> {
            function.handleRequest(input, mockContext);
        });

        verify(mockDynamoDB, times(1)).scan(any(ScanRequest.class));
        verify(mockS3, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
        verify(mockLogger, atLeastOnce()).log(contains("Erro"));
    }
    
    @Test
    void handleRequestShouldHandleS3UploadError() {
        // Mock do DynamoDB
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("feedbackId", AttributeValue.builder().s("fb-123").build());
        item.put("rating", AttributeValue.builder().n("4").build());
        item.put("urgency", AttributeValue.builder().s("baixa").build());
        item.put("createdAt", AttributeValue.builder().s("2026-01-08T10:00:00Z").build());

        ScanResponse scanResponse = ScanResponse.builder()
            .items(item)
            .count(1)
            .build();

        when(mockDynamoDB.scan(any(ScanRequest.class))).thenReturn(scanResponse);

        // Mock do S3 - upload falha
        when(mockS3.headBucket(any(HeadBucketRequest.class)))
            .thenReturn(HeadBucketResponse.builder().build());
        
        when(mockS3.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
            .thenThrow(S3Exception.builder().message("Access denied").build());

        Map<String, Object> input = new HashMap<>();

        // Verificar que lança exceção
        assertThrows(RuntimeException.class, () -> {
            function.handleRequest(input, mockContext);
        });

        verify(mockS3, times(1)).putObject(any(PutObjectRequest.class), any(RequestBody.class));
        verify(mockLogger, atLeastOnce()).log(contains("Erro"));
    }
    
    @Test
    void handleRequestShouldHandleBucketCreationFailure() {
        // Mock do DynamoDB
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("feedbackId", AttributeValue.builder().s("fb-123").build());
        item.put("rating", AttributeValue.builder().n("3").build());

        ScanResponse scanResponse = ScanResponse.builder()
            .items(item)
            .count(1)
            .build();

        when(mockDynamoDB.scan(any(ScanRequest.class))).thenReturn(scanResponse);

        // Mock do S3 - bucket não existe e criação falha
        when(mockS3.headBucket(any(HeadBucketRequest.class)))
            .thenThrow(NoSuchBucketException.builder().message("Bucket not found").build());
        
        when(mockS3.createBucket(any(CreateBucketRequest.class)))
            .thenThrow(S3Exception.builder().message("Permission denied").build());
        
        // Upload deve continuar mesmo com falha na criação do bucket
        when(mockS3.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
            .thenReturn(PutObjectResponse.builder().build());

        Map<String, Object> input = new HashMap<>();

        // Executar
        String result = function.handleRequest(input, mockContext);

        // Verificar que continuou mesmo com erro na criação do bucket
        assertNotNull(result);
        verify(mockS3, times(1)).createBucket(any(CreateBucketRequest.class));
        verify(mockS3, times(1)).putObject(any(PutObjectRequest.class), any(RequestBody.class));
        verify(mockLogger, atLeastOnce()).log(contains("AVISO"));
    }
    
    @Test
    void handleRequestShouldProcessFeedbacksWithMissingFields() {
        // Mock do DynamoDB com feedbacks sem alguns campos
        Map<String, AttributeValue> item1 = new HashMap<>();
        item1.put("feedbackId", AttributeValue.builder().s("fb-incomplete").build());
        // rating ausente
        item1.put("createdAt", AttributeValue.builder().s("2026-01-08T10:00:00Z").build());

        Map<String, AttributeValue> item2 = new HashMap<>();
        item2.put("feedbackId", AttributeValue.builder().s("fb-partial").build());
        item2.put("rating", AttributeValue.builder().n("3").build());
        // urgency ausente (deve usar "baixa" como padrão)
        item2.put("createdAt", AttributeValue.builder().s("2026-01-08T11:00:00Z").build());

        ScanResponse scanResponse = ScanResponse.builder()
            .items(item1, item2)
            .count(2)
            .build();

        when(mockDynamoDB.scan(any(ScanRequest.class))).thenReturn(scanResponse);

        // Mock do S3
        when(mockS3.headBucket(any(HeadBucketRequest.class)))
            .thenReturn(HeadBucketResponse.builder().build());
        when(mockS3.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
            .thenReturn(PutObjectResponse.builder().build());

        Map<String, Object> input = new HashMap<>();

        // Executar
        String result = function.handleRequest(input, mockContext);

        // Verificar
        assertNotNull(result);
        verify(mockS3, times(1)).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }
    
    @Test
    void handleRequestShouldGroupFeedbacksByDay() {
        // Mock do DynamoDB com feedbacks em diferentes dias
        Map<String, AttributeValue> item1 = new HashMap<>();
        item1.put("feedbackId", AttributeValue.builder().s("fb-1").build());
        item1.put("rating", AttributeValue.builder().n("4").build());
        item1.put("createdAt", AttributeValue.builder().s("2026-01-05T10:00:00Z").build());

        Map<String, AttributeValue> item2 = new HashMap<>();
        item2.put("feedbackId", AttributeValue.builder().s("fb-2").build());
        item2.put("rating", AttributeValue.builder().n("5").build());
        item2.put("createdAt", AttributeValue.builder().s("2026-01-05T15:00:00Z").build());

        Map<String, AttributeValue> item3 = new HashMap<>();
        item3.put("feedbackId", AttributeValue.builder().s("fb-3").build());
        item3.put("rating", AttributeValue.builder().n("3").build());
        item3.put("createdAt", AttributeValue.builder().s("2026-01-06T10:00:00Z").build());

        ScanResponse scanResponse = ScanResponse.builder()
            .items(item1, item2, item3)
            .count(3)
            .build();

        when(mockDynamoDB.scan(any(ScanRequest.class))).thenReturn(scanResponse);

        // Mock do S3
        when(mockS3.headBucket(any(HeadBucketRequest.class)))
            .thenReturn(HeadBucketResponse.builder().build());
        when(mockS3.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
            .thenReturn(PutObjectResponse.builder().build());

        Map<String, Object> input = new HashMap<>();

        // Executar
        String result = function.handleRequest(input, mockContext);

        // Verificar
        assertNotNull(result);
        verify(mockS3, times(1)).putObject(any(PutObjectRequest.class), any(RequestBody.class));
        // Verificar que o relatório contém agrupamento por dia
        verify(mockLogger, atLeastOnce()).log(contains("QUANTIDADE DE AVALIAÇÕES POR DIA"));
    }
    
    @Test
    void handleRequestShouldCalculateAverageRating() {
        // Mock do DynamoDB com feedbacks com notas variadas
        Map<String, AttributeValue> item1 = new HashMap<>();
        item1.put("rating", AttributeValue.builder().n("5").build());
        item1.put("createdAt", AttributeValue.builder().s("2026-01-08T10:00:00Z").build());

        Map<String, AttributeValue> item2 = new HashMap<>();
        item2.put("rating", AttributeValue.builder().n("3").build());
        item2.put("createdAt", AttributeValue.builder().s("2026-01-08T11:00:00Z").build());

        Map<String, AttributeValue> item3 = new HashMap<>();
        item3.put("rating", AttributeValue.builder().n("4").build());
        item3.put("createdAt", AttributeValue.builder().s("2026-01-08T12:00:00Z").build());

        ScanResponse scanResponse = ScanResponse.builder()
            .items(item1, item2, item3)
            .count(3)
            .build();

        when(mockDynamoDB.scan(any(ScanRequest.class))).thenReturn(scanResponse);

        // Mock do S3
        when(mockS3.headBucket(any(HeadBucketRequest.class)))
            .thenReturn(HeadBucketResponse.builder().build());
        when(mockS3.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
            .thenReturn(PutObjectResponse.builder().build());

        Map<String, Object> input = new HashMap<>();

        // Executar
        String result = function.handleRequest(input, mockContext);

        // Verificar
        assertNotNull(result);
        // Média = (5 + 3 + 4) / 3 = 4.0
        verify(mockLogger, atLeastOnce()).log(contains("Média geral das notas: 4"));
    }
    
    @Test
    void handleRequestShouldCountUrgencyDistribution() {
        // Mock do DynamoDB com diferentes urgências
        Map<String, AttributeValue> item1 = new HashMap<>();
        item1.put("rating", AttributeValue.builder().n("5").build());
        item1.put("urgency", AttributeValue.builder().s("baixa").build());
        item1.put("createdAt", AttributeValue.builder().s("2026-01-08T10:00:00Z").build());

        Map<String, AttributeValue> item2 = new HashMap<>();
        item2.put("rating", AttributeValue.builder().n("1").build());
        item2.put("urgency", AttributeValue.builder().s("alta").build());
        item2.put("createdAt", AttributeValue.builder().s("2026-01-08T11:00:00Z").build());

        Map<String, AttributeValue> item3 = new HashMap<>();
        item3.put("rating", AttributeValue.builder().n("3").build());
        item3.put("urgency", AttributeValue.builder().s("media").build());
        item3.put("createdAt", AttributeValue.builder().s("2026-01-08T12:00:00Z").build());

        Map<String, AttributeValue> item4 = new HashMap<>();
        item4.put("rating", AttributeValue.builder().n("2").build());
        item4.put("urgency", AttributeValue.builder().s("alta").build());
        item4.put("createdAt", AttributeValue.builder().s("2026-01-08T13:00:00Z").build());

        ScanResponse scanResponse = ScanResponse.builder()
            .items(item1, item2, item3, item4)
            .count(4)
            .build();

        when(mockDynamoDB.scan(any(ScanRequest.class))).thenReturn(scanResponse);

        // Mock do S3
        when(mockS3.headBucket(any(HeadBucketRequest.class)))
            .thenReturn(HeadBucketResponse.builder().build());
        when(mockS3.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
            .thenReturn(PutObjectResponse.builder().build());

        Map<String, Object> input = new HashMap<>();

        // Executar
        String result = function.handleRequest(input, mockContext);

        // Verificar
        assertNotNull(result);
        verify(mockLogger, atLeastOnce()).log(contains("DISTRIBUIÇÃO POR URGÊNCIA"));
        verify(mockLogger, atLeastOnce()).log(contains("Alta: 2"));
        verify(mockLogger, atLeastOnce()).log(contains("Média: 1"));
        verify(mockLogger, atLeastOnce()).log(contains("Baixa: 1"));
    }
    
    @Test
    void handleRequestShouldHandleInvalidRatings() {
        // Mock do DynamoDB com ratings inválidos
        Map<String, AttributeValue> item1 = new HashMap<>();
        item1.put("rating", AttributeValue.builder().n("5").build());
        item1.put("createdAt", AttributeValue.builder().s("2026-01-08T10:00:00Z").build());

        Map<String, AttributeValue> item2 = new HashMap<>();
        // rating com valor inválido que causará NumberFormatException
        item2.put("createdAt", AttributeValue.builder().s("2026-01-08T11:00:00Z").build());
        // Nota será null no feedback map

        ScanResponse scanResponse = ScanResponse.builder()
            .items(item1, item2)
            .count(2)
            .build();

        when(mockDynamoDB.scan(any(ScanRequest.class))).thenReturn(scanResponse);

        // Mock do S3
        when(mockS3.headBucket(any(HeadBucketRequest.class)))
            .thenReturn(HeadBucketResponse.builder().build());
        when(mockS3.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
            .thenReturn(PutObjectResponse.builder().build());

        Map<String, Object> input = new HashMap<>();

        // Executar - não deve lançar exceção
        String result = function.handleRequest(input, mockContext);

        // Verificar
        assertNotNull(result);
        verify(mockS3, times(1)).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }
}
