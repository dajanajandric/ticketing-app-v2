package pozoriste1.ticketing.plays;

import pozoriste1.ticketing.web.OnCreate;
import org.springframework.validation.annotation.Validated;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/performances")
public class PerformanceController {
	
	@Autowired
	private PerformanceService service;

	@GetMapping
    public List<PerformanceDTO> getAllPerformances() {
        return service.getAll();
    }
	
	@GetMapping("/{id}")
    public ResponseEntity<Performance> getPerformanceById(@PathVariable String id) {
    	Performance performance = service.getById(id);
    	return performance == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(performance);
    }
	
	@PostMapping
    public ResponseEntity<Performance> createPerformance(@Validated(OnCreate.class) @RequestBody Performance performance) {
        Performance saved = service.create(performance);
        return ResponseEntity.ok(saved);
    }
	
	@DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePerformance(@PathVariable String id) {
        if (!service.existsById(id)) {
            return ResponseEntity.notFound().build(); 
        }
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
	
	@GetMapping("/by-play/{playId}")
	public ResponseEntity<List<PerformanceDTO>> getPerformancesByPlay(@PathVariable String playId) {
	    List<PerformanceDTO> performances = service.getByPlayId(playId);
	    if (performances.isEmpty()) {
	        return ResponseEntity.noContent().build();
	    }
	    return ResponseEntity.ok(performances);
	}

}
