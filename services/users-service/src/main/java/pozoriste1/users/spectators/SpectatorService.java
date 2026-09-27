package pozoriste1.users.spectators; 

import org.springframework.stereotype.Service;

import pozoriste1.users.ticket_agents.TicketAgent;
import pozoriste1.users.ticket_agents.TicketAgentRepository;

import java.util.List;
import java.util.Optional;

@Service
public class SpectatorService {

	private final SpectatorRepository repository;

	private final TicketAgentRepository ticketAgentRepository;

	public SpectatorService(SpectatorRepository repository, TicketAgentRepository ticketAgentRepository) {
		this.repository = repository;
		this.ticketAgentRepository = ticketAgentRepository;
	}

	public List<Spectator> getAll() {
    	List<Spectator> spectators = repository.findAll();
        System.out.println(spectators);
        return spectators;
    }
	
	public Spectator getByJmbg(String jmbg) {
	    return repository.findByJmbg(jmbg);
	}
	
	public Spectator create(Spectator g) {
		// save() bi postojeceg gledaoca tiho prepisao
		if (repository.existsById(g.getJmbg()))
			throw new IllegalStateException("Gledalac " + g.getJmbg() + " vec postoji");
		g.setStatus(SpectatorStatus.ACTIVE);
		g.setTicketAgent(resolveAgent(g.getTicketAgent()));
	    return repository.save(g);
	}

	// Blagajnik iz zahtjeva mora postojati; inace bi JPA bacio gresku (500)
	private TicketAgent resolveAgent(TicketAgent agent) {
		if (agent == null || agent.getId() == null)
			return null;
		return ticketAgentRepository.findById(agent.getId())
				.orElseThrow(() -> new IllegalArgumentException("Blagajnik " + agent.getId() + " ne postoji"));
	}
	
	public Spectator updatePartial(String jmbg, Spectator update) {
        Optional<Spectator> existingOpt = repository.findById(jmbg);
        if (existingOpt.isEmpty()) {
            return null;
        }

        Spectator existing = existingOpt.get();

        if (update.getFirstName() != null) existing.setFirstName(update.getFirstName());
        if (update.getLastName() != null) existing.setLastName(update.getLastName());
        if (update.getPhoneNumber() != null) existing.setPhoneNumber(update.getPhoneNumber());
        if (update.getEmailAddress() != null) existing.setEmailAddress(update.getEmailAddress());

        if (update.getTicketAgent() != null && update.getTicketAgent().getId() != null)
            existing.setTicketAgent(resolveAgent(update.getTicketAgent()));

        return repository.save(existing);
    }
	
	public boolean existsByJmbg(String jmbg) {
	    return repository.existsById(jmbg);
	}
	
}