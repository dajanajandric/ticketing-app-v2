package pozoriste1.ticketing.tickets;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TicketRepository extends JpaRepository<Ticket, String> {
	List<Ticket> findByPerformance_Id(String performanceId);
}
