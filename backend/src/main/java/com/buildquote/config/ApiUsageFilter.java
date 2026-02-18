package com.buildquote.config;

import com.buildquote.entity.ApiUsageLog;
import com.buildquote.repository.ApiUsageLogRepository;
import com.buildquote.security.UserPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
@Slf4j
@RequiredArgsConstructor
public class ApiUsageFilter extends OncePerRequestFilter {

    private final ApiUsageLogRepository apiUsageLogRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        long startTime = System.currentTimeMillis();

        filterChain.doFilter(request, response);

        long duration = System.currentTimeMillis() - startTime;

        // Log asynchronously to avoid impacting response time
        try {
            UUID userId = null;
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof UserPrincipal principal) {
                userId = principal.getId();
            }

            String ip = request.getHeader("X-Forwarded-For");
            if (ip == null) ip = request.getRemoteAddr();

            ApiUsageLog logEntry = ApiUsageLog.builder()
                    .userId(userId)
                    .endpoint(request.getRequestURI())
                    .method(request.getMethod())
                    .statusCode(response.getStatus())
                    .responseTimeMs(duration)
                    .ipAddress(ip)
                    .build();

            apiUsageLogRepository.save(logEntry);
        } catch (Exception e) {
            // Never fail the request due to logging errors
            log.debug("Failed to log API usage: {}", e.getMessage());
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        // Skip logging for static resources and health checks
        return path.startsWith("/h2-console") || path.equals("/api/projects/health");
    }
}
