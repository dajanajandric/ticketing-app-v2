package pozoriste1.users.web;

import java.io.IOException;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Odbija zahtjev (400) ako putanja sadrzi kontrolne znakove, npr. /spectators/%00.
 * Bez ovoga takav ID stize do upita u bazi i Postgres baci gresku (500).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ControlCharacterFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getServletPath();
        if (path != null && path.chars().anyMatch(c -> c < 0x20 || c == 0x7F)) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Putanja sadrzi nedozvoljene znakove");
            return;
        }
        chain.doFilter(request, response);
    }
}
