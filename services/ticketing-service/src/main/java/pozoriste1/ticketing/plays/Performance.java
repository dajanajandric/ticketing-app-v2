package pozoriste1.ticketing.plays;

import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.*;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import pozoriste1.ticketing.web.InputRules;
import pozoriste1.ticketing.web.OnCreate;

@Entity
@Table(name = "teatar_izvodjenje")
public class Performance {

    @Id
    @Column(name = "izv_id")
	@NotBlank(groups = OnCreate.class)
	@Pattern(regexp = InputRules.SAFE_ID, message = "dozvoljena su slova, cifre, . _ - (do 50)")
    private String id;

    @ManyToOne
    @JoinColumn(name = "sala_sala_id")
    @NotNull(groups = OnCreate.class)
    private Auditorium auditorium;

    @ManyToOne
    @JoinColumn(name = "termin_termin_id")
    @NotNull(groups = OnCreate.class)
    private Showtime showtime;

    @ManyToOne
    // Bez sale, termina i predstave izvodjenje obara GET /performances (fuzz nalaz, 2. krug)
    @JoinColumn(name = "predstava_predstava_id")
    @NotNull(groups = OnCreate.class)
    private Play play;
    
    @ManyToMany(mappedBy = "performances")
    private Set<Repertory> repertories;

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public Auditorium getAuditorium() {
		return auditorium;
	}

	public void setAuditorium(Auditorium auditorium) {
		this.auditorium = auditorium;
	}

	public Showtime getShowtime() {
		return showtime;
	}

	public void setShowtime(Showtime showtime) {
		this.showtime = showtime;
	}

	public Play getPlay() {
		return play;
	}

	public void setPlay(Play play) {
		this.play = play;
	}

	// Repertory -> performances -> repertories -> ... bi se serijalizovalo u krug
	@JsonIgnore
	public Set<Repertory> getRepertoari() {
		return repertories;
	}

	public void setRepertoari(Set<Repertory> repertories) {
		this.repertories = repertories;
	}


    
}
