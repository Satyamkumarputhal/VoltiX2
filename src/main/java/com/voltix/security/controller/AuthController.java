package com.voltix.security.controller;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.voltix.platform.config.VoltixProperties;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Date;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;
    private final VoltixProperties properties;

    public AuthController(JdbcTemplate jdbcTemplate, PasswordEncoder passwordEncoder, VoltixProperties properties) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
    }

    public static class LoginRequest {
        public String username;
        public String password;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        try {
            List<Map<String, Object>> users = jdbcTemplate.queryForList(
                    "SELECT user_id, tenant_id, password_hash, role FROM users WHERE username = ?", request.username);

            if (users.isEmpty()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Invalid credentials"));
            }

            Map<String, Object> user = users.get(0);
            String hash = (String) user.get("password_hash");

            if (!passwordEncoder.matches(request.password, hash)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Invalid credentials"));
            }

            Long tenantId = ((Number) user.get("tenant_id")).longValue();
            String role = (String) user.get("role");

            JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
                    .subject(request.username)
                    .claim("tenant_id", tenantId)
                    .claim("roles", List.of(role))
                    .expirationTime(new Date(System.currentTimeMillis() + 86400000))
                    .build();

            SignedJWT signedJWT = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claimsSet);
            signedJWT.sign(new MACSigner(properties.getSecurity().getJwtSecret()));

            return ResponseEntity.ok(Map.of("token", signedJWT.serialize()));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Server error"));
        }
    }
}
