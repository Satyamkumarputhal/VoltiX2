package com.voltix.security;

import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

public class JwtAuthenticationFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            try {
                SignedJWT signedJWT = SignedJWT.parse(token);
                JWTClaimsSet claims = signedJWT.getJWTClaimsSet();

                // Extract tenant_id
                Long tenantId = claims.getLongClaim("tenant_id");
                if (tenantId != null) {
                    TenantContext.setCurrentTenant(tenantId);
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

            } catch (Exception e) {
                logger.debug("Failed to parse JWT token", e);
            }
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }
}
