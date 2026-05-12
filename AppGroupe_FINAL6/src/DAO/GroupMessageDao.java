package DAO;



import model.GroupMessage;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class GroupMessageDao {

    private Connection getConnection() throws SQLException {
        try {
            return DBConnection.getConnection();
        } catch (Exception e) {
            throw new SQLException(e);
        }
    }

    public int saveMessage(GroupMessage message) {
        String sql = "INSERT INTO group_messages (group_id, sender, content, timestamp, seen, type, file_path) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            pstmt.setInt(1, message.getGroupId());
            pstmt.setString(2, message.getSender());
            pstmt.setString(3, message.getContent());
            pstmt.setString(4, message.getTimestamp().toString());
            pstmt.setBoolean(5, message.isSeen());
            pstmt.setString(6, message.getType());
            pstmt.setString(7, message.getFilePath());
            pstmt.executeUpdate();

            ResultSet rs = pstmt.getGeneratedKeys();
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return -1;
    }

    public List<GroupMessage> getGroupMessages(int groupId, int limit) {
        List<GroupMessage> messages = new ArrayList<>();
        String sql = "SELECT * FROM group_messages WHERE group_id = ? " +
                "ORDER BY id ASC LIMIT ?";

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, groupId);
            pstmt.setInt(2, limit);
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                GroupMessage msg = new GroupMessage();
                msg.setId(rs.getInt("id"));
                msg.setGroupId(rs.getInt("group_id"));
                msg.setSender(rs.getString("sender"));
                msg.setContent(rs.getString("content"));
                msg.setSeen(rs.getBoolean("seen"));
                msg.setType(rs.getString("type"));
                msg.setFilePath(rs.getString("file_path"));
                messages.add(msg);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return messages;
    }

    public void markAsSeen(int groupId, String username) {
        String sql = "UPDATE group_messages SET seen = true WHERE group_id = ? AND sender != ?";

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, groupId);
            pstmt.setString(2, username);
            pstmt.executeUpdate();

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}