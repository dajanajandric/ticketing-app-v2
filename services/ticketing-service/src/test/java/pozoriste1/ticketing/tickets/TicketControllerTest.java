package pozoriste1.ticketing.tickets;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import pozoriste1.ticketing.plays.Performance;
import pozoriste1.ticketing.plays.Play;
import pozoriste1.ticketing.plays.Showtime;
import pozoriste1.ticketing.users.SpectatorDTO;
import pozoriste1.ticketing.users.UsersServiceClient;
import pozoriste1.ticketing.users.UsersServiceUnavailableException;

/** HTTP strana kupovine karte - bez baze, users-service-a i mail servera. */
@WebMvcTest(TicketController.class)
class TicketControllerTest {

    private static final String JMBG = "0101990800007";
    private static final String VALID = "{\"id\":\"ul-1\",\"price\":\"900 RSD\",\"numberOfSeatInAuditorium\":\"6\","
            + "\"ticketAgentId\":\"blag-01\",\"spectatorId\":\"" + JMBG + "\",\"performance\":{\"id\":\"izv-004\"}}";

    @Autowired
    private MockMvc mvc;

    @MockBean
    private TicketService service;

    @MockBean
    private EmailService emailService;

    @MockBean
    private UsersServiceClient usersServiceClient;

    private static Ticket createdTicket() {
        Play play = new Play();
        play.setTitle("Totovi");
        Showtime showtime = new Showtime();
        showtime.setDate(LocalDate.of(2026, 10, 8));
        showtime.setTime(LocalDateTime.of(2026, 10, 8, 21, 0));
        Performance performance = new Performance();
        performance.setId("izv-004");
        performance.setPlay(play);
        performance.setShowtime(showtime);
        Ticket t = new Ticket();
        t.setId("ul-1");
        t.setNumberOfSeatInAuditorium("6");
        t.setSpectatorId(JMBG);
        t.setPerformance(performance);
        return t;
    }

    @Test
    @DisplayName("Ispravna kupovina: 200 i salje se potvrda na mail gledaoca")
    void validPurchaseSendsEmail() throws Exception {
        when(service.create(any())).thenReturn(createdTicket());
        when(usersServiceClient.getSpectator(JMBG))
                .thenReturn(new SpectatorDTO(JMBG, "Ana", "Petrovic", null, "ana@example.com", "ACTIVE"));

        mvc.perform(post("/tickets").contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("ul-1"));
        verify(emailService).sendTicketEmail(eq("ana@example.com"), anyString(), anyString());
    }

    @Test
    @DisplayName("Neispravna karta: 400 sa spiskom gresaka, servis se ne poziva")
    void invalidBodyRejected() throws Exception {
        mvc.perform(post("/tickets").contentType(MediaType.APPLICATION_JSON)
                .content("{\"numberOfSeatInAuditorium\":\"abc\",\"spectatorId\":\"12\",\"ticketAgentId\":\"{x}\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details", hasItem(startsWith("spectatorId:"))))
                .andExpect(jsonPath("$.details", hasItem(startsWith("ticketAgentId:"))))
                .andExpect(jsonPath("$.details", hasItem(startsWith("numberOfSeatInAuditorium:"))))
                .andExpect(jsonPath("$.details", hasItem(startsWith("performance:"))));
        verify(service, never()).create(any());
    }

    @Test
    @DisplayName("users-service stvarno nedostupan: 503")
    void usersServiceDownReturns503() throws Exception {
        when(service.create(any())).thenThrow(new UsersServiceUnavailableException("nedostupan", null));

        mvc.perform(post("/tickets").contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    @DisplayName("Gledalac u postupku brisanja: 409")
    void spectatorBeingDeletedReturns409() throws Exception {
        when(service.create(any())).thenThrow(new IllegalStateException("Gledalac je u postupku brisanja"));

        mvc.perform(post("/tickets").contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().isConflict());
        verify(emailService, never()).sendTicketEmail(anyString(), anyString(), anyString());
    }
}
