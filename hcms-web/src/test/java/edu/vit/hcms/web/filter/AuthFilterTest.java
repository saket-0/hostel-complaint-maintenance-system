package edu.vit.hcms.web.filter;

import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;
import edu.vit.hcms.core.exception.AuthenticationException;
import edu.vit.hcms.core.exception.AuthorizationException;
import edu.vit.hcms.core.model.Admin;
import edu.vit.hcms.core.model.Student;
import edu.vit.hcms.persistence.dao.UserDao;
import edu.vit.hcms.service.auth.AuthService;
import io.javalin.http.Context;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AuthFilter RBAC & JWT Extraction Tests")
class AuthFilterTest {

    @Mock
    private AuthService authService;
    @Mock
    private UserDao userDao;
    @Mock
    private Context ctx;
    @Mock
    private DecodedJWT decodedJwt;
    @Mock
    private Claim roleClaim;
    @Mock
    private Claim emailClaim;
    @Mock
    private Claim nameClaim;

    @InjectMocks
    private AuthFilter authFilter;

    @Test
    @DisplayName("Throws AuthenticationException when no token is present in header or cookie")
    void testMissingTokenThrows() {
        when(ctx.header("Authorization")).thenReturn(null);
        when(ctx.cookie("token")).thenReturn(null);

        assertThrows(AuthenticationException.class, () -> authFilter.requireAuth(ctx));
    }

    @Test
    @DisplayName("Validates Bearer token and sets request context attributes")
    void testValidBearerToken() {
        when(ctx.header("Authorization")).thenReturn("Bearer sample.valid.jwt");
        when(authService.validateToken("sample.valid.jwt")).thenReturn(decodedJwt);
        when(decodedJwt.getSubject()).thenReturn("42");
        when(decodedJwt.getClaim("role")).thenReturn(roleClaim);
        when(roleClaim.asString()).thenReturn("STUDENT");
        when(decodedJwt.getClaim("email")).thenReturn(emailClaim);
        when(emailClaim.asString()).thenReturn("student@vit.ac.in");
        when(decodedJwt.getClaim("name")).thenReturn(nameClaim);
        when(nameClaim.asString()).thenReturn("Student User");

        Student activeUser = new Student(42L, "Student User", "student@vit.ac.in", "hash", "101", "A");
        activeUser.setActive(true);
        when(userDao.findById(42L)).thenReturn(Optional.of(activeUser));

        assertDoesNotThrow(() -> authFilter.requireAuth(ctx));

        verify(ctx).attribute("userId", 42L);
        verify(ctx).attribute("role", "STUDENT");
        verify(ctx).attribute("email", "student@vit.ac.in");
        verify(ctx).attribute("name", "Student User");
    }

    @Test
    @DisplayName("requireRole permits matching role and rejects differing role")
    void testRequireRole() {
        when(ctx.header("Authorization")).thenReturn("Bearer sample.admin.jwt");
        when(authService.validateToken("sample.admin.jwt")).thenReturn(decodedJwt);
        when(decodedJwt.getSubject()).thenReturn("1");
        when(decodedJwt.getClaim("role")).thenReturn(roleClaim);
        when(roleClaim.asString()).thenReturn("ADMIN");
        when(decodedJwt.getClaim("email")).thenReturn(emailClaim);
        when(emailClaim.asString()).thenReturn("admin@vit.ac.in");
        when(decodedJwt.getClaim("name")).thenReturn(nameClaim);
        when(nameClaim.asString()).thenReturn("Chief Admin");

        Admin admin = new Admin(1L, "Chief Admin", "admin@vit.ac.in", "hash", "CHIEF_WARDEN");
        admin.setActive(true);
        when(userDao.findById(1L)).thenReturn(Optional.of(admin));
        when(ctx.attribute("role")).thenReturn("ADMIN");

        assertDoesNotThrow(() -> authFilter.requireRole(ctx, "ADMIN"));
        assertThrows(AuthorizationException.class, () -> authFilter.requireRole(ctx, "STAFF"));
    }
}
