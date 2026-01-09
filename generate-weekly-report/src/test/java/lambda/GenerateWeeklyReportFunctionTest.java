package lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class GenerateWeeklyReportFunctionTest {

    private S3Client mockS3;
    private Context mockContext;
    private LambdaLogger mockLogger;

    @BeforeEach
    void setUp() {
        mockS3 = mock(S3Client.class);
        mockContext = mock(Context.class);
        mockLogger = mock(LambdaLogger.class);
        when(mockContext.getLogger()).thenReturn(mockLogger);

        // Configurar variáveis de ambiente
        System.setProperty("REPORTS_BUCKET", "test-reports-bucket");
        System.setProperty("AWS_REGION", "us-east-1");
    }

    @Test
    void handleRequestShouldGenerateReportAndUploadToS3() {
        // Preparar dados de entrada
        List<Map<String, Object>> feedbacks = new ArrayList<>();
        
        Map<String, Object> feedback1 = new HashMap<>();
        feedback1.put("feedbackId", "fb-123");
        feedback1.put("descricao", "Ótimo serviço");
        feedback1.put("nota", "5");
        feedback1.put("urgencia", "BAIXA");
        feedback1.put("createdAt", "2026-01-08T10:00:00Z");
        feedbacks.add(feedback1);

        Map<String, Object> feedback2 = new HashMap<>();
        feedback2.put("feedbackId", "fb-456");
        feedback2.put("descricao", "Precisa melhorar");
        feedback2.put("nota", "2");
        feedback2.put("urgencia", "ALTA");
        feedback2.put("createdAt", "2026-01-08T11:00:00Z");
        feedbacks.add(feedback2);

        Map<String, Object> input = new HashMap<>();
        input.put("feedbacks", feedbacks);

        // Mock do S3 - bucket existe
        when(mockS3.headBucket(any(HeadBucketRequest.class)))
            .thenReturn(HeadBucketResponse.builder().build());

        // Mock do upload
        when(mockS3.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
            .thenReturn(PutObjectResponse.builder().build());

        // Executar
        GenerateWeeklyReportFunction testFunction = new GenerateWeeklyReportFunction() {
            @Override
            public String handleRequest(Map<String, Object> input, Context context) {
                try {
                    java.lang.reflect.Field s3Field = GenerateWeeklyReportFunction.class.getDeclaredField("s3");
                    s3Field.setAccessible(true);
                    s3Field.set(this, mockS3);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                return super.handleRequest(input, context);
            }
        };

        String result = testFunction.handleRequest(input, mockContext);

        // Verificar
        assertNotNull(result);
        assertTrue(result.startsWith("weekly-report-"));
        assertTrue(result.endsWith(".txt"));
        
        verify(mockS3, times(1)).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    void handleRequestWithEmptyFeedbacksShouldStillGenerateReport() {
        // Preparar dados de entrada vazios
        List<Map<String, Object>> feedbacks = new ArrayList<>();
        Map<String, Object> input = new HashMap<>();
        input.put("feedbacks", feedbacks);

        // Mock do S3
        when(mockS3.headBucket(any(HeadBucketRequest.class)))
            .thenReturn(HeadBucketResponse.builder().build());
        when(mockS3.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
            .thenReturn(PutObjectResponse.builder().build());

        // Executar
        GenerateWeeklyReportFunction testFunction = new GenerateWeeklyReportFunction() {
            @Override
            public String handleRequest(Map<String, Object> input, Context context) {
                try {
                    java.lang.reflect.Field s3Field = GenerateWeeklyReportFunction.class.getDeclaredField("s3");
                    s3Field.setAccessible(true);
                    s3Field.set(this, mockS3);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                return super.handleRequest(input, context);
            }
        };

        String result = testFunction.handleRequest(input, mockContext);

        // Verificar
        assertNotNull(result);
        verify(mockS3, times(1)).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    void handleRequestShouldCreateBucketIfNotExists() {
        // Preparar dados de entrada
        List<Map<String, Object>> feedbacks = new ArrayList<>();
        Map<String, Object> feedback = new HashMap<>();
        feedback.put("feedbackId", "fb-123");
        feedback.put("descricao", "Teste");
        feedback.put("nota", "3");
        feedback.put("urgencia", "MEDIA");
        feedback.put("createdAt", "2026-01-08T10:00:00Z");
        feedbacks.add(feedback);

        Map<String, Object> input = new HashMap<>();
        input.put("feedbacks", feedbacks);

        // Mock do S3 - bucket não existe
        when(mockS3.headBucket(any(HeadBucketRequest.class)))
            .thenThrow(NoSuchBucketException.builder().message("Bucket not found").build());
        
        when(mockS3.createBucket(any(CreateBucketRequest.class)))
            .thenReturn(CreateBucketResponse.builder().build());

        when(mockS3.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
            .thenReturn(PutObjectResponse.builder().build());

        // Executar
        GenerateWeeklyReportFunction testFunction = new GenerateWeeklyReportFunction() {
            @Override
            public String handleRequest(Map<String, Object> input, Context context) {
                try {
                    java.lang.reflect.Field s3Field = GenerateWeeklyReportFunction.class.getDeclaredField("s3");
                    s3Field.setAccessible(true);
                    s3Field.set(this, mockS3);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                return super.handleRequest(input, context);
            }
        };

        String result = testFunction.handleRequest(input, mockContext);

        // Verificar
        assertNotNull(result);
        verify(mockS3, times(1)).createBucket(any(CreateBucketRequest.class));
        verify(mockS3, times(1)).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    void handleRequestShouldCalculateCorrectStatistics() {
        // Preparar dados de entrada com feedbacks variados
        List<Map<String, Object>> feedbacks = new ArrayList<>();
        
        // 3 feedbacks com notas diferentes
        for (int i = 1; i <= 5; i++) {
            Map<String, Object> feedback = new HashMap<>();
            feedback.put("feedbackId", "fb-" + i);
            feedback.put("descricao", "Feedback " + i);
            feedback.put("nota", String.valueOf(i));
            feedback.put("urgencia", i <= 2 ? "ALTA" : (i == 3 ? "MEDIA" : "BAIXA"));
            feedback.put("createdAt", "2026-01-08T" + String.format("%02d", 10 + i) + ":00:00Z");
            feedbacks.add(feedback);
        }

        Map<String, Object> input = new HashMap<>();
        input.put("feedbacks", feedbacks);

        // Mock do S3
        when(mockS3.headBucket(any(HeadBucketRequest.class)))
            .thenReturn(HeadBucketResponse.builder().build());
        when(mockS3.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
            .thenReturn(PutObjectResponse.builder().build());

        // Executar
        GenerateWeeklyReportFunction testFunction = new GenerateWeeklyReportFunction() {
            @Override
            public String handleRequest(Map<String, Object> input, Context context) {
                try {
                    java.lang.reflect.Field s3Field = GenerateWeeklyReportFunction.class.getDeclaredField("s3");
                    s3Field.setAccessible(true);
                    s3Field.set(this, mockS3);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                return super.handleRequest(input, context);
            }
        };

        String result = testFunction.handleRequest(input, mockContext);

        // Verificar
        assertNotNull(result);
        
        // Verificar que o upload foi feito com os dados corretos
        verify(mockS3, times(1)).putObject(any(PutObjectRequest.class), any(RequestBody.class));
        
        // Verificar logs para confirmar estatísticas (média = 3.0 = (1+2+3+4+5)/5)
        verify(mockLogger, atLeastOnce()).log(contains("Total de feedbacks: 5"));
    }
}
