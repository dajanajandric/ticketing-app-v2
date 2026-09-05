package pozoriste1.demo.plays;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "teatar_sala")

public class Auditorium {

	@Id
    @Column(name = "sala_id")
    private String id;
	
	@Column(name = "sala_naziv")
	private String title;
	
	@Column(name = "brojmjesta")
	private int numOfSeats;

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public int getNumOfSeats() {
		return numOfSeats;
	}

	public void setNumOfSeats(int numOfSeats) {
		this.numOfSeats = numOfSeats;
	}

	
	
}