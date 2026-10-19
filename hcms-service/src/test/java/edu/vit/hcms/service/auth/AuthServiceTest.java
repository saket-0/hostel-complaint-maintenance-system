package edu.vit.hcms.service.auth;

import at.favre.lib.crypto.bcrypt.BCrypt;
import com.auth0.jwt.interfaces.DecodedJWT;
import edu.vit.hcms.core.exception.AuthenticationException;
import edu.vit.hcms.core.exception.ValidationException;
import edu.vit.hcms.core.model.Student;
import edu.vit.hcms.persistence.dao.UserDao;
import edu.vit.hcms.persistence.db.TransactionManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService Authentication & JWT Tests")
class AuthServiceTest {

    @Mock
    private UserDao userDao;
    @Mock
    private TransactionManager txManager;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userDao, txManager);
    }

    @Test
    @DisplayName("Student registration hashes password with BCrypt and persists student")
    void testRegisterSuccess() {
        Student student = authService.register("Rohit Sharma", "rohit@vitstudent.ac.in",
                "secret123", "404", "D");

        assertNotNull(student);
        assertEquals("rohit@vitstudent.ac.in", student.getEmail());
        assertEquals("404", student.getRoomNumber());
        assertEquals("D", student.getHostelBlock());
        assertTrue(BCrypt.verifyer().verify("secret123".toCharArray(), student.getPasswordHash().toCharArray()).verified,
                "Password must be properly BCrypt-hashed");

        verify(userDao).save(any(Student.class));
    }

    @Test
    @DisplayName("Registration rejects invalid email format")
    void testInvalidEmail() {
        assertThrows(ValidationException.class, () ->
                authService.register("Bad Email", "not-an-email", "secret123", "101", "A"));
    }

    @Test
    @DisplayName("Registration rejects short password")
    void testShortPassword() {
        assertThrows(ValidationException.class, () ->
                authService.register("Short Pwd", "short@vitstudent.ac.in", "123", "101", "A"));
    }

    @Test
    @DisplayName("Login with valid credentials issues JWT token")
    void testLoginSuccess() {
        String hash = BCrypt.withDefaults().hashToString(12, "password123".toCharArray());
        Student student = new Student(10L, "Kohli", "kohli@vit.ac.in", hash, "101", "A");

        when(userDao.findByEmail("kohli@vit.ac.in")).thenReturn(Optional.of(student));

        String token = authService.login("kohli@vit.ac.in", "password123");
        assertNotNull(token);

        DecodedJWT jwt = authService.validateToken(token);
        assertEquals("10", jwt.getSubject());
        assertEquals("kohli@vit.ac.in", jwt.getClaim("email").asString());
        assertEquals("STUDENT", jwt.getClaim("role").asString());
    }

    @Test
    @DisplayName("Login with invalid password throws AuthenticationException")
    void testLoginInvalidPassword() {
        String hash = BCrypt.withDefaults().hashToString(12, "correctPassword".toCharArray());
        Student student = new Student(10L, "Kohli", "kohli@vit.ac.in", hash, "101", "A");

        when(userDao.findByEmail("kohli@vit.ac.in")).thenReturn(Optional.of(student));

        assertThrows(AuthenticationException.class, () ->
                authService.login("kohli@vit.ac.in", "wrongPassword"));
    }
}
