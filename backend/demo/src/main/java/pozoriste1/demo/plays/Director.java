package pozoriste1.demo.plays;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "teatar_reziser")

public class Director {

	@Id
    @Column(name = "reziser_id")
    private String id;
	
	@Column(name = "reziser_ime")
	private String firstName;
	
	@Column(name = "reziser_prezime")
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

