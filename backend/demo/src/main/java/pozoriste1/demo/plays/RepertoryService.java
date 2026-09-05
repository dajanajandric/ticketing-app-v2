package pozoriste1.demo.plays;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class RepertoryService {

    @Autowired
    private RepertoryRepository repository;

    @Autowired
    private PerformanceRepository performanceRepository;

    public Optional<Repertory> getById(String id) {
        return repository.findById(id);
    }

    public Repertory save(Repertory repertory) {
        return repository.save(repertory);
    }

    public boolean addPerformance(String repId, String perId) {
        Optional<Repertory> repOpt = repository.findById(repId);
        Optional<Performance> perOpt = performanceRepository.findById(perId);
        if (repOpt.isPresent() && perOpt.isPresent()) {
            Repertory rep = repOpt.get();
            rep.getPerformances().add(perOpt.get());
            repository.save(rep);
            return true;
        }
        return false;
    }

    public boolean removePerformance(String repId, String perId) {
        Optional<Repertory> repOpt = repository.findById(repId);
        Optional<Performance> perOpt = performanceRepository.findById(perId);
        if (repOpt.isPresent() && perOpt.isPresent()) {
            Repertory rep = repOpt.get();
            boolean removed = rep.getPerformances().remove(perOpt.get());
            if (removed) {
                repository.save(rep);
            }
            return removed;
        }
        return false;
    }
}

