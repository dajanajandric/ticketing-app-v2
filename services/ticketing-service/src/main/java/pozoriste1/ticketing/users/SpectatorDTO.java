package pozoriste1.ticketing.users;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SpectatorDTO(String jmbg, String firstName, String lastName, String phoneNumber, String emailAddress) {
}
