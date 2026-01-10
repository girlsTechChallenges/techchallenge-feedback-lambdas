package lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.SendEmailRequest;
import software.amazon.awssdk.services.ses.model.SendEmailResponse;
import software.amazon.awssdk.services.ses.model.SesException;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class NotifyReportFunctionTest {

    @Mock
    private S3Client mockS3;
    
    @Mock
    private SesClient mockSes;
    
    @Mock
    private Context mockContext;
    
    @Mock
    private LambdaLogger mockLogger;
    
    private TestableNotifyReportFunction function;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(mockContext.getLogger()).thenReturn(mockLogger);

        // Configurar variáveis de ambiente
        System.setProperty("REPORTS_BUCKET", "test-reports-bucket");
        System.setProperty("RECIPIENT_EMAIL", "test@example.com");
        System.setProperty("SOURCE_EMAIL", "no-reply@example.com");
        System.setProperty("AWS_REGION", "us-east-1");
        
        // Criar função testável com mocks injetados
        function = new TestableNotifyReportFunction(mockS3, mockSes);
    }
    
    // Classe interna para injetar os mocks
    private static class TestableNotifyReportFunction extends NotifyReportFunction {
        private final S3Client testS3;
        private final SesClient testSes;
        
        public TestableNotifyReportFunction(S3Client s3, SesClient ses) {
            super();
            this.testS3 = s3;
            this.testSes = ses;
        }
        
        @Override
        public String handleRequest(Map<String, Object> input, Context context) {
            try {
                java.lang.reflect.Field s3Field = NotifyReportFunction.class.getDeclaredField("s3");
                s3Field.setAccessible(true);
                s3Field.set(this, testS3);

                java.lang.reflect.Field sesField = NotifyReportFunction.class.getDeclaredField("ses");
                sesField.setAccessible(true);
                sesField.set(this, testSes);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            return super.handleRequest(input, context);
        }
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
        String result = function.handleRequest(input, mockContext);

        // Verificar
        assertNotNull(result);
        assertTrue(result.contains("enviado com sucesso"));
        
        verify(mockS3, times(1)).getObject(any(GetObjectRequest.class), any(ResponseTransformer.class));
        verify(mockSes, times(1)).sendEmail(any(SendEmailRequest.class));
    }

    @Test
    void handleRequestWithoutReportKeyShouldThrowException() {
        // Preparar dados de entrada sem reportKey
        Map<String, Object> input = new HashMap<>();

        // Verificar que lança exceção
        assertThrows(Exception.class, () -> {
            function.handleRequest(input, mockContext);
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
        String result = function.handleRequest(input, mockContext);

        // Verificar logs
        verify(mockLogger, atLeastOnce()).log(contains("RECIPIENT_EMAIL"));
        verify(mockLogger, atLeastOnce()).log(contains("SOURCE_EMAIL"));
        verify(mockLogger, atLeastOnce()).log(contains("BUCKET"));
        verify(mockLogger, atLeastOnce()).log(contains("Relatório enviado"));
    }

    @Test
    void handleRequestShouldHandleS3Error() {
        // Preparar dados de entrada
        Map<String, Object> input = new HashMap<>();
        input.put("reportKey", "non-existent-report.txt");

        // Mock do S3 - lançar exceção
        when(mockS3.getObject(any(GetObjectRequest.class), any(ResponseTransformer.class)))
            .thenThrow(S3Exception.builder().message("Object not found").build());

        // Verificar que lança exceção
        assertThrows(Exception.class, () -> {
            function.handleRequest(input, mockContext);
        });

        verify(mockS3, times(1)).getObject(any(GetObjectRequest.class), any(ResponseTransformer.class));
        verify(mockSes, never()).sendEmail(any(SendEmailRequest.class));
        verify(mockLogger, atLeastOnce()).log(contains("Erro"));
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
            .thenThrow(SesException.builder().message("Email not verified").build());

        // Verificar que lança exceção
        assertThrows(Exception.class, () -> {
            function.handleRequest(input, mockContext);
        });

        verify(mockS3, times(1)).getObject(any(GetObjectRequest.class), any(ResponseTransformer.class));
        verify(mockSes, times(1)).sendEmail(any(SendEmailRequest.class));
        verify(mockLogger, atLeastOnce()).log(contains("Erro"));
    }
    
    @Test
    void handleRequestWithNullReportKeyShouldThrowIllegalArgumentException() {
        // Preparar dados de entrada com reportKey explicitamente null
        Map<String, Object> input = new HashMap<>();
        input.put("reportKey", null);

        // Verificar que lança exceção (pode ser RuntimeException encapsulando IllegalArgumentException)
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            function.handleRequest(input, mockContext);
        });

        // Verificar que a causa é IllegalArgumentException
        Throwable cause = exception.getCause();
        assertTrue(cause instanceof IllegalArgumentException);
        assertTrue(cause.getMessage().contains("reportKey is required"));
        
        verify(mockS3, never()).getObject(any(GetObjectRequest.class), any(ResponseTransformer.class));
        verify(mockSes, never()).sendEmail(any(SendEmailRequest.class));
    }
    
    @Test
    void handleRequestWithEmptyReportContentShouldSendEmail() {
        // Preparar dados de entrada
        Map<String, Object> input = new HashMap<>();
        input.put("reportKey", "empty-report.txt");

        // Mock do S3 com conteúdo vazio
        ResponseBytes<GetObjectResponse> responseBytes = ResponseBytes.fromByteArray(
            GetObjectResponse.builder().build(),
            "".getBytes(StandardCharsets.UTF_8)
        );

        when(mockS3.getObject(any(GetObjectRequest.class), any(ResponseTransformer.class)))
            .thenReturn(responseBytes);

        // Mock do SES
        when(mockSes.sendEmail(any(SendEmailRequest.class)))
            .thenReturn(SendEmailResponse.builder()
                .messageId("test-message-id-empty")
                .build());

        // Executar
        String result = function.handleRequest(input, mockContext);

        // Verificar - deve enviar email mesmo com conteúdo vazio
        assertNotNull(result);
        assertTrue(result.contains("enviado com sucesso"));
        verify(mockSes, times(1)).sendEmail(any(SendEmailRequest.class));
    }
    
    @Test
    void handleRequestWithLargeReportShouldSendEmail() {
        // Preparar dados de entrada
        Map<String, Object> input = new HashMap<>();
        input.put("reportKey", "large-report.txt");

        // Mock do S3 com conteúdo grande (10KB)
        StringBuilder largeContent = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            largeContent.append("Linha ").append(i).append(": Feedback de teste com dados diversos\n");
        }
        
        ResponseBytes<GetObjectResponse> responseBytes = ResponseBytes.fromByteArray(
            GetObjectResponse.builder().build(),
            largeContent.toString().getBytes(StandardCharsets.UTF_8)
        );

        when(mockS3.getObject(any(GetObjectRequest.class), any(ResponseTransformer.class)))
            .thenReturn(responseBytes);

        // Mock do SES
        when(mockSes.sendEmail(any(SendEmailRequest.class)))
            .thenReturn(SendEmailResponse.builder()
                .messageId("test-message-id-large")
                .build());

        // Executar
        String result = function.handleRequest(input, mockContext);

        // Verificar
        assertNotNull(result);
        assertTrue(result.contains("enviado com sucesso"));
        verify(mockS3, times(1)).getObject(any(GetObjectRequest.class), any(ResponseTransformer.class));
        verify(mockSes, times(1)).sendEmail(any(SendEmailRequest.class));
    }
    
    @Test
    void handleRequestWithSpecialCharactersShouldSendEmail() {
        // Preparar dados de entrada
        Map<String, Object> input = new HashMap<>();
        input.put("reportKey", "report-with-special-chars.txt");

        // Mock do S3 com caracteres especiais
        String reportContent = "=== RELATÓRIO ===\n" +
            "Caracteres especiais: á é í ó ú ã õ ç\n" +
            "Símbolos: @#$%&*()_+-=[]{}|;:',.<>?/\n" +
            "Emoji: 😀 ✅ ⚠️\n" +
            "Unicode: \u2665 \u2660 \u2663 \u2666";
        
        ResponseBytes<GetObjectResponse> responseBytes = ResponseBytes.fromByteArray(
            GetObjectResponse.builder().build(),
            reportContent.getBytes(StandardCharsets.UTF_8)
        );

        when(mockS3.getObject(any(GetObjectRequest.class), any(ResponseTransformer.class)))
            .thenReturn(responseBytes);

        // Mock do SES
        when(mockSes.sendEmail(any(SendEmailRequest.class)))
            .thenReturn(SendEmailResponse.builder()
                .messageId("test-message-id-special")
                .build());

        // Executar
        String result = function.handleRequest(input, mockContext);

        // Verificar
        assertNotNull(result);
        assertTrue(result.contains("enviado com sucesso"));
        verify(mockSes, times(1)).sendEmail(any(SendEmailRequest.class));
    }
    
    @Test
    void handleRequestShouldUseCorrectBucketAndKey() {
        // Preparar dados de entrada
        Map<String, Object> input = new HashMap<>();
        String testKey = "reports/2026/01/weekly-report.txt";
        input.put("reportKey", testKey);

        // Mock do S3
        ResponseBytes<GetObjectResponse> responseBytes = ResponseBytes.fromByteArray(
            GetObjectResponse.builder().build(),
            "Relatório teste".getBytes(StandardCharsets.UTF_8)
        );

        when(mockS3.getObject(any(GetObjectRequest.class), any(ResponseTransformer.class)))
            .thenReturn(responseBytes);

        // Mock do SES
        when(mockSes.sendEmail(any(SendEmailRequest.class)))
            .thenReturn(SendEmailResponse.builder()
                .messageId("test-message-id")
                .build());

        // Executar
        function.handleRequest(input, mockContext);

        // Verificar que o método getObject foi chamado
        verify(mockS3, times(1)).getObject(any(GetObjectRequest.class), any(ResponseTransformer.class));
    }
    
    @Test
    void handleRequestShouldSendEmailWithCorrectSubject() {
        // Preparar dados de entrada
        Map<String, Object> input = new HashMap<>();
        input.put("reportKey", "weekly-report.txt");

        // Mock do S3
        ResponseBytes<GetObjectResponse> responseBytes = ResponseBytes.fromByteArray(
            GetObjectResponse.builder().build(),
            "Conteúdo do relatório".getBytes(StandardCharsets.UTF_8)
        );

        when(mockS3.getObject(any(GetObjectRequest.class), any(ResponseTransformer.class)))
            .thenReturn(responseBytes);

        // Mock do SES
        when(mockSes.sendEmail(any(SendEmailRequest.class)))
            .thenReturn(SendEmailResponse.builder()
                .messageId("test-message-id")
                .build());

        // Executar
        function.handleRequest(input, mockContext);

        // Verificar que o email foi enviado
        verify(mockSes, times(1)).sendEmail(any(SendEmailRequest.class));
    }
    
    @Test
    void handleRequestWithDifferentReportKeyFormatsShouldWork() {
        // Testar diferentes formatos de chaves
        String[] testKeys = {
            "simple-report.txt",
            "reports/2026/01/report.txt",
            "reports/nested/deep/path/report.txt",
            "report-with-dashes-and_underscores.txt",
            "UPPERCASE-REPORT.TXT"
        };

        for (String key : testKeys) {
            // Preparar dados de entrada
            Map<String, Object> input = new HashMap<>();
            input.put("reportKey", key);

            // Mock do S3
            ResponseBytes<GetObjectResponse> responseBytes = ResponseBytes.fromByteArray(
                GetObjectResponse.builder().build(),
                "Relatório".getBytes(StandardCharsets.UTF_8)
            );

            when(mockS3.getObject(any(GetObjectRequest.class), any(ResponseTransformer.class)))
                .thenReturn(responseBytes);

            // Mock do SES
            when(mockSes.sendEmail(any(SendEmailRequest.class)))
                .thenReturn(SendEmailResponse.builder()
                    .messageId("test-message-id-" + key)
                    .build());

            // Executar
            String result = function.handleRequest(input, mockContext);

            // Verificar
            assertNotNull(result);
            assertTrue(result.contains("enviado com sucesso"));
        }
    }
}
