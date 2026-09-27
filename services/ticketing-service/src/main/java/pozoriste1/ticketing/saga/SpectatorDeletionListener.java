package pozoriste1.ticketing.saga;

import java.time.LocalDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import pozoriste1.ticketing.saga.SpectatorDeletionEvents.Approved;
import pozoriste1.ticketing.saga.SpectatorDeletionEvents.Rejected;
import pozoriste1.ticketing.saga.SpectatorDeletionEvents.Requested;
import pozoriste1.ticketing.tickets.Ticket;
import pozoriste1.ticketing.tickets.TicketRepository;

/**
 * Korak 2 sage brisanja gledaoca. Pravilo:
 *  - gledalac ima kartu za izvodjenje koje jos nije proslo -> odbij (users-service vraca gledaoca u ACTIVE)
 *  - inace -> karte za prosla izvodjenja se anonimizuju (istorija prodaje ostaje), pa odobri brisanje
 */
@Component
public class SpectatorDeletionListener {

    private static final Logger log = LoggerFactory.getLogger(SpectatorDeletionListener.class);

    private final TicketRepository tickets;
    private final RabbitTemplate rabbit;

    public SpectatorDeletionListener(TicketRepository tickets, RabbitTemplate rabbit) {
        this.tickets = tickets;
        this.rabbit = rabbit;
    }

    @RabbitListener(queues = RabbitConfig.REQUESTED_QUEUE)
    @Transactional
    public void onDeletionRequested(Requested event) {
        List<Ticket> spectatorTickets = tickets.findBySpectatorId(event.jmbg());
        LocalDateTime now = LocalDateTime.now();
        long future = spectatorTickets.stream().filter(t -> isUpcoming(t, now)).count();

        if (future > 0) {
            String reason = "Gledalac ima " + future + " karata za izvodjenja koja jos nisu odrzana";
            rabbit.convertAndSend(RabbitConfig.EXCHANGE, RabbitConfig.DELETION_REJECTED,
                    new Rejected(event.sagaId(), event.jmbg(), (int) future, reason));
            log.info("[saga {}] odbijeno brisanje gledaoca {}: {}", event.sagaId(), event.jmbg(), reason);
            return;
        }

        spectatorTickets.forEach(t -> t.setSpectatorId(null));
        tickets.saveAll(spectatorTickets);
        rabbit.convertAndSend(RabbitConfig.EXCHANGE, RabbitConfig.DELETION_APPROVED,
                new Approved(event.sagaId(), event.jmbg(), spectatorTickets.size()));
        log.info("[saga {}] odobreno brisanje gledaoca {}, anonimizovano {} karata",
                event.sagaId(), event.jmbg(), spectatorTickets.size());
    }

    // Karta bez termina se racuna kao buduca - bolje odbiti brisanje nego izgubiti vezu
    private static boolean isUpcoming(Ticket ticket, LocalDateTime now) {
        if (ticket.getPerformance() == null || ticket.getPerformance().getShowtime() == null
                || ticket.getPerformance().getShowtime().getTime() == null)
            return true;
        return ticket.getPerformance().getShowtime().getTime().isAfter(now);
    }
}
