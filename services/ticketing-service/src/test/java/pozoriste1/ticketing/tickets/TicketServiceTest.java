package pozoriste1.ticketing.tickets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import pozoriste1.ticketing.plays.Auditorium;
import pozoriste1.ticketing.plays.Performance;
import pozoriste1.ticketing.plays.PerformanceRepository;
import pozoriste1.ticketing.users.SpectatorDTO;
import pozoriste1.ticketing.users.UsersServiceClient;

@ExtendWith(MockitoExtension.class)
class TicketServiceTest {

    private static final String JMBG = "0101990800007";

    @Mock
    private TicketRepository repository;

    @Mock
    private PerformanceRepository performanceRepository;

    @Mock
    private UsersServiceClient usersServiceClient;

    @InjectMocks
    private TicketService service;

    private Performance performance;

    @BeforeEach
    void setUp() {
        Auditorium hall = new Auditorium();
        hall.setId("snp-ks");
        hall.setNumOfSeats(120);
        performance = new Performance();
        performance.setId("izv-004");
        performance.setAuditorium(hall);
        lenient().when(performanceRepository.findById("izv-004")).thenReturn(Optional.of(performance));
        lenient().when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private static Ticket request(String id, String seat) {
        Performance ref = new Performance();
        ref.setId("izv-004");
        Ticket t = new Ticket();
        t.setId(id);
        t.setNumberOfSeatInAuditorium(seat);
        t.setTicketAgentId("blag-01");
        t.setSpectatorId(JMBG);
        t.setPerformance(ref);
        return t;
    }

    private static Ticket sold(String seat) {
        Ticket t = new Ticket();
        t.setNumberOfSeatInAuditorium(seat);
        return t;
    }

    private void usersExist(String status) {
        when(usersServiceClient.agentExists("blag-01")).thenReturn(true);
        when(usersServiceClient.getSpectator(JMBG))
                .thenReturn(new SpectatorDTO(JMBG, "Ana", "Petrovic", null, "ana@example.com", status));
    }

    @Test
    @DisplayName("Ispravna kupovina: karta se cuva sa ucitanim izvodjenjem")
    void createValidTicket() {
        when(repository.findByPerformance_Id("izv-004")).thenReturn(List.of(sold("5")));
        usersExist("ACTIVE");

        Ticket saved = service.create(request("ul-1", "6"));

        assertThat(saved.getPerformance()).isSameAs(performance);
        verify(repository).save(saved);
    }

    @Test
    @DisplayName("Zauzeto mjesto se odbija; \"007\" i \"7\" su isto mjesto")
    void createRejectsTakenSeat() {
        when(repository.findByPerformance_Id("izv-004")).thenReturn(List.of(sold("7")));

        assertThatThrownBy(() -> service.create(request("ul-1", "007")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already taken");
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("Mjesto van sale se odbija (sala ima 120 mjesta)")
    void createRejectsSeatOutsideHall() {
        assertThatThrownBy(() -> service.create(request("ul-1", "121"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.create(request("ul-1", "0"))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Nepostojece ili izostavljeno izvodjenje se odbija")
    void createRejectsUnknownPerformance() {
        Ticket t = request("ul-1", "6");
        t.getPerformance().setId("nema");
        assertThatThrownBy(() -> service.create(t)).isInstanceOf(IllegalArgumentException.class);

        Ticket noPerformance = request("ul-2", "6");
        noPerformance.setPerformance(null);
        assertThatThrownBy(() -> service.create(noPerformance)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Postojeci ID karte se ne prepisuje (409)")
    void createRejectsDuplicateId() {
        when(repository.existsById("ul-1")).thenReturn(true);

        assertThatThrownBy(() -> service.create(request("ul-1", "6"))).isInstanceOf(IllegalStateException.class);
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("Nepostojeci blagajnik se odbija")
    void createRejectsUnknownAgent() {
        when(repository.findByPerformance_Id("izv-004")).thenReturn(List.of());
        when(usersServiceClient.agentExists("blag-01")).thenReturn(false);

        assertThatThrownBy(() -> service.create(request("ul-1", "6"))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Gledalac u postupku brisanja (saga) ne moze kupiti kartu")
    void createRejectsSpectatorBeingDeleted() {
        when(repository.findByPerformance_Id("izv-004")).thenReturn(List.of());
        usersExist("DELETION_PENDING");

        assertThatThrownBy(() -> service.create(request("ul-1", "6")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("brisanja");
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("Slobodna mjesta ne sadrze prodata")
    void availableSeatsExcludeSold() {
        when(repository.findByPerformance_Id("izv-004")).thenReturn(List.of(sold("1"), sold("3")));

        List<Integer> free = service.getAvailableSeats("izv-004");

        assertThat(free).hasSize(118).doesNotContain(1, 3).contains(2, 120);
    }

    @Test
    @DisplayName("Slobodna mesta za nepostojece izvodjenje: 404")
    void availableSeatsUnknownPerformance() {
        assertThatThrownBy(() -> service.getAvailableSeats("nema"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }
}
