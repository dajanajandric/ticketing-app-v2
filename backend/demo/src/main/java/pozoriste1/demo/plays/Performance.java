package pozoriste1.demo.plays;

import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.*;

@Entity
@Table(name = "teatar_izvodjenje")
public class Performance {

    @Id
    @Column(name = "izv_id")
    private String id;

    @ManyToOne
    @JoinColumn(name = "sala_sala_id")
    private Auditorium auditorium;

    @ManyToOne
    @JoinColumn(name = "termin_termin_id")
    private Showtime showtime;

    @ManyToOne
    @JoinColumn(name = "predstava_predstava_id")
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
