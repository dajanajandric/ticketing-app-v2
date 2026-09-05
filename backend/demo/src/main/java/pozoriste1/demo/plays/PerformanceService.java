package pozoriste1.demo.plays;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class PerformanceService {
	
	 @Autowired
	    private PerformanceRepository repository;

	 public List<PerformanceDTO> getAll() {
		    return repository.findAll().stream()
		            .map(performance -> new PerformanceDTO(
		                    performance.getId(),
		                    performance.getPlay().getTitle(),
		                    performance.getShowtime().getDate(),
		                    performance.getShowtime().getTime().toLocalTime(),
		                    performance.getAuditorium().getTitle()
		            ))
		            .collect(Collectors.toList());
		}

	    
	    public Performance getById(String id) {
	    	return repository.findById(id).orElse(null);
	    }
	    
	    public Performance create(Performance i) {
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
