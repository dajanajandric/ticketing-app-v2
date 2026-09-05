package pozoriste1.demo.tickets;

import org.springframework.stereotype.Service;

import pozoriste1.demo.plays.Performance;
import pozoriste1.demo.plays.PerformanceRepository;

import java.util.List;
import java.util.stream.IntStream;

@Service
public class TicketService {

	private final TicketRepository repository;

	private final PerformanceRepository performanceRepository;

	public TicketService(TicketRepository repository, PerformanceRepository performanceRepository) {
		this.repository = repository;
		this.performanceRepository = performanceRepository;
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
        
		return repository.save(t);
	}

}
