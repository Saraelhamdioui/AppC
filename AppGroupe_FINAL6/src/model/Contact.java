package model;

public class Contact {

    private String username;
    private int unread;

    public Contact(String username) {
        this.username = username;
        this.unread = 0;
    }

    public String getUsername() {
        return username;
    }

    public int getUnread() {
        return unread;
    }

    public void setUnread(int unread) {
        this.unread = unread;
    }

    @Override
    public String toString() {
        if (unread > 0)
            return "● " + username + " (" + unread + ")";
        else
            return "● " + username;
    }
}