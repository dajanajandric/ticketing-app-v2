package pozoriste1.ticketing.users;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;

/**
 * Jedina mrezna zavisnost ticketing-service -> users-service.
 * Sinhroni REST poziv sa timeout-om (RestClientConfig), retry-jem i circuit
 * breaker-om (application.properties, resilience4j.instances.usersService).
 */
@Component
public class UsersServiceClient {

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public UsersServiceClient(RestTemplate usersServiceRestTemplate,
                               @Value("${users.service.url}") String baseUrl) {
        this.restTemplate = usersServiceRestTemplate;
        this.baseUrl = baseUrl;
    }

    @Retry(name = "usersService")
    @CircuitBreaker(name = "usersService", fallbackMethod = "spectatorFallback")
    public SpectatorDTO getSpectator(String jmbg) {
        try {
            return restTemplate.getForObject(baseUrl + "/spectators/" + jmbg, SpectatorDTO.class);
        } catch (HttpClientErrorException.NotFound e) {
            return null;
        }
    }

    @Retry(name = "usersService")
    @CircuitBreaker(name = "usersService", fallbackMethod = "agentExistsFallback")
    public boolean agentExists(String id) {
        try {
            restTemplate.getForEntity(baseUrl + "/ticket-agents/" + id, Void.class);
            return true;
        } catch (HttpClientErrorException.NotFound e) {
            return false;
        }
    }

    // Fallback se poziva i kad circuit breaker otvori kolo, i kad retry pokusaji potrosi
    // (timeout, konekcija odbijena, 5xx) - u oba slucaja ne mozemo da potvrdimo da
    // korisnik postoji, pa prodaju karte odbijamo umesto da nagadjamo.
    private SpectatorDTO spectatorFallback(String jmbg, Exception ex) {
        throw new UsersServiceUnavailableException(
                "users-service nedostupan, ne mogu da proverim gledaoca " + jmbg, ex);
    }

    private boolean agentExistsFallback(String id, Exception ex) {
        throw new UsersServiceUnavailableException(
                "users-service nedostupan, ne mogu da proverim radnika " + id, ex);
    }
}
