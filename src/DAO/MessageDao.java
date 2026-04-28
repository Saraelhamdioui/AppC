package DAO;

import model.Message;
import java.sql.*;
import java.util.*;

public class MessageDao {

    public void save(Message m) {

        String sql = "INSERT INTO messages(sender, receiver, content, seen) VALUES (?, ?, ?, ?)";

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setString(1, m.getSender());
            ps.setString(2, m.getReceiver());
            ps.setString(3, m.getContent());
            ps.setBoolean(4, m.isSeen());

            ps.executeUpdate();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public List<Message> getMessages(String username) {

        List<Message> list = new ArrayList<>();

        String sql = "SELECT * FROM messages WHERE sender=? OR receiver=?";

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setString(1, username);
            ps.setString(2, username);

            ResultSet rs = ps.executeQuery();

            while (rs.next()) {

                Message m = new Message(
                        rs.getString("sender"),
                        rs.getString("receiver"),
                        rs.getString("content")
                );

                m.setSeen(rs.getBoolean("seen"));
                list.add(m);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return list;
    }

    public void markAsSeen(String sender, String receiver) {

        String sql = "UPDATE messages SET seen=true WHERE sender=? AND receiver=?";

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setString(1, sender);
            ps.setString(2, receiver);
            ps.executeUpdate();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}