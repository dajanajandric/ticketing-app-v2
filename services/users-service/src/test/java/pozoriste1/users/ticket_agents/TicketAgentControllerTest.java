package pozoriste1.users.ticket_agents;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(TicketAgentController.class)
class TicketAgentControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private TicketAgentService service;

    private static TicketAgent agent() {
        TicketAgent a = new TicketAgent();
        a.setId("blag-01");
        a.setUsername("milica");
        a.setPassword("Milica123");
        return a;
    }

    @Test
    @DisplayName("Lista blagajnika ne otkriva lozinke")
    void listDoesNotExposePasswords() throws Exception {
        when(service.getAll()).thenReturn(List.of(agent()));

        mvc.perform(get("/ticket-agents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].username").value("milica"))
                .andExpect(jsonPath("$[0].password").doesNotExist());
    }

    @Test
    @DisplayName("Nepostojeci blagajnik vraca 404 - na to se oslanja ticketing-service")
    void unknownAgentReturns404() throws Exception {
        mvc.perform(get("/ticket-agents/{id}", "nepostoji")).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Prijava radi i dalje, iako se lozinka ne vraca u odgovorima")
    void loginStillWorks() throws Exception {
        when(service.validateCredentials("milica", "Milica123")).thenReturn(true);

        mvc.perform(post("/ticket-agents/login").param("username", "milica").param("password", "Milica123"))
                .andExpect(status().isOk());
        mvc.perform(post("/ticket-agents/login").param("username", "milica").param("password", "pogresna"))
                .andExpect(status().isUnauthorized());
    }
}
