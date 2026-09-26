package pozoriste1.users.ticket_agents;

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

    @Column(name = "radnik_password") 
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
