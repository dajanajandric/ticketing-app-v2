package pozoriste1.demo.plays;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PerformanceRepository extends JpaRepository<Performance, String> {
	List<Performance> findByPlayId(String playId);
}
