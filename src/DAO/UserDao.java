package DAO;

import model.User;
import java.sql.*;
import java.util.*;

public class UserDao {

    public void save(User user) {
        String sql = "INSERT IGNORE INTO users(username) VALUES(?)";

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setString(1, user.getUsername());
            ps.executeUpdate();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}