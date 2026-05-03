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

    public List<User> getAllUsers() {

        List<User> list = new ArrayList<>();
        String sql = "SELECT username FROM users";

        try (Connection con = DBConnection.getConnection();
             Statement st = con.createStatement();
             ResultSet rs = st.executeQuery(sql)) {

            while (rs.next()) {
                list.add(new User(rs.getString("username")));
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return list;
    }
}