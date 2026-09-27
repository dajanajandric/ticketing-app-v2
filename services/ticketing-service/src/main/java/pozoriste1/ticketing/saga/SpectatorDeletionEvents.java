package pozoriste1.ticketing.saga;

/** Dogadjaji sage brisanja gledaoca. Ista struktura postoji i u users-service. */
public final class SpectatorDeletionEvents {

    public record Requested(String sagaId, String jmbg) {
    }

    public record Approved(String sagaId, String jmbg, int anonymizedTickets) {
    }

    public record Rejected(String sagaId, String jmbg, int futureTickets, String reason) {
    }

    private SpectatorDeletionEvents() {
    }
}
