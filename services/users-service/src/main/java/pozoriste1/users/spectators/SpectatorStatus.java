package pozoriste1.users.spectators;

public enum SpectatorStatus {
    /** Normalno stanje. */
    ACTIVE,
    /** Saga brisanja je u toku - ticketing-service provjerava karte gledaoca. */
    DELETION_PENDING
}
