package com.ajaymalewar.insightqa.controller;

import com.ajaymalewar.insightqa.dto.AuthDtos.AuthResponse;
import com.ajaymalewar.insightqa.dto.AuthDtos.LoginRequest;
import com.ajaymalewar.insightqa.dto.AuthDtos.RegisterRequest;
import com.ajaymalewar.insightqa.model.User;
import com.ajaymalewar.insightqa.repository.UserRepository;
import com.ajaymalewar.insightqa.security.JwtService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    public AuthController(UserRepository userRepository,
                           PasswordEncoder passwordEncoder,
                           JwtService jwtService,
                           AuthenticationManager authenticationManager) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.authenticationManager = authenticationManager;
    }

    @PostMapping("/register")
    public ResponseEntity<String> register(@RequestBody RegisterRequest request) {
        log.info("Registration attempt for username: {}", request.username());

        if (userRepository.existsByUsername(request.username())) {
            log.warn("Registration failed - username already taken: {}", request.username());
            return ResponseEntity.badRequest().body("Username already taken");
        }

        User user = new User(request.username(), passwordEncoder.encode(request.password()));
        userRepository.save(user);

        log.info("User registered successfully: {}", request.username());
        return ResponseEntity.ok("User registered successfully");
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody LoginRequest request) {
        log.info("Login attempt for username: {}", request.username());

        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username(), request.password())
        );

        String token = jwtService.generateToken(request.username());
        log.info("Login successful for username: {}", request.username());

        return ResponseEntity.ok(new AuthResponse(token));
    }
}