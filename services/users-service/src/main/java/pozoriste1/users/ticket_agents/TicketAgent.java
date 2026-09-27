package pozoriste1.users.ticket_agents;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "teatar_radnik")
public class TicketAgent {

    @Id
    @Column(name = "radnik_id")
    private String id;

    @Column(name = "radnik_username")
    private String username;

    // Lozinka se prima pri upisu, ali se nikad ne vraca u odgovoru
    @Column(name = "radnik_password") 
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String password;

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getUsername() {
		return username;
	}

	public void setUsername(String username) {
		this.username = username;
	}

	public String getPassword() {
		return password;
	}

	public void setPassword(String password) {
		this.password = password;
	}

    
}
