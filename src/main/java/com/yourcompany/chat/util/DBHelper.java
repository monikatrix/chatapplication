package com.yourcompany.chat.util;
import java.sql.*;

public class DBHelper {
	
	private static final String URL = "jdbc:mysql://<host_IP>/chatapp?useSSL=false&serverTimezone=Asia/Kolkata";
	private static final String USER = "<db_name>";
	private static final String PASS = "<db_password>";

    static {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver"); 
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASS);
    }

    public static void storeMessage(String sender, String senderEmail, String receiver, String receiverEmail,
                                    String group, String message, String type) {
        String sql = "INSERT INTO messages(sender_username, sender_email, receiver_username, receiver_email, group_name, message, type, timestamp) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = getConnection(); 
        	PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, sender);
            ps.setString(2, senderEmail);
            ps.setString(3, receiver);
            ps.setString(4, receiverEmail);
            ps.setString(5, group);
            ps.setString(6, message);
            ps.setString(7, type);
            ps.setTimestamp(8, new java.sql.Timestamp(System.currentTimeMillis()));
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static boolean addUser(String username, String email, String password) {
        String sql = "INSERT INTO users(username, email, password) VALUES(?,?,?)";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            ps.setString(2, email);
            ps.setString(3, password);
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            return false;
        }
    }

    public static boolean validateUser(String email, String password) {
        String sql = "SELECT * FROM users WHERE email=? AND password=?";
        try (Connection conn = getConnection(); 
        	PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, email);
            ps.setString(2, password);
            ResultSet rs = ps.executeQuery();
            return rs.next();
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static String getUsername(String email) {
        String sql = "SELECT username FROM users WHERE email=?";
        try (Connection conn = getConnection(); 
        	PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, email);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getString("username");
        } catch (SQLException e) { e.printStackTrace(); }
        return null;
    }
    public static String getEmail(String username) {
        String sql = "SELECT email FROM users WHERE username=?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getString("email");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null; 
    }
}
