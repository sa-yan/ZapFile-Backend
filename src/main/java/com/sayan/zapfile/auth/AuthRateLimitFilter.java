package com.sayan.zapfile.auth;

import com.sayan.zapfile.common.GlobalExceptionHandler.ErrorResponse;
import com.sayan.zapfile.common.RateLimiter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

/**
 * Rate-limits the unauthenticated auth endpoints per client IP so
 * credential stuffing and register spam can't hammer the database.
 * Render sits behind a proxy, so the real client IP is the first entry
 * of X-Forwarded-For when present.
 */
@Component
public class AuthRateLimitFilter extends OncePerRequestFilter {

    private static final Set<String> LIMITED_PATHS = Set.of(
            "/api/auth/login", "/api/auth/register", "/api/auth/refresh");
    private static final int MAX_REQUESTS_PER_MINUTE = 10;

    private final RateLimiter limiter = new RateLimiter(MAX_REQUESTS_PER_MINUTE, Duration.ofMinutes(1));
    private final ObjectMapper objectMapper;

    public AuthRateLimitFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if ("POST".equals(request.getMethod())
                && LIMITED_PATHS.contains(request.getRequestURI())
                && !limiter.tryAcquire(clientIp(request))) {
            HttpStatus status = HttpStatus.TOO_MANY_REQUESTS;
            response.setStatus(status.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write(objectMapper.writeValueAsString(
                    ErrorResponse.of(status, "Too many requests; try again in a minute")));
            return;
        }
        filterChain.doFilter(request, response);
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
