//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class CallDao {
    public CallDao() {
    }

    public int startCall(String caller, String callee, String type) {
        String sql = "INSERT INTO calls (caller, callee, type, status, start_time) VALUES (?, ?, ?, 'ongoing', NOW())";

        try {
            int var8;
            try (Connection con = DBConnection.getConnection()) {
                try (PreparedStatement ps = con.prepareStatement(sql, 1)) {
                    ps.setString(1, caller);
                    ps.setString(2, callee);
                    ps.setString(3, type);
                    ps.executeUpdate();
                    ResultSet rs = ps.getGeneratedKeys();
                    if (!rs.next()) {
                        return -1;
                    }

                    var8 = rs.getInt(1);
                }
            }

            return var8;
        } catch (Exception e) {
            e.printStackTrace();
            return -1;
        }
    }

    public void endCall(int callId) {
        String sql = "UPDATE calls SET status='ended', duration = TIMESTAMPDIFF(SECOND, start_time, NOW()), end_time=NOW() WHERE id=?";

        try (
                Connection con = DBConnection.getConnection();
                PreparedStatement ps = con.prepareStatement(sql);
        ) {
            ps.setInt(1, callId);
            ps.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }

    }
}
