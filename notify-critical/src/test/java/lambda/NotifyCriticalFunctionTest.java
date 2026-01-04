package lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class NotifyCriticalFunctionTest {

    @Test
    void handleRequestNonCriticalShouldReturnNoEmailMessage() {
        Map<String, Object> detail = new HashMap<>();
        detail.put("feedbackId", "fb-1");
        detail.put("fullName", "João Silva");
        detail.put("category", "General");
        detail.put("comment", "Ótimo");
        detail.put("rating", 4);
        detail.put("isCritical", false);

        Map<String, Object> input = new HashMap<>();
        input.put("detail", detail);

        Context ctx = mock(Context.class);
        LambdaLogger logger = mock(LambdaLogger.class);
        when(ctx.getLogger()).thenReturn(logger);

        NotifyCriticalFunction function = new NotifyCriticalFunction();
        String result = function.handleRequest(input, ctx);

        assertEquals("Feedback não é crítico. Nenhum e-mail enviado.", result);
    }

    @Test
    void handleRequestCriticalShouldSendEmailViaHttpClient() throws Exception {
        Map<String, Object> detail = new HashMap<>();
        detail.put("feedbackId", "fb-1");
        detail.put("fullName", "João Silva");
        detail.put("category", "Critical");
        detail.put("comment", "Ótimo");
        detail.put("rating", 5);
        detail.put("isCritical", true);

        Map<String, Object> input = new HashMap<>();
        input.put("detail", detail);

        Context ctx = mock(Context.class);
        LambdaLogger logger = mock(LambdaLogger.class);
        when(ctx.getLogger()).thenReturn(logger);

        CloseableHttpClient mockClient = mock(CloseableHttpClient.class);
        when(mockClient.execute(any(HttpPost.class), any(HttpClientResponseHandler.class)))
                .thenAnswer(invocation -> {
                    HttpClientResponseHandler<String> handler = invocation.getArgument(1);
                    ClassicHttpResponse response = mock(ClassicHttpResponse.class);
                    when(response.getCode()).thenReturn(200);
                    try {
                        return handler.handleResponse(response);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });

        try (MockedStatic<HttpClients> mocked = mockStatic(HttpClients.class)) {
            mocked.when(HttpClients::createDefault).thenReturn(mockClient);

            NotifyCriticalFunction function = new NotifyCriticalFunction();
            String result = function.handleRequest(input, ctx);

            assertEquals("E-mail enviado via API Mailtrap.", result);
            verify(mockClient, times(1)).execute(any(HttpPost.class), any(HttpClientResponseHandler.class));
        }
    }
}