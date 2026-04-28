package network;

public class Protocol {

    // 🔐 AUTH / USERS
    public static final String LOGIN = "LOGIN";
    public static final String USERS = "USERS";
    public static final String HISTORY = "HISTORY";

    // 💬 MESSAGES
    public static final String MSG = "MSG";
    public static final String SEEN = "SEEN";

    // 📞 CALL SYSTEM
    public static final String CALL_REQUEST = "CALL_REQUEST";
    public static final String CALL_ACCEPT = "CALL_ACCEPT";
    public static final String CALL_REJECT = "CALL_REJECT";
    public static final String CALL_END = "CALL_END";

    // optional future upgrade
    public static final String CALL_BUSY = "CALL_BUSY";
}