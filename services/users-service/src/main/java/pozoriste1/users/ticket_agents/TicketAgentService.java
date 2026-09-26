package pozoriste1.users.ticket_agents;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;


@Service
public class TicketAgentService {
	
	 @Autowired
	    private TicketAgentRepository repository;

	    public List<TicketAgent> getAll() {
	    	List<TicketAgent> TicketAgents = repository.findAll();
	        System.out.println(TicketAgents);
	        return TicketAgents;
	    }
	    
	    public boolean validateCredentials(String username, String password) {
	        TicketAgent TicketAgent = repository.findByUsernameAndPassword(username, password);
	        return TicketAgent != null;
	    }
	    
	    public TicketAgent getById(String id) {
	    	return repository.findById(id).orElse(null);

	    }
}
