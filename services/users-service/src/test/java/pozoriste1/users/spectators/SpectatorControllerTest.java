package pozoriste1.users.spectators;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import pozoriste1.users.saga.SpectatorDeletionSaga;

/** Validacija ulaza, handler gresaka i HTTP strana sage - bez baze i bez RabbitMQ-a. */
@WebMvcTest(SpectatorController.class)
class SpectatorControllerTest {

    private static final String JMBG = "0101990800007";

    @Autowired
    private MockMvc mvc;

    @MockBean
    private SpectatorService service;

    @MockBean
    private SpectatorDeletionSaga deletionSaga;

    @Test
    @DisplayName("POST sa neispravnim JMBG-om i bez imena vraca 400 sa spiskom gresaka")
    void createRejectsInvalidBody() throws Exception {
        mvc.perform(post("/spectators").contentType(MediaType.APPLICATION_JSON)
                .content("{\"jmbg\":\"123\",\"emailAddress\":\"nije-email\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details", hasItem(startsWith("jmbg:"))))
                .andExpect(jsonPath("$.details", hasItem(startsWith("firstName:"))))
                .andExpect(jsonPath("$.details", hasItem(startsWith("emailAddress:"))));
        verify(service, never()).create(any());
    }

    @Test
    @DisplayName("POST sa NUL znakom u imenu vraca 400 (ranije: greska baze, 500)")
    void createRejectsControlCharacters() throws Exception {
        mvc.perform(post("/spectators").contentType(MediaType.APPLICATION_JSON)
                .content("{\"jmbg\":\"" + JMBG + "\",\"firstName\":\"Ana\\u0000\",\"lastName\":\"Petrovic\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details", hasItem(startsWith("firstName:"))));
    }

    @Test
    @DisplayName("POST sa ispravnim podacima prolazi")
    void createAcceptsValidBody() throws Exception {
        when(service.create(any())).thenAnswer(inv -> inv.getArgument(0));

        mvc.perform(post("/spectators").contentType(MediaType.APPLICATION_JSON)
                .content("{\"jmbg\":\"" + JMBG + "\",\"firstName\":\"Ana\",\"lastName\":\"Petrovic\","
                        + "\"phoneNumber\":\"0601234567\",\"emailAddress\":\"ana@example.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jmbg").value(JMBG));
    }

    @Test
    @DisplayName("POST za postojeceg gledaoca vraca 409 umjesto da ga prepise")
    void createConflictsOnDuplicate() throws Exception {
        when(service.create(any())).thenThrow(new IllegalStateException("Gledalac vec postoji"));

        mvc.perform(post("/spectators").contentType(MediaType.APPLICATION_JSON)
                .content("{\"jmbg\":\"" + JMBG + "\",\"firstName\":\"Ana\",\"lastName\":\"Petrovic\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("DELETE pokrece sagu i vraca 202 Accepted")
    void deleteStartsSaga() throws Exception {
        when(service.existsByJmbg(JMBG)).thenReturn(true);
        when(deletionSaga.start(JMBG)).thenReturn("saga-1");

        mvc.perform(delete("/spectators/{jmbg}", JMBG))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.sagaId").value("saga-1"))
                .andExpect(jsonPath("$.status").value("DELETION_PENDING"));
    }

    @Test
    @DisplayName("DELETE nepostojeceg gledaoca vraca 404 i ne pokrece sagu")
    void deleteUnknownReturns404() throws Exception {
        when(service.existsByJmbg(JMBG)).thenReturn(false);

        mvc.perform(delete("/spectators/{jmbg}", JMBG)).andExpect(status().isNotFound());
        verify(deletionSaga, never()).start(any());
    }

    @Test
    @DisplayName("DELETE dok brisanje vec traje vraca 409")
    void deleteWhilePendingReturns409() throws Exception {
        when(service.existsByJmbg(JMBG)).thenReturn(true);
        when(deletionSaga.start(JMBG)).thenThrow(new IllegalStateException("Brisanje je vec u toku"));

        mvc.perform(delete("/spectators/{jmbg}", JMBG)).andExpect(status().isConflict());
    }

    @Test
    @DisplayName("DELETE kad RabbitMQ ne radi vraca 503")
    void deleteWhenBrokerDownReturns503() throws Exception {
        when(service.existsByJmbg(JMBG)).thenReturn(true);
        when(deletionSaga.start(JMBG)).thenThrow(new AmqpException("broker ne radi"));

        mvc.perform(delete("/spectators/{jmbg}", JMBG)).andExpect(status().isServiceUnavailable());
    }

    @Test
    @DisplayName("Putanja sa NUL znakom se odbija sa 400 prije kontrolera")
    void pathWithControlCharacterRejected() throws Exception {
        mvc.perform(get(URI.create("/spectators/%00"))).andExpect(status().isBadRequest());
        verify(service, never()).getByJmbg(any());
    }
}
