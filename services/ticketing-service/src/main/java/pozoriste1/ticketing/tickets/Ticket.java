package pozoriste1.ticketing.tickets;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import pozoriste1.ticketing.plays.Performance;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import pozoriste1.ticketing.web.InputRules;
import pozoriste1.ticketing.web.OnCreate;

@Entity
@Table(name="teatar_ulaznica")
public class Ticket {
	@Id
	@Column(name="ulaznica_id")
	@NotBlank(groups = OnCreate.class)
	@Pattern(regexp = InputRules.SAFE_ID, message = "dozvoljena su slova, cifre, . _ - (do 50)")
	private String id;
	
	@Column(name="ulaznica_cijena")
	@Size(max = 20)
	@Pattern(regexp = InputRules.NO_CONTROL_CHARS, message = "sadrzi nedozvoljene znakove")
	private String price;
	
	@Column(name="brojmjestausali")
	@NotBlank(groups = OnCreate.class)
	@Pattern(regexp = "^[0-9]{1,4}$", message = "broj mjesta mora biti broj")
	private String numberOfSeatInAuditorium;
	
	// Ticket_agents i Spectators zive u users-service (odvojena baza) - ovde ostaje
	// samo strani identifikator, ne JPA relacija; postojanje se proverava preko
	// UsersServiceClient pri kreiranju ulaznice.
	@Column(name = "radnik_radnik_id")
	@NotBlank(groups = OnCreate.class)
	@Pattern(regexp = InputRules.SAFE_ID, message = "neispravan ID blagajnika")
	private String ticketAgentId;

	@Column(name = "gledalac_gledalac_jmbg")
	@NotBlank(groups = OnCreate.class)
	@Pattern(regexp = InputRules.JMBG, message = "JMBG mora imati tacno 13 cifara")
	private String spectatorId;

	@ManyToOne
	@JoinColumn(name="izvodjenje_izv_id")
	@NotNull(groups = OnCreate.class)
	private Performance performance;

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getPrice() {
		return price;
	}

	public void setPrice(String price) {
		this.price = price;
	}

	public String getNumberOfSeatInAuditorium() {
		return numberOfSeatInAuditorium;
	}

	public void setNumberOfSeatInAuditorium(String numberOfSeatInAuditorium) {
		this.numberOfSeatInAuditorium = numberOfSeatInAuditorium;
	}

	public String getTicketAgentId() {
		return ticketAgentId;
	}

	public void setTicketAgentId(String ticketAgentId) {
		this.ticketAgentId = ticketAgentId;
	}

	public String getSpectatorId() {
		return spectatorId;
	}

	public void setSpectatorId(String spectatorId) {
		this.spectatorId = spectatorId;
	}

	public Performance getPerformance() {
		return performance;
	}

	public void setPerformance(Performance performance) {
		this.performance = performance;
	}
	
}
