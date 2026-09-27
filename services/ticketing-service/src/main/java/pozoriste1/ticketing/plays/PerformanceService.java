package pozoriste1.ticketing.plays;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class PerformanceService {
	
	 @Autowired
	    private PerformanceRepository repository;

	 public List<PerformanceDTO> getAll() {
		    // Nepotpuno izvodjenje (npr. staro, bez termina) preskacemo umjesto da cijela lista pukne
		    return repository.findAll().stream()
		            .filter(p -> p.getPlay() != null && p.getShowtime() != null && p.getShowtime().getTime() != null)
		            .map(performance -> new PerformanceDTO(
		                    performance.getId(),
		                    performance.getPlay().getTitle(),
		                    performance.getShowtime().getDate(),
		                    performance.getShowtime().getTime().toLocalTime(),
		                    performance.getAuditorium() != null ? performance.getAuditorium().getTitle() : null
		            ))
		            .collect(Collectors.toList());
		}

	    
	    public Performance getById(String id) {
	    	return repository.findById(id).orElse(null);
	    }
	    
	    public Performance create(Performance i) {
		// save() bi postojeci objekat sa istim ID-em tiho prepisao
		if (repository.existsById(i.getId()))
			throw new IllegalStateException("Izvodjenje " + i.getId() + " vec postoji");
		    return repository.save(i);
		}
	    
	    public boolean existsById(String id) {
		    return repository.existsById(id);
		}

	    public void delete(String id) {
	        repository.deleteById(id);
	    }
	    
	    public List<PerformanceDTO> getByPlayId(String playId) {
	        List<Performance> performances = repository.findByPlayId(playId);

	        return performances.stream()
	        		.filter(p -> p.getPlay() != null && p.getShowtime() != null && p.getShowtime().getTime() != null)
	        		.map(p -> new PerformanceDTO(
	        			    p.getId(),
	        			    p.getPlay().getId(),
	        			    p.getShowtime().getDate(),
	        			    p.getShowtime().getTime().toLocalTime(),
	        			    p.getAuditorium() != null ? p.getAuditorium().getTitle() : null
	        			))

	                .toList();
	    }
}
