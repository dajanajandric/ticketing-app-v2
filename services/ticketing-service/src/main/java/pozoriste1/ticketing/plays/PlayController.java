package pozoriste1.ticketing.plays;

import pozoriste1.ticketing.web.OnCreate;
import org.springframework.validation.annotation.Validated;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/plays")
@CrossOrigin(origins = "http://localhost:8080") 
public class PlayController {
	
	@Autowired
	private PlayService service;

	@GetMapping
    public List<Play> getAllPlays() {
        return service.getAll();
    }
	
	@GetMapping("/{id}")
    public ResponseEntity<Play> getPlayById(@PathVariable String id) {
    	Play play = service.getById(id);
    	return play == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(play);
    }
	
	@PostMapping
    public ResponseEntity<Play> createPlay(@Validated(OnCreate.class) @RequestBody Play play) {
        Play saved = service.create(play);
        return ResponseEntity.ok(saved);
    }
	
	@DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePlay(@PathVariable String id) {
        if (!service.existsById(id)) 
            return ResponseEntity.notFound().build(); 
        
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
	
	
}
