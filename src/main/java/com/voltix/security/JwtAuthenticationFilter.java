package com.voltix.security;

import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private final com.voltix.platform.config.VoltixProperties properties;

    public JwtAuthenticationFilter(com.voltix.platform.config.VoltixProperties properties) {
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            try {
                SignedJWT signedJWT = SignedJWT.parse(token);
                String jwtSecret = properties.getSecurity().getJwtSecret();
                com.nimbusds.jose.JWSVerifier verifier = new com.nimbusds.jose.crypto.MACVerifier(jwtSecret);
                log.debug("Verifier created");
                if (!signedJWT.verify(verifier)) {
                    log.warn("JWT verification failed");
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    return;
                }
                
                JWTClaimsSet claims = signedJWT.getJWTClaimsSet();

                // Extract tenant_id
                Long tenantId = claims.getLongClaim("tenant_id");
                if (tenantId != null) {
                    TenantContext.setCurrentTenant(tenantId);
                    log.debug("Tenant context set: {}", tenantId);
                }

                // Extract roles and map to authorities
                List<String> roles = claims.getStringListClaim("roles");
                List<SimpleGrantedAuthority> authorities = List.of();
                if (roles != null) {
                    authorities = roles.stream()
                            .map(role -> new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()))
                            .collect(Collectors.toList());
                }

                UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                        claims.getSubject(), null, authorities
                );
                SecurityContextHolder.getContext().setAuthentication(auth);
                log.debug("Authentication set for subject: {}", claims.getSubject());

            } catch (Exception e) {
                log.error("Failed to parse or verify JWT token: {}", e.getMessage(), e);
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                return;
            }
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }
}
