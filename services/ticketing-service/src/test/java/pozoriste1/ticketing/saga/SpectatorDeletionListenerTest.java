package pozoriste1.ticketing.saga;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import pozoriste1.ticketing.plays.Performance;
import pozoriste1.ticketing.plays.Showtime;
import pozoriste1.ticketing.saga.SpectatorDeletionEvents.Approved;
import pozoriste1.ticketing.saga.SpectatorDeletionEvents.Rejected;
import pozoriste1.ticketing.saga.SpectatorDeletionEvents.Requested;
import pozoriste1.ticketing.tickets.Ticket;
import pozoriste1.ticketing.tickets.TicketRepository;

/** Pravilo sage: buduce karte -> odbij; samo prosle -> anonimizuj i odobri. */
@ExtendWith(MockitoExtension.class)
class SpectatorDeletionListenerTest {

    private static final String JMBG = "0101990800007";
    private static final Requested REQUEST = new Requested("saga-1", JMBG);

    @Mock
    private TicketRepository tickets;

    @Mock
    private RabbitTemplate rabbit;

    @InjectMocks
    private SpectatorDeletionListener listener;

    private static Ticket ticket(LocalDateTime showtime) {
        Showtime s = new Showtime();
        s.setTime(showtime);
        Performance p = new Performance();
        p.setId("izv-001");
        p.setShowtime(s);
        Ticket t = new Ticket();
        t.setId("ul-" + showtime.hashCode());
        t.setSpectatorId(JMBG);
        t.setPerformance(p);
        return t;
    }

    private Object publishedEvent(String routingKey) {
        ArgumentCaptor<Object> event = ArgumentCaptor.forClass(Object.class);
        verify(rabbit).convertAndSend(eq(RabbitConfig.EXCHANGE), eq(routingKey), event.capture());
        return event.getValue();
    }

    @Test
    @DisplayName("Gledalac bez karata: brisanje se odobrava")
    void noTicketsApproved() {
        when(tickets.findBySpectatorId(JMBG)).thenReturn(List.of());

        listener.onDeletionRequested(REQUEST);

        assertThat(publishedEvent(RabbitConfig.DELETION_APPROVED)).isEqualTo(new Approved("saga-1", JMBG, 0));
    }

    @Test
    @DisplayName("Karta za buduce izvodjenje: brisanje se odbija, karte se ne diraju")
    void futureTicketRejected() {
        Ticket past = ticket(LocalDateTime.now().minusDays(3));
        Ticket future = ticket(LocalDateTime.now().plusDays(10));
        when(tickets.findBySpectatorId(JMBG)).thenReturn(List.of(past, future));

        listener.onDeletionRequested(REQUEST);

        Rejected rejected = (Rejected) publishedEvent(RabbitConfig.DELETION_REJECTED);
        assertThat(rejected.futureTickets()).isEqualTo(1);
        assertThat(past.getSpectatorId()).isEqualTo(JMBG);
        verify(tickets, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("Samo prosle karte: anonimizuju se (spectatorId = null) i brisanje se odobrava")
    void pastTicketsAnonymizedAndApproved() {
        Ticket t1 = ticket(LocalDateTime.now().minusDays(1));
        Ticket t2 = ticket(LocalDateTime.now().minusDays(30));
        when(tickets.findBySpectatorId(JMBG)).thenReturn(List.of(t1, t2));

        listener.onDeletionRequested(REQUEST);

        assertThat(t1.getSpectatorId()).isNull();
        assertThat(t2.getSpectatorId()).isNull();
        verify(tickets).saveAll(List.of(t1, t2));
        assertThat(publishedEvent(RabbitConfig.DELETION_APPROVED)).isEqualTo(new Approved("saga-1", JMBG, 2));
    }

    @Test
    @DisplayName("Karta bez termina se racuna kao buduca - sigurnije je odbiti nego izgubiti vezu")
    void ticketWithoutShowtimeCountsAsFuture() {
        Ticket t = new Ticket();
        t.setSpectatorId(JMBG);
        when(tickets.findBySpectatorId(JMBG)).thenReturn(List.of(t));

        listener.onDeletionRequested(REQUEST);

        publishedEvent(RabbitConfig.DELETION_REJECTED);
        verify(rabbit, never()).convertAndSend(eq(RabbitConfig.EXCHANGE), eq(RabbitConfig.DELETION_APPROVED), any(Object.class));
    }
}
