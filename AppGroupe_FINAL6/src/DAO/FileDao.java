package DAO;

import java.sql.Connection;
import java.sql.PreparedStatement;

public class FileDao {

    public void save(String sender, String receiver, String fileName, String filePath) {
        String sql = "INSERT INTO files(sender, receiver, file_name, file_path) VALUES (?, ?, ?, ?)";

        try (
                Connection con = DBConnection.getConnection();
                PreparedStatement ps = con.prepareStatement(sql)
        ) {
            ps.setString(1, sender);
            ps.setString(2, receiver);
            ps.setString(3, fileName);
            ps.setString(4, filePath);

            ps.executeUpdate();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}