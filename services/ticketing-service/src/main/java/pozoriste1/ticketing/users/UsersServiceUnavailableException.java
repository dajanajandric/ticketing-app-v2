package pozoriste1.ticketing.users;

public class UsersServiceUnavailableException extends RuntimeException {
    public UsersServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
