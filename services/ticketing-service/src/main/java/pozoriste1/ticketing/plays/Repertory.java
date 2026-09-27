package pozoriste1.ticketing.plays;

import jakarta.persistence.*;
import java.util.Set;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import pozoriste1.ticketing.web.InputRules;
import pozoriste1.ticketing.web.OnCreate;

@Entity
@Table(name = "teatar_repertoar")
public class Repertory {

    @Id
    @Column(name = "rep_id")
	@NotBlank(groups = OnCreate.class)
	@Pattern(regexp = InputRules.SAFE_ID, message = "dozvoljena su slova, cifre, . _ - (do 50)")
    private String id;

    @Column(name = "repertoar_naziv")
    @NotBlank(groups = OnCreate.class)
    @Size(max = 50)
    @Pattern(regexp = InputRules.NO_CONTROL_CHARS, message = "sadrzi nedozvoljene znakove")
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

