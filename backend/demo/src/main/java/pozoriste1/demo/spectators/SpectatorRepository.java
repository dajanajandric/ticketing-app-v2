package pozoriste1.demo.spectators;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;


@Repository
public interface SpectatorRepository extends JpaRepository<Spectator, String> {
	Spectator findByJmbg(String jmbg);
}