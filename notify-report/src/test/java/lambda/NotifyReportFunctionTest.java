package lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.SendEmailRequest;
import software.amazon.awssdk.services.ses.model.SendEmailResponse;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class NotifyReportFunctionTest {

    private S3Client mockS3;
    private SesClient mockSes;
    private Context mockContext;
    private LambdaLogger mockLogger;

    @BeforeEach
    void setUp() {
        mockS3 = mock(S3Client.class);
        mockSes = mock(SesClient.class);
        mockContext = mock(Context.class);
        mockLogger = mock(LambdaLogger.class);
        when(mockContext.getLogger()).thenReturn(mockLogger);

        // Configurar variáveis de ambiente
        System.setProperty("REPORTS_BUCKET", "test-reports-bucket");
        System.setProperty("RECIPIENT_EMAIL", "test@example.com");
        System.setProperty("SOURCE_EMAIL", "no-reply@example.com");
        System.setProperty("AWS_REGION", "us-east-1");
    }

    @Test
    void handleRequestShouldReadFromS3AndSendEmail() {
        // Preparar dados de entrada
        Map<String, Object> input = new HashMap<>();
        input.put("reportKey", "weekly-report-2026-01-08.txt");

        // Mock do conteúdo do relatório do S3
        String reportContent = "=== RELATÓRIO SEMANAL ===\nTotal de feedbacks: 10\nMédia: 4.5";
        ResponseBytes<GetObjectResponse> responseBytes = ResponseBytes.fromByteArray(
            GetObjectResponse.builder().build(),
            reportContent.getBytes(StandardCharsets.UTF_8)
        );

        when(mockS3.getObject(any(GetObjectRequest.class), any(ResponseTransformer.class)))
            .thenReturn(responseBytes);

        // Mock do SES
        when(mockSes.sendEmail(any(SendEmailRequest.class)))
            .thenReturn(SendEmailResponse.builder()
                .messageId("test-message-id")
                .build());

        // Executar
        NotifyReportFunction testFunction = new NotifyReportFunction() {
            @Override
            public String handleRequest(Map<String, Object> input, Context context) {
                try {
                    java.lang.reflect.Field s3Field = NotifyReportFunction.class.getDeclaredField("s3");
                    s3Field.setAccessible(true);
                    s3Field.set(this, mockS3);

                    java.lang.reflect.Field sesField = NotifyReportFunction.class.getDeclaredField("ses");
                    sesField.setAccessible(true);
                    sesField.set(this, mockSes);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                return super.handleRequest(input, context);
            }
        };

        String result = testFunction.handleRequest(input, mockContext);

        // Verificar
        assertNotNull(result);
        assertTrue(result.contains("enviado com sucesso"));
        assertTrue(result.contains("test@example.com"));
        
        verify(mockS3, times(1)).getObject(any(GetObjectRequest.class), any(ResponseTransformer.class));
        verify(mockSes, times(1)).sendEmail(any(SendEmailRequest.class));
    }

    @Test
    void handleRequestWithoutReportKeyShouldThrowException() {
        // Preparar dados de entrada sem reportKey
        Map<String, Object> input = new HashMap<>();

        // Executar
        NotifyReportFunction testFunction = new NotifyReportFunction() {
            @Override
            public String handleRequest(Map<String, Object> input, Context context) {
                try {
                    java.lang.reflect.Field s3Field = NotifyReportFunction.class.getDeclaredField("s3");
                    s3Field.setAccessible(true);
                    s3Field.set(this, mockS3);

                    java.lang.reflect.Field sesField = NotifyReportFunction.class.getDeclaredField("ses");
                    sesField.setAccessible(true);
                    sesField.set(this, mockSes);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                return super.handleRequest(input, context);
            }
        };

        // Verificar que lança exceção
        assertThrows(Exception.class, () -> {
            testFunction.handleRequest(input, mockContext);
        });

        verify(mockS3, never()).getObject(any(GetObjectRequest.class), any(ResponseTransformer.class));
        verify(mockSes, never()).sendEmail(any(SendEmailRequest.class));
    }

    @Test
    void handleRequestShouldLogEmailDetails() {
        // Preparar dados de entrada
        Map<String, Object> input = new HashMap<>();
        input.put("reportKey", "weekly-report-2026-01-08.txt");

        // Mock do S3
        String reportContent = "Relatório de teste";
        ResponseBytes<GetObjectResponse> responseBytes = ResponseBytes.fromByteArray(
            GetObjectResponse.builder().build(),
            reportContent.getBytes(StandardCharsets.UTF_8)
        );

        when(mockS3.getObject(any(GetObjectRequest.class), any(ResponseTransformer.class)))
            .thenReturn(responseBytes);

        // Mock do SES
        when(mockSes.sendEmail(any(SendEmailRequest.class)))
            .thenReturn(SendEmailResponse.builder()
                .messageId("test-message-id")
                .build());

        // Executar
        NotifyReportFunction testFunction = new NotifyReportFunction() {
            @Override
            public String handleRequest(Map<String, Object> input, Context context) {
                try {
                    java.lang.reflect.Field s3Field = NotifyReportFunction.class.getDeclaredField("s3");
                    s3Field.setAccessible(true);
                    s3Field.set(this, mockS3);

                    java.lang.reflect.Field sesField = NotifyReportFunction.class.getDeclaredField("ses");
                    sesField.setAccessible(true);
                    sesField.set(this, mockSes);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                return super.handleRequest(input, context);
            }
        };

        String result = testFunction.handleRequest(input, mockContext);

        // Verificar logs
        verify(mockLogger, atLeastOnce()).log(contains("RECIPIENT_EMAIL"));
        verify(mockLogger, atLeastOnce()).log(contains("SOURCE_EMAIL"));
        verify(mockLogger, atLeastOnce()).log(contains("BUCKET"));
    }

    @Test
    void handleRequestShouldHandleS3Error() {
        // Preparar dados de entrada
        Map<String, Object> input = new HashMap<>();
        input.put("reportKey", "non-existent-report.txt");

        // Mock do S3 - lançar exceção
        when(mockS3.getObject(any(GetObjectRequest.class), any(ResponseTransformer.class)))
            .thenThrow(new RuntimeException("Object not found"));

        // Executar
        NotifyReportFunction testFunction = new NotifyReportFunction() {
            @Override
            public String handleRequest(Map<String, Object> input, Context context) {
                try {
                    java.lang.reflect.Field s3Field = NotifyReportFunction.class.getDeclaredField("s3");
                    s3Field.setAccessible(true);
                    s3Field.set(this, mockS3);

                    java.lang.reflect.Field sesField = NotifyReportFunction.class.getDeclaredField("ses");
                    sesField.setAccessible(true);
                    sesField.set(this, mockSes);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                return super.handleRequest(input, context);
            }
        };

        // Verificar que lança exceção
        assertThrows(Exception.class, () -> {
            testFunction.handleRequest(input, mockContext);
        });

        verify(mockS3, times(1)).getObject(any(GetObjectRequest.class), any(ResponseTransformer.class));
        verify(mockSes, never()).sendEmail(any(SendEmailRequest.class));
    }

    @Test
    void handleRequestShouldHandleSesError() {
        // Preparar dados de entrada
        Map<String, Object> input = new HashMap<>();
        input.put("reportKey", "weekly-report-2026-01-08.txt");

        // Mock do S3
        String reportContent = "Relatório de teste";
        ResponseBytes<GetObjectResponse> responseBytes = ResponseBytes.fromByteArray(
            GetObjectResponse.builder().build(),
            reportContent.getBytes(StandardCharsets.UTF_8)
        );

        when(mockS3.getObject(any(GetObjectRequest.class), any(ResponseTransformer.class)))
            .thenReturn(responseBytes);

        // Mock do SES - lançar exceção
        when(mockSes.sendEmail(any(SendEmailRequest.class)))
            .thenThrow(new RuntimeException("Email not verified"));

        // Executar
        NotifyReportFunction testFunction = new NotifyReportFunction() {
            @Override
            public String handleRequest(Map<String, Object> input, Context context) {
                try {
                    java.lang.reflect.Field s3Field = NotifyReportFunction.class.getDeclaredField("s3");
                    s3Field.setAccessible(true);
                    s3Field.set(this, mockS3);

                    java.lang.reflect.Field sesField = NotifyReportFunction.class.getDeclaredField("ses");
                    sesField.setAccessible(true);
                    sesField.set(this, mockSes);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                return super.handleRequest(input, context);
            }
        };

        // Verificar que lança exceção
        assertThrows(Exception.class, () -> {
            testFunction.handleRequest(input, mockContext);
        });

        verify(mockS3, times(1)).getObject(any(GetObjectRequest.class), any(ResponseTransformer.class));
        verify(mockSes, times(1)).sendEmail(any(SendEmailRequest.class));
    }
}
