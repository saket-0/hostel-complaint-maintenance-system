package edu.vit.hcms.service.auth;

import at.favre.lib.crypto.bcrypt.BCrypt;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import edu.vit.hcms.core.config.AppConfig;
import edu.vit.hcms.core.enums.Role;
import edu.vit.hcms.core.exception.AuthenticationException;
import edu.vit.hcms.core.exception.DuplicateEntryException;
import edu.vit.hcms.core.exception.ValidationException;
import edu.vit.hcms.core.model.*;
import edu.vit.hcms.persistence.dao.UserDao;
import edu.vit.hcms.persistence.db.TransactionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Authentication service handling registration, login, JWT token management, and password changes.
 * Uses BCrypt for password hashing and JWT (java-jwt) for stateless tokens.
 */
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[\\w.-]+@[\\w.-]+\\.[a-zA-Z]{2,}$");

    private final UserDao userDao;
    private final TransactionManager txManager;
    private final Algorithm jwtAlgorithm;
    private final long tokenExpiryHours;

    public AuthService(UserDao userDao, TransactionManager txManager) {
        this.userDao = userDao;
        this.txManager = txManager;

        AppConfig config = AppConfig.getInstance();
        this.jwtAlgorithm = Algorithm.HMAC256(config.get("JWT_SECRET"));
        this.tokenExpiryHours = config.getLong("JWT_EXPIRY_HOURS");
    }

    /**
     * Registers a new student user.
     *
     * @param name         full name
     * @param email        email address
     * @param password     plain-text password (will be hashed)
     * @param roomNumber   hostel room number
     * @param hostelBlock  hostel block letter
     * @return the created Student
     * @throws ValidationException    if input is invalid
     * @throws DuplicateEntryException if email already exists
     */
    public Student register(String name, String email, String password,
                            String roomNumber, String hostelBlock) {
        validateRegistration(name, email, password, roomNumber, hostelBlock);

        String hash = BCrypt.withDefaults().hashToString(12, password.toCharArray());
        Student student = new Student(name, email.toLowerCase().trim(), hash, roomNumber, hostelBlock.toUpperCase());

        txManager.begin();
        try {
            userDao.save(student);
            txManager.commit();
            log.info("Registered new student: {}", email);
            return student;
        } catch (Exception e) {
            txManager.rollback();
            throw e;
        }
    }

    /**
     * Authenticates a user and returns a JWT token.
     *
     * @param email    user email
     * @param password plain-text password
     * @return JWT token string
     * @throws AuthenticationException if credentials are invalid
     */
    public String login(String email, String password) {
        if (email == null || email.isBlank()) {
            throw new ValidationException("email", "Email is required");
        }
        if (password == null || password.isBlank()) {
            throw new ValidationException("password", "Password is required");
        }

        User user = userDao.findByEmail(email.toLowerCase().trim())
                .orElseThrow(() -> new AuthenticationException("Invalid email or password"));

        if (!user.isActive()) {
            throw new AuthenticationException("Account is deactivated");
        }

        BCrypt.Result result = BCrypt.verifyer().verify(password.toCharArray(), user.getPasswordHash());
        if (!result.verified) {
            throw new AuthenticationException("Invalid email or password");
        }

        String token = generateToken(user);
        user.onLogin();
        log.info("User logged in: {} ({})", email, user.getRole());
        return token;
    }

    /**
     * Validates a JWT token and returns the decoded claims.
     *
     * @param token the JWT token
     * @return DecodedJWT with claims
     * @throws AuthenticationException if the token is invalid or expired
     */
    public DecodedJWT validateToken(String token) {
        try {
            return JWT.require(jwtAlgorithm)
                    .withIssuer("hcms")
                    .build()
                    .verify(token);
        } catch (JWTVerificationException e) {
            throw new AuthenticationException("Invalid or expired token");
        }
    }

    /**
     * Changes a user's password.
     */
    public void changePassword(Long userId, String currentPassword, String newPassword) {
        if (newPassword == null || newPassword.length() < 6) {
            throw new ValidationException("password", "New password must be at least 6 characters");
        }

        User user = userDao.findById(userId)
                .orElseThrow(() -> new AuthenticationException("User not found"));

        BCrypt.Result result = BCrypt.verifyer().verify(currentPassword.toCharArray(), user.getPasswordHash());
        if (!result.verified) {
            throw new AuthenticationException("Current password is incorrect");
        }

        String newHash = BCrypt.withDefaults().hashToString(12, newPassword.toCharArray());
        userDao.updatePassword(userId, newHash);
        log.info("Password changed for user: {}", userId);
    }

    /**
     * Generates a JWT token for the given user.
     */
    private String generateToken(User user) {
        Instant now = Instant.now();
        return JWT.create()
                .withIssuer("hcms")
                .withSubject(user.getUserId().toString())
                .withClaim("email", user.getEmail())
                .withClaim("role", user.getRole().name())
                .withClaim("name", user.getName())
                .withIssuedAt(Date.from(now))
                .withExpiresAt(Date.from(now.plus(tokenExpiryHours, ChronoUnit.HOURS)))
                .sign(jwtAlgorithm);
    }

    private void validateRegistration(String name, String email, String password,
                                       String roomNumber, String hostelBlock) {
        if (name == null || name.isBlank()) {
            throw new ValidationException("name", "Name is required");
        }
        if (email == null || !EMAIL_PATTERN.matcher(email).matches()) {
            throw new ValidationException("email", "Valid email is required");
        }
        if (password == null || password.length() < 6) {
            throw new ValidationException("password", "Password must be at least 6 characters");
        }
        if (roomNumber == null || roomNumber.isBlank()) {
            throw new ValidationException("roomNumber", "Room number is required");
        }
        if (hostelBlock == null || hostelBlock.isBlank() || hostelBlock.length() > 1) {
            throw new ValidationException("hostelBlock", "Hostel block must be a single letter (A-Z)");
        }
    }
}
