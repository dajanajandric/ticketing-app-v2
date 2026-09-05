package pozoriste1.demo.tickets;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import pozoriste1.demo.plays.Performance;
import pozoriste1.demo.spectators.Spectator;
import pozoriste1.demo.ticket_agents.TicketAgent;

@Entity
@Table(name="teatar_ulaznica")
public class Ticket {
	@Id
	@Column(name="ulaznica_id")
	private String id;
	
	@Column(name="ulaznica_cijena")
	private String price;
	
	@Column(name="brojmjestausali")
	private String numberOfSeatInAuditorium;
	
	@ManyToOne
    @JoinColumn(name = "radnik_radnik_id")
    private TicketAgent ticketAgent;
	
	@ManyToOne
	@JoinColumn(name="gledalac_gledalac_jmbg")
	private Spectator spectator;
	
	@ManyToOne
	@JoinColumn(name="izvodjenje_izv_id")
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

	public TicketAgent getTicketAgent() {
		return ticketAgent;
	}

	public void setTicketAgent(TicketAgent ticketAgent) {
		this.ticketAgent = ticketAgent;
	}

	public Spectator getSpectator() {
		return spectator;
	}

	public void setSpectator(Spectator spectator) {
		this.spectator = spectator;
	}

	public Performance getPerformance() {
		return performance;
	}

	public void setPerformance(Performance performance) {
		this.performance = performance;
	}
	
}
