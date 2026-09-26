package pozoriste1.ticketing.plays;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "teatar_termin")

public class Showtime {

	@Id
    @Column(name = "termin_id")
    private String id;
	
	@Column(name = "termin_datum")
	private LocalDate date;
	
	@Column(name = "termin_vrijeme")
	private LocalDateTime time;

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public LocalDate getDate() {
		return date;
	}

	public void setDate(LocalDate date) {
		this.date = date;
	}

	public LocalDateTime getTime() {
		return time;
	}

	public void setTime(LocalDateTime time) {
		this.time = time;
	}
	
}