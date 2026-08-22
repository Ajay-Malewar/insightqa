package com.ajaymalewar.insightqa.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtServiceTest {

    private static final Logger log = LoggerFactory.getLogger(JwtServiceTest.class);

    private JwtService jwtService;

    private static final String TEST_SECRET = "test-secret-key-for-unit-tests-must-be-32-bytes-min";

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(TEST_SECRET);
        log.info("JwtService initialized for test with a fixed test secret");
    }

    @Test
    void generateToken_shouldProduceAValidThreePartJwt() {
        String token = jwtService.generateToken("ajay");
        log.info("Generated token: {}", token);

        assertEquals(3, token.split("\\.").length, "JWT should have 3 dot-separated segments");
    }

    @Test
    void extractUsername_shouldReturnTheOriginalUsername() {
        String token = jwtService.generateToken("ajay");
        String extracted = jwtService.extractUsername(token);
        log.info("Extracted username: {}", extracted);

        assertEquals("ajay", extracted);
    }

    @Test
    void isTokenValid_shouldReturnTrueForMatchingUsernameAndFreshToken() {
        String token = jwtService.generateToken("ajay");
        boolean valid = jwtService.isTokenValid(token, "ajay");
        log.info("Token valid for correct username: {}", valid);

        assertTrue(valid);
    }

    @Test
    void isTokenValid_shouldReturnFalseForMismatchedUsername() {
        String token = jwtService.generateToken("ajay");
        boolean valid = jwtService.isTokenValid(token, "someone-else");
        log.info("Token valid for mismatched username: {}", valid);

        assertFalse(valid);
    }
}