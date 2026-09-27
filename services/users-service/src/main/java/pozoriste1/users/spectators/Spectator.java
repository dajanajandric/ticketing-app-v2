package pozoriste1.users.spectators;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import pozoriste1.users.ticket_agents.TicketAgent;
import pozoriste1.users.web.InputRules;
import pozoriste1.users.web.OnCreate;

@Entity
@Table(name = "teatar_gledalac")
public class Spectator {
	
	public Spectator() {}
	
	@Id
    @Column(name = "gledalac_jmbg")
	@NotBlank(groups = OnCreate.class)
	@Pattern(regexp = InputRules.JMBG, message = "JMBG mora imati tacno 13 cifara")
    private String jmbg;
	
	@Column(name = "gledalac_ime")
	@NotBlank(groups = OnCreate.class)
	@Size(max = 50)
	@Pattern(regexp = InputRules.NO_CONTROL_CHARS, message = "sadrzi nedozvoljene znakove")
	private String firstName;
	
	@Column(name = "gledalac_prezime")
	@NotBlank(groups = OnCreate.class)
	@Size(max = 50)
	@Pattern(regexp = InputRules.NO_CONTROL_CHARS, message = "sadrzi nedozvoljene znakove")
	private String lastName;
	
	@Column(name = "gledalac_brtelefona")
	@Pattern(regexp = "^\\+?[0-9 /-]{6,20}$", message = "neispravan broj telefona")
	private String phoneNumber;
	
	@Column(name = "gledalac_email")
	@Email
	@Size(max = 50)
	@Pattern(regexp = InputRules.NO_CONTROL_CHARS, message = "sadrzi nedozvoljene znakove")
	private String emailAddress;

	// Stanje sage brisanja; postavlja ga samo servis, klijent ga ne moze poslati.
	@Column(name = "gledalac_status")
	@Enumerated(EnumType.STRING)
	@JsonProperty(access = JsonProperty.Access.READ_ONLY)
	private SpectatorStatus status = SpectatorStatus.ACTIVE;
	
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

	// Redovi napravljeni prije uvodjenja statusa imaju NULL u bazi - to je ACTIVE.
	public SpectatorStatus getStatus() {
		return status == null ? SpectatorStatus.ACTIVE : status;
	}

	public void setStatus(SpectatorStatus status) {
		this.status = status;
	}

	public TicketAgent getTicketAgent() {
		return ticketAgent;
	}

	public void setTicketAgent(TicketAgent ticketAgent) {
		this.ticketAgent = ticketAgent;
	}
	
}


