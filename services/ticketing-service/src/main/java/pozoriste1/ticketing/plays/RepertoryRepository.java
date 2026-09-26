package pozoriste1.ticketing.plays;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RepertoryRepository extends JpaRepository<Repertory, String> {
}