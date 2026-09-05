package pozoriste1.demo.plays;

import jakarta.persistence.*;
import java.util.Set;

@Entity
@Table(name = "teatar_repertoar")
public class Repertory {

    @Id
    @Column(name = "rep_id")
    private String id;

    @Column(name = "repertoar_naziv")
    private String title;

    @ManyToMany
    @JoinTable(
        name = "teatar_izvodise",
        joinColumns = @JoinColumn(name = "repertoar_rep_id"),
        inverseJoinColumns = @JoinColumn(name = "izvodjenje_izv_id")
    )
    private Set<Performance> performances;

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

	public Set<Performance> getPerformances() {
		return performances;
	}

	public void setPerformances(Set<Performance> performances) {
		this.performances = performances;
	}

}

