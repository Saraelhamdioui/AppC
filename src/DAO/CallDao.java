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
                return rs.getInt(1);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return -1;
    }

    // =========================
    // ⏹ END CALL
    // =========================
    public void endCall(int callId) {

        String sql =
                "UPDATE calls SET " +
                        "status='ended', " +
                        "end_time=NOW(), " +
                        "duration = TIMESTAMPDIFF(SECOND, start_time, end_time) " +
                        "WHERE id=?";

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setInt(1, callId);
            ps.executeUpdate();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    public int getActiveCallId(String u1, String u2) {

        String sql = "SELECT id FROM calls WHERE " +
                "((caller=? AND callee=?) OR (caller=? AND callee=?)) " +
                "AND status='ongoing' ORDER BY id DESC LIMIT 1";

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setString(1, u1);
            ps.setString(2, u2);
            ps.setString(3, u2);
            ps.setString(4, u1);

            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                return rs.getInt("id");
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return -1;
    }
}
