package pozoriste1.ticketing.users;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

/**
 * Neispravan ID (npr. "{x}") ne sme da napravi mrezni poziv, a ispravan se salje
 * kao parametar URI sablona.
 */
@ExtendWith(MockitoExtension.class)
class UsersServiceClientTest {

    private static final String BASE = "http://users";

    @Mock
    private RestTemplate restTemplate;

    private UsersServiceClient client;

    @BeforeEach
    void setUp() {
        client = new UsersServiceClient(restTemplate, BASE);
    }

    @ParameterizedTest
    @ValueSource(strings = { "{x}", "../ticket-agents/blag-01", "a b", "", "blag-01?admin=1" })
    @DisplayName("Neispravan ID blagajnika: odgovor 'ne postoji' bez mreznog poziva")
    void invalidAgentIdMakesNoCall(String id) {
        assertThat(client.agentExists(id)).isFalse();
        verifyNoInteractions(restTemplate);
    }

    @Test
    @DisplayName("Neispravan JMBG: nema mreznog poziva")
    void invalidSpectatorIdMakesNoCall() {
        assertThat(client.getSpectator("{jmbg}")).isNull();
        assertThat(client.getSpectator(null)).isNull();
        verifyNoInteractions(restTemplate);
    }

    @Test
    @DisplayName("Ispravan ID se salje kao parametar URI sablona, ne lijepi se u URL")
    void validIdPassedAsUriVariable() {
        when(restTemplate.getForEntity(BASE + "/ticket-agents/{id}", Void.class, "blag-01"))
                .thenReturn(ResponseEntity.ok().build());

        assertThat(client.agentExists("blag-01")).isTrue();
        verify(restTemplate).getForEntity(BASE + "/ticket-agents/{id}", Void.class, "blag-01");
    }

    @Test
    @DisplayName("404 od users-service znaci 'ne postoji', nije kvar")
    void notFoundMeansMissing() {
        when(restTemplate.getForEntity(BASE + "/ticket-agents/{id}", Void.class, "blag-99"))
                .thenThrow(HttpClientErrorException.create(HttpStatus.NOT_FOUND, "Not Found", null, null, null));
        when(restTemplate.getForObject(BASE + "/spectators/{jmbg}", SpectatorDTO.class, "0101990800007"))
                .thenThrow(HttpClientErrorException.create(HttpStatus.NOT_FOUND, "Not Found", null, null, null));

        assertThat(client.agentExists("blag-99")).isFalse();
        assertThat(client.getSpectator("0101990800007")).isNull();
    }
}
