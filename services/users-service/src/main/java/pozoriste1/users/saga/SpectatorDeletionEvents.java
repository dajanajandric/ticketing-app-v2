package pozoriste1.users.saga;

/** Dogadjaji sage brisanja gledaoca. Ista struktura postoji i u ticketing-service. */
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
