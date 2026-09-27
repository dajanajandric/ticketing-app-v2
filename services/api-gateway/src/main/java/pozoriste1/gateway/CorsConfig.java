package pozoriste1.gateway;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

/**
 * CORS se podesava samo ovde, jer je gateway jedina ulazna tacka za browser.
 * Servisi vise nemaju @CrossOrigin: da ga imaju, odgovor bi imao dva
 * Access-Control-Allow-Origin zaglavlja i browser bi ga odbio.
 * Bez ovoga gateway odbija preflight (OPTIONS) zahteve sa 403, pa iz browsera
 * ne rade POST/PATCH/DELETE sa JSON telom (kupovina, slobodna mesta, gledaoci).
 */
@Configuration
public class CorsConfig {

    @Bean
    public CorsFilter corsFilter(@Value("${frontend.url:http://localhost:8080}") String frontendUrl) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(frontendUrl));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return new CorsFilter(source);
    }
}
