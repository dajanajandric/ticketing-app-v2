package pozoriste1.users.web;

/** Regularni izrazi za provjeru ulaza, zajednicki za sve entitete servisa. */
public final class InputRules {

    /** Bez kontrolnih znakova (kodovi 0-31 i 127) - Postgres npr. odbija NUL znak u tekstu. */
    public static final String NO_CONTROL_CHARS = "^[^\\p{Cntrl}]*$";

    /** ID-evi u sistemu su oblika "blag-01", "pr-05", "260926-1900". */
    public static final String SAFE_ID = "^[A-Za-z0-9._-]{1,50}$";

    /** JMBG: tacno 13 cifara. */
    public static final String JMBG = "^\\d{13}$";

    private InputRules() {
    }
}
