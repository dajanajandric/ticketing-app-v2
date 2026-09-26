package pozoriste1.users.spectators;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import pozoriste1.users.ticket_agents.TicketAgent;

@Entity
@Table(name = "teatar_gledalac")
public class Spectator {
	
	public Spectator() {}
	
	@Id
    @Column(name = "gledalac_jmbg")
    private String jmbg;
	
	@Column(name = "gledalac_ime")
	private String firstName;
	
	@Column(name = "gledalac_prezime")
	private String lastName;
	
	@Column(name = "gledalac_brtelefona")
	private String phoneNumber;
	
	@Column(name = "gledalac_email")
	private String emailAddress;
	
	@ManyToOne	
	@JoinColumn(name = "radnik_radnik_id")
	private TicketAgent ticketAgent;

	public String getJmbg() {
		return jmbg;
	}

	public void setJmbg(String jmbg) {
		this.jmbg = jmbg;
	}

	public String getFirstName() {
		return firstName;
	}

	public void setFirstName(String firstName) {
		this.firstName = firstName;
	}

	public String getLastName() {
		return lastName;
	}

	public void setLastName(String lastName) {
		this.lastName = lastName;
	}

	public String getPhoneNumber() {
		return phoneNumber;
	}

	public void setPhoneNumber(String phoneNumber) {
		this.phoneNumber = phoneNumber;
	}

	public String getEmailAddress() {
		return emailAddress;
	}

	public void setEmailAddress(String emailAddress) {
		this.emailAddress = emailAddress;
	}

	public TicketAgent getTicketAgent() {
		return ticketAgent;
	}

	public void setTicketAgent(TicketAgent ticketAgent) {
		this.ticketAgent = ticketAgent;
	}
	
}


