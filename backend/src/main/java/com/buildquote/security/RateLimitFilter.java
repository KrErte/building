package com.buildquote.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@Slf4j
public class RateLimitFilter extends OncePerRequestFilter {

    // Sliding window rate limiting: key -> (windowStart, count)
    private final Map<String, RateBucket> buckets = new ConcurrentHashMap<>();

    private static final int WINDOW_MS = 60_000; // 1 minute
    private static final int MAX_REQUESTS_AUTHENTICATED = 120;
    private static final int MAX_REQUESTS_ANONYMOUS = 30;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String key = getBucketKey(request);
        int maxRequests = isAuthenticated() ? MAX_REQUESTS_AUTHENTICATED : MAX_REQUESTS_ANONYMOUS;

        RateBucket bucket = buckets.compute(key, (k, existing) -> {
            long now = System.currentTimeMillis();
            if (existing == null || (now - existing.windowStart) > WINDOW_MS) {
                return new RateBucket(now, new AtomicInteger(1));
            }
            existing.count.incrementAndGet();
            return existing;
        });

        if (bucket.count.get() > maxRequests) {
            long retryAfter = (WINDOW_MS - (System.currentTimeMillis() - bucket.windowStart)) / 1000;
            response.setStatus(429);
            response.setHeader("Retry-After", String.valueOf(Math.max(retryAfter, 1)));
            response.setContentType("application/json");
            response.getWriter().write("{\"message\":\"Rate limit exceeded. Try again in " + retryAfter + " seconds.\"}");
            log.warn("Rate limit exceeded for key: {}", key);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private String getBucketKey(HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserPrincipal principal) {
            return "user:" + principal.getId();
        }
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null) ip = request.getRemoteAddr();
        return "ip:" + ip;
    }

    private boolean isAuthenticated() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof UserPrincipal;
    }

    private record RateBucket(long windowStart, AtomicInteger count) {}
}
