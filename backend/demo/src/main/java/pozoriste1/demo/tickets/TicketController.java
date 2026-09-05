package pozoriste1.demo.tickets;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;



@RestController
@RequestMapping("/tickets")
@CrossOrigin(origins = "http://localhost:8080") 
public class TicketController {

	@Autowired
	private TicketService service;
	
	@Autowired
	private EmailService emailService;
	
	@GetMapping
    public ResponseEntity<List<Ticket>> getAllTickets() {
        return ResponseEntity.ok(service.getAll());
    }
	
	 @GetMapping("/{id}")
	    public ResponseEntity<Ticket> getTiketById(@PathVariable String id) {
	        Ticket ticket = service.getById(id);
	        if (ticket == null) 
	            return ResponseEntity.notFound().build();
	        
	        return ResponseEntity.ok(ticket);
	    }
	 
	 @PostMapping
	 public ResponseEntity<?> createTicket(@RequestBody Ticket u) {
	     try {
	         Ticket created = service.create(u);

	         // Provera da li gledaoc ima email
	         if (created.getSpectator().getEmailAddress() != null) {
	             String email = created.getSpectator().getEmailAddress();
	             String subject = "Potvrda kupovine karte";
	             String body = "Upravo ste kupili kartu za predstavu: " 
	                         + created.getPerformance().getPlay().getTitle() + "\n" 
	                         + "Detalji ulaznice:\n"
	                         + "Datum: " + created.getPerformance().getShowtime().getDate() + "\n"
	                         + "Vreme: " + created.getPerformance().getShowtime().getTime() + "\n"
	                         + "Broj sedista: " + created.getNumberOfSeatInAuditorium();
	             
	             emailService.sendTicketEmail(email, subject, body);
	         }

	         return ResponseEntity.ok(created);
	     } catch (IllegalArgumentException e) {
	         return ResponseEntity.badRequest().body(e.getMessage());
	     }
	 }
	
	 @PostMapping("/performance/available-seats")
	 public ResponseEntity<List<Integer>> getAvailableSeats(@RequestBody Map<String, String> payload) {
	     String performanceId = payload.get("id");
	     List<Integer> availableSeats = service.getAvailableSeats(performanceId);
	     return ResponseEntity.ok(availableSeats);
	 }

	
}
