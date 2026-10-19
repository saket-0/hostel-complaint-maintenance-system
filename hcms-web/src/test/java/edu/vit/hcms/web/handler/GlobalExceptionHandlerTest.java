package edu.vit.hcms.web.handler;

import edu.vit.hcms.core.exception.*;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.ExceptionHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("GlobalExceptionHandler Exception Mapping Tests")
class GlobalExceptionHandlerTest {

    @Mock
    private Javalin app;
    @Mock
    private Context ctx;

    @Test
    @SuppressWarnings("unchecked")
    @DisplayName("Registers exception handlers and produces uniform error payload")
    void testUniformErrorStructure() {
        when(ctx.path()).thenReturn("/api/v1/complaints");
        when(ctx.status(anyInt())).thenReturn(ctx);

        // Capture exception handlers registered on app
        ArgumentCaptor<ExceptionHandler> handlerCaptor = ArgumentCaptor.forClass(ExceptionHandler.class);
        GlobalExceptionHandler.register(app);

        // Verify registration of key exceptions
        verify(app).exception(eq(ValidationException.class), handlerCaptor.capture());
        verify(app).exception(eq(ResourceNotFoundException.class), any());
        verify(app).exception(eq(AuthenticationException.class), any());
        verify(app).exception(eq(InvalidStatusTransitionException.class), any());

        // Invoke the captured ValidationException handler
        ExceptionHandler<ValidationException> validationHandler = handlerCaptor.getValue();
        validationHandler.handle(new ValidationException("Field 'roomNumber' is mandatory"), ctx);

        // Verify status 422 is set
        verify(ctx).status(422);

        // Verify uniform error shape JSON is emitted: {timestamp, status, error, message, path, details[]}
        ArgumentCaptor<Map<String, Object>> jsonCaptor = ArgumentCaptor.forClass(Map.class);
        verify(ctx).json(jsonCaptor.capture());

        Map<String, Object> body = jsonCaptor.getValue();
        assertEquals(422, body.get("status"));
        assertEquals("Unprocessable Entity", body.get("error"));
        assertEquals("Field &#39;roomNumber&#39; is mandatory", body.get("message"),
                "Error messages must be HTML-escaped against XSS");
        assertEquals("/api/v1/complaints", body.get("path"));
        assertNotNull(body.get("timestamp"));
        assertNotNull(body.get("details"));
    }
}
