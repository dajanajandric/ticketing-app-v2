package pozoriste1.users.ticket_agents;

import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.JpaRepository;


@Repository
public interface TicketAgentRepository extends JpaRepository<TicketAgent, String> {
	TicketAgent findByUsernameAndPassword(String username, String password);
}
