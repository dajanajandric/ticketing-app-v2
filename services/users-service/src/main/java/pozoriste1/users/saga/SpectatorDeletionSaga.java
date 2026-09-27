package pozoriste1.users.saga;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pozoriste1.users.saga.SpectatorDeletionEvents.Approved;
import pozoriste1.users.saga.SpectatorDeletionEvents.Rejected;
import pozoriste1.users.saga.SpectatorDeletionEvents.Requested;
import pozoriste1.users.spectators.Spectator;
import pozoriste1.users.spectators.SpectatorRepository;
import pozoriste1.users.spectators.SpectatorStatus;

/**
 * users-service strana sage brisanja gledaoca.
 * Gledalac se ne brise odmah: prvo prelazi u DELETION_PENDING (dok traje saga
 * ticketing-service mu ne prodaje nove karte), a brise se tek kad ticketing
 * potvrdi da nema karata za buduca izvodjenja.
 */
@Service
public class SpectatorDeletionSaga {

    private static final Logger log = LoggerFactory.getLogger(SpectatorDeletionSaga.class);

    private final SpectatorRepository repository;
    private final RabbitTemplate rabbit;

    public SpectatorDeletionSaga(SpectatorRepository repository, RabbitTemplate rabbit) {
        this.repository = repository;
        this.rabbit = rabbit;
    }

    /**
     * Korak 1: oznaci gledaoca i objavi dogadjaj. Ako objava ne uspije (broker ne radi),
     * izuzetak ponistava transakciju pa gledalac ostaje ACTIVE.
     */
    @Transactional
    public String start(String jmbg) {
        Spectator spectator = repository.findById(jmbg)
                .orElseThrow(() -> new IllegalArgumentException("Gledalac " + jmbg + " ne postoji"));
        if (spectator.getStatus() == SpectatorStatus.DELETION_PENDING)
            throw new IllegalStateException("Brisanje gledaoca " + jmbg + " je vec u toku");

        String sagaId = UUID.randomUUID().toString();
        spectator.setStatus(SpectatorStatus.DELETION_PENDING);
        repository.save(spectator);
        rabbit.convertAndSend(RabbitConfig.EXCHANGE, RabbitConfig.DELETION_REQUESTED, new Requested(sagaId, jmbg));
        log.info("[saga {}] zapoceto brisanje gledaoca {}", sagaId, jmbg);
        return sagaId;
    }

    /** Korak 3a: ticketing je odobrio - gledalac se brise. */
    @RabbitListener(queues = RabbitConfig.APPROVED_QUEUE)
    @Transactional
    public void onApproved(Approved event) {
        repository.findById(event.jmbg())
                .filter(s -> s.getStatus() == SpectatorStatus.DELETION_PENDING)
                .ifPresentOrElse(s -> {
                    repository.delete(s);
                    log.info("[saga {}] gledalac {} obrisan ({} proslih karata anonimizovano)",
                            event.sagaId(), event.jmbg(), event.anonymizedTickets());
                }, () -> log.warn("[saga {}] gledalac {} nije u stanju DELETION_PENDING, dogadjaj ignorisan",
                        event.sagaId(), event.jmbg()));
    }

    /** Korak 3b: ticketing je odbio - kompenzacija, gledalac se vraca u ACTIVE. */
    @RabbitListener(queues = RabbitConfig.REJECTED_QUEUE)
    @Transactional
    public void onRejected(Rejected event) {
        repository.findById(event.jmbg())
                .filter(s -> s.getStatus() == SpectatorStatus.DELETION_PENDING)
                .ifPresent(s -> {
                    s.setStatus(SpectatorStatus.ACTIVE);
                    repository.save(s);
                });
        log.info("[saga {}] brisanje gledaoca {} odbijeno: {}", event.sagaId(), event.jmbg(), event.reason());
    }
}
