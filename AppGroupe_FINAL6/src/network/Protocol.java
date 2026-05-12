package network;

public class Protocol {


    public static final String LOGIN = "LOGIN";
    public static final String USERS = "USERS";
    public static final String HISTORY = "HISTORY";

    public static final String ADD_CONTACT    = "ADD_CONTACT";
    public static final String DELETE_CONTACT = "DELETE_CONTACT";
    public static final String BLOCK_CONTACT  = "BLOCK_CONTACT";
    public static final String UNBLOCK_CONTACT = "UNBLOCK_CONTACT";
    public static final String CONTACTS       = "CONTACTS";


    public static final String MSG = "MSG";
    public static final String SEEN = "SEEN";


    public static final String CALL_REQUEST = "CALL_REQUEST";
    public static final String CALL_ACCEPT = "CALL_ACCEPT";
    public static final String CALL_REJECT = "CALL_REJECT";
    public static final String CALL_END = "CALL_END";


    public static final String CALL_BUSY = "CALL_BUSY";


//******************PARTIE AJOUTER

    // NOUVEAUX - Groupes
    public static final String GROUP_CREATE = "GROUP_CREATE";
    public static final String GROUP_LIST = "GROUP_LIST";
    public static final String GROUP_MSG = "GROUP_MSG";
    public static final String GROUP_HISTORY = "GROUP_HISTORY";
    public static final String GROUP_ADD_MEMBER = "GROUP_ADD_MEMBER";
    public static final String GROUP_REMOVE_MEMBER = "GROUP_REMOVE_MEMBER";
    public static final String GROUP_JOIN = "GROUP_JOIN";
    public static final String GROUP_LEAVE = "GROUP_LEAVE";
    public static final String GROUP_DELETE = "GROUP_DELETE";
    public static final String GROUP_MEMBERS = "GROUP_MEMBERS";
    public static final String GROUP_SEEN = "GROUP_SEEN";

    public static final String GROUP_CALL_START  = "GROUP_CALL_START";
    public static final String GROUP_CALL_JOIN   = "GROUP_CALL_JOIN";
    public static final String GROUP_CALL_LEAVE  = "GROUP_CALL_LEAVE";
    public static final String GROUP_CALL_NOTIFY = "GROUP_CALL_NOTIFY";

}