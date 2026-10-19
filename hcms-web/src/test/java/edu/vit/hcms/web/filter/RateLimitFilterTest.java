package edu.vit.hcms.web.filter;

import edu.vit.hcms.core.exception.HcmsException;
import io.javalin.http.Context;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("RateLimitFilter Sliding Window Tests")
class RateLimitFilterTest {

    @Mock
    private Context ctx;

    @Test
    @DisplayName("Allows requests up to the maximum limit and blocks subsequent requests")
    void testRateLimitEnforcement() {
        RateLimitFilter filter = new RateLimitFilter();
        when(ctx.ip()).thenReturn("192.168.1.100");

        // Allowed 3 requests
        assertDoesNotThrow(() -> filter.checkLimit(ctx, "login", 3));
        assertDoesNotThrow(() -> filter.checkLimit(ctx, "login", 3));
        assertDoesNotThrow(() -> filter.checkLimit(ctx, "login", 3));

        // 4th request must throw HcmsException with RATE_LIMITED
        assertThrows(HcmsException.class, () -> filter.checkLimit(ctx, "login", 3));
    }
}
