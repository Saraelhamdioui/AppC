package DAO;

import model.Message;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class MessageDao {

    public int save(Message m) {
        String sql = "INSERT INTO messages(sender, receiver, content, seen) VALUES (?, ?, ?, ?)";

        try (
                Connection con = DBConnection.getConnection();
                PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)
        ) {
            ps.setString(1, m.getSender());
            ps.setString(2, m.getReceiver());
            ps.setString(3, m.getContent());
            ps.setBoolean(4, m.isSeen());

            ps.executeUpdate();

            ResultSet keys = ps.getGeneratedKeys();
            if (keys.next()) {
                int id = keys.getInt(1);
                System.out.println("[MessageDao] Message sauvegardé : id=" + id
                        + " | " + m.getSender() + " -> " + m.getReceiver()
                        + " | \"" + m.getContent() + "\"");
                return id;
            }

        } catch (Exception e) {
            System.err.println("[MessageDao] ERREUR save() : " + e.getMessage());
            e.printStackTrace();
        }

        return -1;
    }

    public List<Message> getConversation(String user1, String user2) {
        List<Message> list = new ArrayList<>();

        String sql =
                "SELECT * FROM messages " +
                        "WHERE (sender=? AND receiver=?) " +
                        "OR (sender=? AND receiver=?) " +
                        "ORDER BY created_at ASC";

        try (
                Connection con = DBConnection.getConnection();
                PreparedStatement ps = con.prepareStatement(sql)
        ) {
            ps.setString(1, user1);
            ps.setString(2, user2);
            ps.setString(3, user2);
            ps.setString(4, user1);

            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                list.add(mapMessage(rs));
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return list;
    }

    public List<Message> getMessages(String username) {
        List<Message> list = new ArrayList<>();

        String sql = "SELECT * FROM messages WHERE sender=? OR receiver=? ORDER BY created_at ASC";

        try (
                Connection con = DBConnection.getConnection();
                PreparedStatement ps = con.prepareStatement(sql)
        ) {
            System.out.println("[getMessages] Connexion BDD OK, recherche pour username='" + username + "'");
            ps.setString(1, username);
            ps.setString(2, username);

            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                Message m = mapMessage(rs);
                System.out.println("[getMessages] Trouvé: id=" + m.getId() + " | " + m.getSender() + " -> " + m.getReceiver() + " | " + m.getContent());
                list.add(m);
            }
            System.out.println("[getMessages] Total: " + list.size() + " messages pour " + username);

        } catch (Exception e) {
            System.err.println("[getMessages] ERREUR: " + e.getMessage());
            e.printStackTrace();
        }

        return list;
    }

    public void markAsSeen(int messageId) {
        String sql = "UPDATE messages SET seen=true WHERE id=?";

        try (
                Connection con = DBConnection.getConnection();
                PreparedStatement ps = con.prepareStatement(sql)
        ) {
            ps.setInt(1, messageId);
            ps.executeUpdate();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void markConversationAsSeen(String sender, String receiver) {
        String sql = "UPDATE messages SET seen=true WHERE sender=? AND receiver=?";

        try (
                Connection con = DBConnection.getConnection();
                PreparedStatement ps = con.prepareStatement(sql)
        ) {
            ps.setString(1, sender);
            ps.setString(2, receiver);
            ps.executeUpdate();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private Message mapMessage(ResultSet rs) throws SQLException {
        Timestamp ts = rs.getTimestamp("created_at");

        LocalDateTime createdAt = ts != null
                ? ts.toLocalDateTime()
                : LocalDateTime.now();

        return new Message(
                rs.getInt("id"),
                rs.getString("sender"),
                rs.getString("receiver"),
                rs.getString("content"),
                rs.getBoolean("seen"),
                "TEXT",
                createdAt
        );
    }
}