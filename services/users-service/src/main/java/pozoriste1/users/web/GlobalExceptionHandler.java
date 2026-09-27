package pozoriste1.users.web;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.jpa.JpaObjectRetrievalFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Pretvara greske izazvane losim ulazom u 4xx odgovore. Fuzz testiranje je pokazalo
 * da bez ovoga npr. nepostojeca referenca ili povreda stranog kljuca daju 500.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    public record ApiError(int status, String message, List<String> details) {
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> invalidBody(MethodArgumentNotValidException e) {
        List<String> details = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .toList();
        return error(HttpStatus.BAD_REQUEST, "Neispravni podaci u zahtjevu", details);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> badArgument(IllegalArgumentException e) {
        return error(HttpStatus.BAD_REQUEST, e.getMessage(), List.of());
    }

    // Referenca na objekat koji ne postoji (npr. gledalac sa nepostojecim blagajnikom)
    @ExceptionHandler({ InvalidDataAccessApiUsageException.class, JpaObjectRetrievalFailureException.class })
    public ResponseEntity<ApiError> missingReference(RuntimeException e) {
        return error(HttpStatus.BAD_REQUEST, "Referencirani objekat ne postoji", List.of());
    }

    // Npr. brisanje objekta koji je jos u upotrebi
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> integrity(DataIntegrityViolationException e) {
        return error(HttpStatus.CONFLICT, "Operacija bi narusila integritet podataka", List.of());
    }

    // Npr. objekat sa tim ID-em vec postoji, ili je brisanje vec u toku
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiError> conflict(IllegalStateException e) {
        return error(HttpStatus.CONFLICT, e.getMessage(), List.of());
    }

    // Broker nedostupan - saga ne moze da pocne
    @ExceptionHandler(AmqpException.class)
    public ResponseEntity<ApiError> brokerDown(AmqpException e) {
        log.error("RabbitMQ nedostupan", e);
        return error(HttpStatus.SERVICE_UNAVAILABLE, "Servis za dogadjaje trenutno nije dostupan, pokusajte kasnije", List.of());
    }

    private static ResponseEntity<ApiError> error(HttpStatus status, String message, List<String> details) {
        return ResponseEntity.status(status).body(new ApiError(status.value(), message, details));
    }
}
