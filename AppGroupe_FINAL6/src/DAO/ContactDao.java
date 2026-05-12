package DAO;

import java.sql.*;
import java.util.*;

public class ContactDao {

    public void addContact(String owner, String contact) {

        String check = "SELECT * FROM contacts WHERE owner=? AND contact=?";
        String insert = "INSERT INTO contacts(owner, contact) VALUES(?, ?)";

        try (Connection con = DBConnection.getConnection()) {

            PreparedStatement psCheck = con.prepareStatement(check);
            psCheck.setString(1, owner);
            psCheck.setString(2, contact);

            ResultSet rs = psCheck.executeQuery();

            if (!rs.next()) { // 🔥 غير إلا ما كاينش

                PreparedStatement psInsert = con.prepareStatement(insert);
                psInsert.setString(1, owner);
                psInsert.setString(2, contact);
                psInsert.executeUpdate();
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    public void deleteContact(String owner, String contact) {

        String sql = "DELETE FROM contacts WHERE owner=? AND contact=?";

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setString(1, owner);
            ps.setString(2, contact);
            ps.executeUpdate();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void blockContact(String blocker, String blocked) {
        String sql = "INSERT IGNORE INTO blocked_contacts(blocker, blocked) VALUES(?, ?)";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, blocker);
            ps.setString(2, blocked);
            ps.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void unblockContact(String blocker, String blocked) {
        String sql = "DELETE FROM blocked_contacts WHERE blocker=? AND blocked=?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, blocker);
            ps.setString(2, blocked);
            ps.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public boolean isBlocked(String blocker, String blocked) {
        String sql = "SELECT 1 FROM blocked_contacts WHERE blocker=? AND blocked=?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, blocker);
            ps.setString(2, blocked);
            return ps.executeQuery().next();
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public List<String> getBlockedList(String blocker) {
        List<String> list = new ArrayList<>();
        String sql = "SELECT blocked FROM blocked_contacts WHERE blocker=?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, blocker);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) list.add(rs.getString("blocked"));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    public List<String> getContacts(String username) {

        List<String> list = new ArrayList<>();

        String sql = "SELECT contact FROM contacts WHERE owner=?";

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setString(1, username);

            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                list.add(rs.getString("contact"));
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return list;
    }
}