package pozoriste1.ticketing.tickets;

import org.springframework.stereotype.Service;

import pozoriste1.ticketing.plays.Performance;
import pozoriste1.ticketing.plays.PerformanceRepository;
import pozoriste1.ticketing.users.SpectatorDTO;
import pozoriste1.ticketing.users.UsersServiceClient;

import java.util.List;
import java.util.stream.IntStream;

@Service
public class TicketService {

	private final TicketRepository repository;

	private final PerformanceRepository performanceRepository;

	private final UsersServiceClient usersServiceClient;

	public TicketService(TicketRepository repository, PerformanceRepository performanceRepository,
			UsersServiceClient usersServiceClient) {
		this.repository = repository;
		this.performanceRepository = performanceRepository;
		this.usersServiceClient = usersServiceClient;
	}

	public List<Ticket> getAll() {
    	List<Ticket> tickets = repository.findAll();
        System.out.println(tickets);
        return tickets;
    }
	
	public Ticket getById(String id) {
		return repository.findById(id).orElse(null);
	}
	
	// check which seats are taken for the passed performance
	public List<String> getTakenSeats(String perfromanceId) {
		return repository.findByPerformance_Id(perfromanceId).stream().map(Ticket::getNumberOfSeatInAuditorium).toList();
	}
	
	// check which seats are available for the passed performance
	public List<Integer> getAvailableSeats(String performanceId) {
	    Performance performance = performanceRepository.findById(performanceId)
	        .orElseThrow(() -> new RuntimeException("Performance not found"));

	    int totalSeats = performance.getAuditorium().getNumOfSeats();
	    List<String> takenSeats = this.getTakenSeats(performance.getId());

	    return IntStream.rangeClosed(1, totalSeats)
	            .filter(i -> !takenSeats.contains(String.valueOf(i)))
	            .boxed()
	            .toList();
	}
	
	
	public Ticket create(Ticket t) {
		//check if the seat is taken
        List<Ticket> existingTickets = repository.findByPerformance_Id(t.getPerformance().getId());
        boolean seatTaken = existingTickets.stream()
                .anyMatch(ticket -> ticket.getNumberOfSeatInAuditorium().equals(t.getNumberOfSeatInAuditorium()));
        if(seatTaken)
        	throw new IllegalArgumentException("Seat " + t.getNumberOfSeatInAuditorium() +
                    " is already taken for performance " + t.getPerformance().getId());

        // Cross-service validacija preko REST-a (users-service) - jedini nacin da se
        // proveri postojanje jer TicketAgent/Spectator vise nisu u ovoj bazi.
        if (!usersServiceClient.agentExists(t.getTicketAgentId()))
        	throw new IllegalArgumentException("Ticket agent " + t.getTicketAgentId() + " does not exist");

        SpectatorDTO spectator = usersServiceClient.getSpectator(t.getSpectatorId());
        if (spectator == null)
        	throw new IllegalArgumentException("Spectator " + t.getSpectatorId() + " does not exist");

		return repository.save(t);
	}

}
