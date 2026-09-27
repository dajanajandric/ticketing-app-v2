package pozoriste1.ticketing.plays;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import pozoriste1.ticketing.web.InputRules;
import pozoriste1.ticketing.web.OnCreate;

@Entity
@Table(name = "teatar_predstava")

public class Play {
	@Id
    @Column(name = "predstava_id")
	@NotBlank(groups = OnCreate.class)
	@Pattern(regexp = InputRules.SAFE_ID, message = "dozvoljena su slova, cifre, . _ - (do 50)")
    private String id;
	
	@Column(name = "predstava_naziv")
	@NotBlank(groups = OnCreate.class)
	@Size(max = 50)
	@Pattern(regexp = InputRules.NO_CONTROL_CHARS, message = "sadrzi nedozvoljene znakove")
	private String title;
	
	@Column(name = "predstava_autorteksta")
	@Size(max = 50)
	@Pattern(regexp = InputRules.NO_CONTROL_CHARS, message = "sadrzi nedozvoljene znakove")
	private String playwright;
	
	@ManyToOne
    @JoinColumn(name = "scenograf_scenograf_id")
    private Scenographer scenographer;

    @ManyToOne
    @JoinColumn(name = "reziser_reziser_id")
    private Director director;

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

	public String getPlaywright() {
		return playwright;
	}

	public void setPlaywright(String playwright) {
		this.playwright = playwright;
	}

	public Scenographer getScenographer() {
		return scenographer;
	}

	public void setScenographer(Scenographer scenographer) {
		this.scenographer = scenographer;
	}

	public Director getDirector() {
		return director;
	}

	public void setDirector(Director director) {
		this.director = director;
	}
}
