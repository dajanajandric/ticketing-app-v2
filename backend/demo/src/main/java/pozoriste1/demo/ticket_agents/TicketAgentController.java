package pozoriste1.demo.ticket_agents;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/ticket-agents")
@CrossOrigin(origins = "http://localhost:8080") 
public class TicketAgentController {

    @Autowired
    private TicketAgentService service;

    @GetMapping
    public List<TicketAgent> getAll() {
        return service.getAll();
    }
    
    @PostMapping("/login")
    public ResponseEntity<String> login(@RequestParam String username, @RequestParam String password) {
    	boolean isValid = service.validateCredentials(username, password);
    	if(isValid) return ResponseEntity.ok("Valid credentials.");
    	else return ResponseEntity.status(401).body("Invalid username or password");
    }
    
    @GetMapping("/{id}")
    public TicketAgent getById(@PathVariable String id) {
    	return service.getById(id);
    }
    
}
