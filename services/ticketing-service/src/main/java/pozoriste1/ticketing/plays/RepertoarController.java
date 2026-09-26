package pozoriste1.ticketing.plays;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequestMapping("/repertory")
public class RepertoarController {

    @Autowired
    private RepertoryService service;

    @GetMapping("/{id}")
    public ResponseEntity<Repertory> getRepertoryById(@PathVariable String id) {
        Optional<Repertory> repertory = service.getById(id);
        return repertory.map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<Repertory> createRepertory(@RequestBody Repertory repertory) {
        Repertory saved = service.save(repertory);
        return ResponseEntity.ok(saved);
    }

    @PostMapping("/{repertoryId}/performances/{performanceId}")
    public ResponseEntity<?> addPerformanceToRepertory(@PathVariable String repertoryId, @PathVariable String performanceId) {
        boolean success = service.addPerformance(repertoryId, performanceId);
        return success ? ResponseEntity.ok().build() : ResponseEntity.notFound().build();
    }

    @DeleteMapping("/{repertoryId}/performances/{performanceId}")
    public ResponseEntity<?> removePerformanceFromRepertory(@PathVariable String repertoryId, @PathVariable String performanceId) {
        boolean success = service.removePerformance(repertoryId, performanceId);
        return success ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }
}


