package pozoriste1.ticketing.plays;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "teatar_scenograf")

public class Scenographer {

	@Id
    @Column(name = "scenograf_id")
    private String id;
	
	@Column(name = "scenograf_ime")
	private String firstName;
	
	@Column(name = "scenograf_prezime")
	private String lastName;

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
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

}
