package DAO;

import java.sql.*;

public class CallDao {

    public int startCall(String caller, String callee, String type) {

        String sql = "INSERT INTO calls (caller, callee, type, status, start_time) " +
                "VALUES (?, ?, ?, 'ongoing', NOW())";

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setString(1, caller);
            ps.setString(2, callee);
            ps.setString(3, type);

            ps.executeUpdate();

            ResultSet rs = ps.getGeneratedKeys();

            if (rs.next()) {
                return rs.getInt(1); // 🔥 call_id
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return -1;
    }


    public void endCall(int callId) {

        String sql =
                "UPDATE calls SET " +
                        "status='ended', " +
                        "duration = TIMESTAMPDIFF(SECOND, start_time, NOW()), " +
                        "end_time=NOW() " +
                        "WHERE id=?";

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setInt(1, callId);
            ps.executeUpdate();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}