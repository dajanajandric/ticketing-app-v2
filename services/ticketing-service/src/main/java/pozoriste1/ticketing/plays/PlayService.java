package pozoriste1.ticketing.plays;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class PlayService {
	
	 @Autowired
	    private PlayRepository repository;

	    public List<Play> getAll() {
	    	List<Play> plays = repository.findAll();
	        System.out.println(plays);
	        return plays;
	    }
	    
	    public Play getById(String id) {
	    	Optional<Play> optionalPlay = repository.findById(id);
	        return optionalPlay.orElse(null);
	    }
	    
	    public Play create(Play p) {
		    return repository.save(p);
		}
	    
	    public boolean existsById(String id) {
		    return repository.existsById(id);
		}

	    public void delete(String id) {
	        repository.deleteById(id);
	    }
}
