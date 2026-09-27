package pozoriste1.users.web;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UriUtils;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Odbija zahtev (400) ako putanja sadrzi kontrolne znakove, npr. /spectators/%00,
 * jer bi ih Postgres u upitu odbio sa greskom.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ControlCharacterFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path;
        try {
            path = UriUtils.decode(request.getRequestURI(), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) { // neispravno kodiranje, npr. "%zz"
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Neispravno kodirana putanja");
            return;
        }
        if (path.chars().anyMatch(c -> c < 0x20 || c == 0x7F)) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Putanja sadrzi nedozvoljene znakove");
            return;
        }
        chain.doFilter(request, response);
    }
}
