package pozoriste1.ticketing.users;

import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import pozoriste1.ticketing.web.InputRules;

/**
 * Jedina mrezna zavisnost ticketing-service -> users-service.
 * Sinhroni REST poziv sa timeout-om (RestClientConfig), retry-jem i circuit
 * breaker-om (application.properties, resilience4j.instances.usersService).
 *
 * Fuzz testiranje je pokazalo da je ID ranije lijepljen direktno u URL, pa je npr.
 * "{x}" rusio poziv jos prije slanja, a circuit breaker je to brojao kao kvar
 * users-service-a i poslije 5 takvih zahtjeva blokirao svaku prodaju na 10 s.
 * Sada: (1) ID se provjerava prije poziva, (2) prosljedjuje se kao parametar
 * sablona pa se ispravno kodira, (3) circuit breaker broji samo mrezne greske
 * i 5xx (application.properties), a fallback hvata samo njih.
 */
@Component
public class UsersServiceClient {

    private static final Pattern SAFE_ID = Pattern.compile(InputRules.SAFE_ID);

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
        // Neispravan ID ne moze postojati - nema potrebe zvati users-service
        if (jmbg == null || !SAFE_ID.matcher(jmbg).matches())
            return null;
        try {
            return restTemplate.getForObject(baseUrl + "/spectators/{jmbg}", SpectatorDTO.class, jmbg);
        } catch (HttpClientErrorException.NotFound e) {
            return null;
        }
    }

    @Retry(name = "usersService")
    @CircuitBreaker(name = "usersService", fallbackMethod = "agentExistsFallback")
    public boolean agentExists(String id) {
        if (id == null || !SAFE_ID.matcher(id).matches())
            return false;
        try {
            restTemplate.getForEntity(baseUrl + "/ticket-agents/{id}", Void.class, id);
            return true;
        } catch (HttpClientErrorException.NotFound e) {
            return false;
        }
    }

    // Fallback samo za stvarnu nedostupnost users-service-a: kolo otvoreno, timeout /
    // odbijena konekcija, ili 5xx poslije svih retry pokusaja. Tada ne mozemo da potvrdimo
    // da korisnik postoji, pa prodaju karte odbijamo umesto da nagadjamo. Sve ostale
    // greske se ne pretvaraju u "nedostupan".
    private SpectatorDTO spectatorFallback(String jmbg, CallNotPermittedException ex) {
        throw unavailable("gledaoca " + jmbg, ex);
    }

    private SpectatorDTO spectatorFallback(String jmbg, ResourceAccessException ex) {
        throw unavailable("gledaoca " + jmbg, ex);
    }

    private SpectatorDTO spectatorFallback(String jmbg, HttpServerErrorException ex) {
        throw unavailable("gledaoca " + jmbg, ex);
    }

    private boolean agentExistsFallback(String id, CallNotPermittedException ex) {
        throw unavailable("radnika " + id, ex);
    }

    private boolean agentExistsFallback(String id, ResourceAccessException ex) {
        throw unavailable("radnika " + id, ex);
    }

    private boolean agentExistsFallback(String id, HttpServerErrorException ex) {
        throw unavailable("radnika " + id, ex);
    }

    private static UsersServiceUnavailableException unavailable(String what, Exception ex) {
        return new UsersServiceUnavailableException("users-service nedostupan, ne mogu da proverim " + what, ex);
    }
}
