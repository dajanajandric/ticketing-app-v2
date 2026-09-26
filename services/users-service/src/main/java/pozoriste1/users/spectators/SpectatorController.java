package pozoriste1.users.spectators;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/spectators")
@CrossOrigin(origins = "http://localhost:8080") 
public class SpectatorController {
	@Autowired
    private SpectatorService service;

    @GetMapping
    public List<Spectator> getAll() {
        return service.getAll();
    }
    
    @GetMapping("/{jmbg}")
    public ResponseEntity<Spectator> getByJmbg(@PathVariable String jmbg) {
    	Spectator spectator = service.getByJmbg(jmbg);
    	if (spectator == null)
    		return ResponseEntity.notFound().build();
    	return ResponseEntity.ok(spectator);
    }
    
    @PostMapping
    public ResponseEntity<Spectator> createSpectator(@RequestBody Spectator spectator) {
        Spectator saved = service.create(spectator);
        return ResponseEntity.ok(saved);
    }
    
    @PatchMapping("/{jmbg}")
    public ResponseEntity<Spectator> updateGledalac(@PathVariable String jmbg,
                                                   @RequestBody Spectator update) {
        Spectator updated = service.updatePartial(jmbg, update);
        if (updated == null) 
            return ResponseEntity.notFound().build();
        
        return ResponseEntity.ok(updated);
    }
    
    @DeleteMapping("/{jmbg}")
    public ResponseEntity<Void> deleteSpectator(@PathVariable String jmbg) {
        if (!service.existsByJmbg(jmbg)) 
            return ResponseEntity.notFound().build(); 
        
        service.delete(jmbg);
        return ResponseEntity.noContent().build();
    }


    
	

}
