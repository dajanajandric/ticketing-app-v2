package pozoriste1.ticketing.tickets;

import org.springframework.stereotype.Service;

import pozoriste1.ticketing.plays.Performance;
import pozoriste1.ticketing.plays.PerformanceRepository;
import pozoriste1.ticketing.users.SpectatorDTO;
import pozoriste1.ticketing.users.UsersServiceClient;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
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
	
	// zauzeta mjesta za dato izvodjenje
	public List<String> getTakenSeats(String perfromanceId) {
		return repository.findByPerformance_Id(perfromanceId).stream().map(Ticket::getNumberOfSeatInAuditorium).toList();
	}
	
	// slobodna mjesta za dato izvodjenje
	public List<Integer> getAvailableSeats(String performanceId) {
	    if (performanceId == null)
	        throw new IllegalArgumentException("Nedostaje id izvodjenja");
	    Performance performance = performanceRepository.findById(performanceId)
	        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Izvodjenje " + performanceId + " ne postoji"));

	    int totalSeats = performance.getAuditorium().getNumOfSeats();
	    List<String> takenSeats = this.getTakenSeats(performance.getId());

	    return IntStream.rangeClosed(1, totalSeats)
	            .filter(i -> !takenSeats.contains(String.valueOf(i)))
	            .boxed()
	            .toList();
	}
	
	
	public Ticket create(Ticket t) {
		// save() bi postojecu kartu sa istim ID-em tiho prepisao
		if (repository.existsById(t.getId()))
			throw new IllegalStateException("Karta " + t.getId() + " vec postoji");

		// Izvodjenje mora postojati, a mesto mora biti u sali
		if (t.getPerformance() == null || t.getPerformance().getId() == null)
			throw new IllegalArgumentException("Nedostaje izvodjenje");
		Performance performance = performanceRepository.findById(t.getPerformance().getId())
				.orElseThrow(() -> new IllegalArgumentException("Izvodjenje " + t.getPerformance().getId() + " ne postoji"));
		int seat = Integer.parseInt(t.getNumberOfSeatInAuditorium());
		if (performance.getAuditorium() != null && (seat < 1 || seat > performance.getAuditorium().getNumOfSeats()))
			throw new IllegalArgumentException("Sala ima mjesta 1-" + performance.getAuditorium().getNumOfSeats());
		t.setPerformance(performance);
		t.setNumberOfSeatInAuditorium(String.valueOf(seat)); // "007" -> "7", kao u getAvailableSeats

		// provjera da li je mjesto vec zauzeto
        List<Ticket> existingTickets = repository.findByPerformance_Id(performance.getId());
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
        // Dok traje saga brisanja gledaoca, ne prodajemo mu nove karte
        if ("DELETION_PENDING".equals(spectator.status()))
        	throw new IllegalStateException("Gledalac " + t.getSpectatorId() + " je u postupku brisanja");

		return repository.save(t);
	}

}
