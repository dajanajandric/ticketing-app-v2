package pozoriste1.users.spectators;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import pozoriste1.users.saga.SpectatorDeletionSaga;
import pozoriste1.users.web.OnCreate;

@RestController
@RequestMapping("/spectators")
public class SpectatorController {
	@Autowired
    private SpectatorService service;

	@Autowired
	private SpectatorDeletionSaga deletionSaga;

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
    public ResponseEntity<Spectator> createSpectator(@Validated(OnCreate.class) @RequestBody Spectator spectator) {
        Spectator saved = service.create(spectator);
        return ResponseEntity.ok(saved);
    }
    
    @PatchMapping("/{jmbg}")
    public ResponseEntity<Spectator> updateGledalac(@PathVariable String jmbg,
                                                   @Valid @RequestBody Spectator update) {
        Spectator updated = service.updatePartial(jmbg, update);
        if (updated == null) 
            return ResponseEntity.notFound().build();
        
        return ResponseEntity.ok(updated);
    }
    
    // Brisanje je saga (vidi SpectatorDeletionSaga): odgovor 202 znaci "zahtjev prihvacen",
    // a gledalac se stvarno brise tek kad ticketing-service potvrdi da nema buducih karata.
    @DeleteMapping("/{jmbg}")
    public ResponseEntity<Map<String, String>> deleteSpectator(@PathVariable String jmbg) {
        if (!service.existsByJmbg(jmbg)) 
            return ResponseEntity.notFound().build(); 
        
        String sagaId = deletionSaga.start(jmbg);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(Map.of("sagaId", sagaId, "jmbg", jmbg, "status", SpectatorStatus.DELETION_PENDING.name()));
    }


    
	

}
