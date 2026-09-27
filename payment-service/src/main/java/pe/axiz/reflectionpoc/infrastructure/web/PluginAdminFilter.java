package pe.axiz.reflectionpoc.infrastructure.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Minimal local administration boundary; use real identity/access control at a production gateway. */
@Component
public final class PluginAdminFilter extends OncePerRequestFilter {
    private final String token;

    public PluginAdminFilter(@Value("${poc.admin-token:}") String token) { this.token = token; }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return !path.equals("/api/v1/reflection") && !path.startsWith("/api/v1/reflection/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (token.isBlank()) {
            reject(response, 503, "Administración deshabilitada: configure PLUGIN_ADMIN_TOKEN");
            return;
        }
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !MessageDigest.isEqual(("Bearer " + token).getBytes(StandardCharsets.UTF_8),
                authorization.getBytes(StandardCharsets.UTF_8))) {
            response.setHeader("WWW-Authenticate", "Bearer");
            reject(response, 401, "Credenciales administrativas requeridas");
            return;
        }
        chain.doFilter(request, response);
    }

    private void reject(HttpServletResponse response, int status, String detail) throws IOException {
        response.setStatus(status);
        response.setContentType("application/problem+json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"status\":" + status + ",\"detail\":\"" + detail + "\"}");
    }
}
