package pozoriste1.demo.plays;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RepertoryRepository extends JpaRepository<Repertory, String> {
}