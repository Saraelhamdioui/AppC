package DAO;

import model.Group;
import model.GroupMember;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class GroupDao {

    private Connection getConnection() throws SQLException {
        try {
            return DBConnection.getConnection();
        } catch (Exception e) {
            throw new SQLException(e);
        }
    }

    // *** AJOUT : crée les tables si elles n'existent pas ***
    public void initTables() {
        String sql1 = "CREATE TABLE IF NOT EXISTS `groups` (" +
                "id INT AUTO_INCREMENT PRIMARY KEY," +
                "name VARCHAR(100) NOT NULL," +
                "created_by VARCHAR(50) NOT NULL," +
                "created_at VARCHAR(50) NOT NULL)";

        String sql2 = "CREATE TABLE IF NOT EXISTS `group_members` (" +
                "id INT AUTO_INCREMENT PRIMARY KEY," +
                "group_id INT NOT NULL," +
                "username VARCHAR(50) NOT NULL," +
                "role VARCHAR(20) DEFAULT 'MEMBER'," +
                "joined_at VARCHAR(50))";

        String sql3 = "CREATE TABLE IF NOT EXISTS `group_messages` (" +
                "id INT AUTO_INCREMENT PRIMARY KEY," +
                "group_id INT NOT NULL," +
                "sender VARCHAR(50) NOT NULL," +
                "content TEXT," +
                "timestamp VARCHAR(50)," +
                "seen BOOLEAN DEFAULT FALSE," +
                "type VARCHAR(20) DEFAULT 'TEXT'," +
                "file_path VARCHAR(255))";

        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(sql1);
            stmt.executeUpdate(sql2);
            stmt.executeUpdate(sql3);
            System.out.println("[BDD] Tables groupes verifiees/creees.");
        } catch (SQLException e) {
            System.out.println("[BDD] Erreur creation tables: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public int createGroup(Group group) {
        String sql = "INSERT INTO `groups` (name, created_by, created_at) VALUES (?, ?, ?)";

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            pstmt.setString(1, group.getName());
            pstmt.setString(2, group.getCreatedBy());
            // Securiser : utiliser now() si createdAt est null
            String createdAt = (group.getCreatedAt() != null)
                    ? group.getCreatedAt().toString()
                    : java.time.LocalDateTime.now().toString();
            pstmt.setString(3, createdAt);
            pstmt.executeUpdate();

            ResultSet rs = pstmt.getGeneratedKeys();
            if (rs.next()) {
                int groupId = rs.getInt(1);
                // Ajouter le createur comme ADMIN
                addMember(groupId, group.getCreatedBy(), "ADMIN");
                return groupId;
            } else {
                System.out.println("[BDD] createGroup: INSERT OK mais aucun ID retourne");
            }
        } catch (SQLException e) {
            System.out.println("[BDD] ERREUR createGroup: SQLState=" + e.getSQLState()
                    + " | Code=" + e.getErrorCode()
                    + " | Message=" + e.getMessage());
            e.printStackTrace();
        }
        return -1;
    }

    public void addMember(int groupId, String username, String role) {
        // Vérifier d'abord si déjà membre pour éviter les doublons
        if (isMember(groupId, username)) return;

        String sql = "INSERT INTO `group_members` (group_id, username, role, joined_at) VALUES (?, ?, ?, ?)";

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, groupId);
            pstmt.setString(2, username);
            pstmt.setString(3, role);
            pstmt.setString(4, java.time.LocalDateTime.now().toString());
            pstmt.executeUpdate();

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void removeMember(int groupId, String username) {
        String sql = "DELETE FROM `group_members` WHERE group_id = ? AND username = ?";

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, groupId);
            pstmt.setString(2, username);
            pstmt.executeUpdate();

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public List<Group> getUserGroups(String username) {
        List<Group> groups = new ArrayList<>();
        String sql = "SELECT g.* FROM `groups` g " +
                "JOIN `group_members` gm ON g.id = gm.group_id " +
                "WHERE gm.username = ? ORDER BY g.id ASC";

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, username);
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                Group group = new Group();
                group.setId(rs.getInt("id"));
                group.setName(rs.getString("name"));
                group.setCreatedBy(rs.getString("created_by"));
                group.setMembers(getGroupMembers(rs.getInt("id")));
                groups.add(group);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return groups;
    }

    public List<GroupMember> getGroupMembers(int groupId) {
        List<GroupMember> members = new ArrayList<>();
        String sql = "SELECT * FROM `group_members` WHERE group_id = ?";

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, groupId);
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                GroupMember member = new GroupMember();
                member.setGroupId(rs.getInt("group_id"));
                member.setUsername(rs.getString("username"));
                member.setRole(rs.getString("role"));
                members.add(member);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return members;
    }

    public boolean isMember(int groupId, String username) {
        String sql = "SELECT 1 FROM `group_members` WHERE group_id = ? AND username = ?";

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, groupId);
            pstmt.setString(2, username);
            return pstmt.executeQuery().next();

        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    public List<Group> getAllGroups() {
        List<Group> groups = new ArrayList<>();
        String sql = "SELECT * FROM `groups`";

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                Group group = new Group();
                group.setId(rs.getInt("id"));
                group.setName(rs.getString("name"));
                group.setCreatedBy(rs.getString("created_by"));
                group.setMembers(getGroupMembers(rs.getInt("id")));
                groups.add(group);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return groups;
    }

    public void deleteGroup(int groupId) {
        String sql1 = "DELETE FROM `group_messages` WHERE group_id = ?";
        String sql2 = "DELETE FROM `group_members` WHERE group_id = ?";
        String sql3 = "DELETE FROM `groups` WHERE id = ?";

        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);

            try (PreparedStatement p1 = conn.prepareStatement(sql1)) {
                p1.setInt(1, groupId); p1.executeUpdate();
            }
            try (PreparedStatement p2 = conn.prepareStatement(sql2)) {
                p2.setInt(1, groupId); p2.executeUpdate();
            }
            try (PreparedStatement p3 = conn.prepareStatement(sql3)) {
                p3.setInt(1, groupId); p3.executeUpdate();
            }

            conn.commit();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}