package com.messagehub.messagehub.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class JwtFilter extends OncePerRequestFilter {
    private final JwtUtil jwtUtil;

    public JwtFilter(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String header =
                request.getHeader("Authorization");

        if (header != null &&
                header.startsWith("Bearer ")) {

            String token =
                    header.substring(7);

            try {

                if (jwtUtil.isValid(token)) {

                    Long userId =
                            jwtUtil.getUserId(token);

                    String username =
                            jwtUtil.getUsername(token);

                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(
                                    userId,
                                    null,
                                    AuthorityUtils.NO_AUTHORITIES
                            );

                    authentication.setDetails(username);

                    SecurityContextHolder
                            .getContext()
                            .setAuthentication(authentication);
                }

            } catch (Exception error) {

                SecurityContextHolder
                        .clearContext();
            }
        }

        filterChain.doFilter(
                request,
                response
        );
    }
}
