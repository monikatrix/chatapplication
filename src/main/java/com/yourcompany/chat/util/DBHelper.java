package com.yourcompany.chat.util;
import java.util.*;
import java.sql.*;
import java.text.SimpleDateFormat;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;

public class DBHelper {

//	 private static final String URL = "jdbc:mysql://10.210.216.238:3306/chatapp";
//	 private static final String USER = "app_user";
//	 private static final String PASSWORD = "moni#123"; 
//
//	    static {
//	        try {
//	            Class.forName("com.mysql.cj.jdbc.Driver");
//	        } catch (ClassNotFoundException e) {
//	            e.printStackTrace();
//	        }
//	    }
//
//	    public static Connection getConnection() throws SQLException {
//	        return DriverManager.getConnection(URL, USER, PASSWORD);
//	    }
	private static DataSource dataSource;
	static {
	       try {
	       	Context initContext = new InitialContext();
	           dataSource = (DataSource) initContext.lookup("java:comp/env/jdbc/MyDB");
	       } catch (NamingException e) {
	           e.printStackTrace();
	       }
	   }

    public static Connection getConnection() throws SQLException {
        return dataSource.getConnection();
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
    
    public static boolean storeMessage(String sender, String senderEmail, String receiver, String receiverEmail,
        Integer groupId, String message, String type) {
		String sql = "INSERT INTO messages(sender_username, sender_email, receiver_username, receiver_email, group_id, message, type, timestamp) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
		try (Connection conn = getConnection(); 
			PreparedStatement ps = conn.prepareStatement(sql)) {
			ps.setString(1, sender);
			ps.setString(2, senderEmail);
			ps.setString(3, receiver);
			ps.setString(4, receiverEmail);
			if (groupId != null)
	            ps.setInt(5, groupId);
	        else
	            ps.setNull(5, java.sql.Types.INTEGER);

			ps.setString(6, message);
			ps.setString(7, type);
			ps.setTimestamp(8, Timestamp.valueOf(java.time.LocalDateTime.now()));
			
			int row = ps.executeUpdate();
			return row==1;
		} catch (SQLException e) {
			e.printStackTrace();
			return false;
		}
    }
    
    public static List<Map<String, Object>> getPrivateChatHistory(String user1, String user2){
    	List<Map<String, Object>> chatList = new ArrayList<>();
    	
    	String sql = "SELECT sender_username, receiver_username, message, timestamp " +
                "FROM messages " +
                "WHERE type = 'private' AND " +
                "((sender_username = ? AND receiver_username = ?) " +
                "OR (sender_username = ? AND receiver_username = ?)) " +
                "ORDER BY timestamp ASC";
    	
    	try(Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)){
    		ps.setString(1, user1);
    		ps.setString(2, user2);
    		ps.setString(3, user2);
    		ps.setString(4, user1);
    		
    		ResultSet rs = ps.executeQuery();
    		
    		while(rs.next()) {
    			Map<String, Object> msg = new HashMap<>();
    			msg.put("sender_username", rs.getString("sender_username"));
    			msg.put("receiver_username", rs.getString("receiver_username"));
    			msg.put("message", rs.getString("message"));
    			Timestamp ts = rs.getTimestamp("timestamp");
    			if (ts != null) {
    			    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    			    msg.put("timestamp", sdf.format(ts));
    			} else {
    			    msg.put("timestamp", null);
    			}

    			chatList.add(msg);

    		}
    	}
    	catch(SQLException e) {
    		e.printStackTrace();
    	}
    	return chatList;
    }
    
    public static List<Map<String, Object>> getGroupChatHistory(String groupName){
    	List<Map<String, Object>> chatList = new ArrayList<>();
    	int groupId = getGroupId(groupName);
    	if(groupId==-1) return chatList;
    	
    	String sql = "SELECT sender_username, message, timestamp " +
                "FROM messages WHERE type = 'group-message' AND group_id = ? " +
                "ORDER BY timestamp ASC";
    	
    	try(Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)){
    		ps.setInt(1, groupId);		
    		ResultSet rs = ps.executeQuery();
    		
    		while(rs.next()) {
    			Map<String, Object> msg = new HashMap<>();
    			msg.put("sender_username", rs.getString("sender_username"));
    			msg.put("message", rs.getString("message"));
    			Timestamp ts = rs.getTimestamp("timestamp");
    			if (ts != null) {
    			    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    			    msg.put("timestamp", sdf.format(ts));
    			} else {
    			    msg.put("timestamp", null);
    			}

    			chatList.add(msg);
    		}
    	}
    	catch(SQLException e) {
    		e.printStackTrace();
    	}
    	return chatList;
    }
    
    public static boolean createGroup(String groupName, String createdBy) {
    	String sql = "INSERT INTO chat_groups(group_name, created_by, admin_username) VALUES (?,?,?)";
    	try(Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)){
    		ps.setString(1, groupName);
    		ps.setString(2, createdBy);
    		ps.setString(3, createdBy);
    		ps.executeUpdate();
    		return true;
    	}
    	catch(SQLException e) {
    		e.printStackTrace();
    		return false;
    	}
    }
    
    public static int getGroupId(String groupName) {
    	String sql = "SELECT id FROM chat_groups WHERE group_name=?";
    	try(Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)){
    		ps.setString(1, groupName);
    		ResultSet rs = ps.executeQuery();
    		if(rs.next()) return rs.getInt("id");
    	}
    	catch(SQLException e) {
    		e.printStackTrace();
    	}
    	return -1;
    }
    
    public static List<String> getGroupMembers(int groupId){
    	List<String> members = new ArrayList<String>();
    	String sql = "SELECT username FROM group_members WHERE group_id=?";
    	try(Connection conn = getConnection();PreparedStatement ps = conn.prepareStatement(sql)){
    		ps.setInt(1, groupId);
    		ResultSet rs = ps.executeQuery();
    		while(rs.next()) members.add(rs.getString("username"));
    	}
    	catch(SQLException e) {
    		e.printStackTrace();
    	}
    	return members;
    }
    
    public static List<String> getAllGroups() {
        List<String> groups = new ArrayList<>();
        String sql = "SELECT group_name FROM chat_groups ORDER BY group_name";
        try (Connection conn = getConnection(); Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) groups.add(rs.getString("group_name"));
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return groups;
    }

    public static List<String> getUserGroups(String username){
    	List<String> groups = new ArrayList<String>();
    	String sql = "SELECT g.group_name FROM chat_groups g JOIN group_members m ON g.id=m.group_id WHERE m.username=?";
    	try(Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)){
    		ps.setString(1, username);
    		ResultSet rs = ps.executeQuery();
    		while(rs.next()) groups.add(rs.getString("group_name"));
    	}catch(SQLException e) {
    		e.printStackTrace();
    	}
    	return groups;
    }
    
    public static void updateUserStatus(String username, boolean isOnline) {
        String sql = isOnline
            ? "UPDATE users SET is_online = ? WHERE username = ?"
            : "UPDATE users SET is_online = ?, last_seen = ? WHERE username = ?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setBoolean(1, isOnline);
            if (isOnline) {
                ps.setString(2, username);
            } else {
                ps.setTimestamp(2, new Timestamp(System.currentTimeMillis()));
                ps.setString(3, username);
            }
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    
    public static String getLastSeen(String username) {
        String sql = "SELECT is_online, last_seen FROM users WHERE username=?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                if (rs.getBoolean("is_online")) return "Online";
                Timestamp ts = rs.getTimestamp("last_seen");
                if (ts != null) {
                    SimpleDateFormat sdf = new SimpleDateFormat("MMM dd 'at' hh:mm a");
                    return "Last seen " + sdf.format(ts);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return "Offline";
    }


    public static List<Map<String, Object>> getAllUsersWithStatus() {
        List<Map<String, Object>> users = new ArrayList<>();
        String sql = "SELECT username, is_online, last_seen FROM users ORDER BY username";
        try (Connection conn = getConnection(); Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                Map<String, Object> user = new HashMap<>();
                user.put("username", rs.getString("username"));
                user.put("isOnline", rs.getBoolean("is_online"));
                Timestamp ts = rs.getTimestamp("last_seen");
                if (ts != null) {
                    SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy 'at' hh:mm a");
                    user.put("lastSeen", sdf.format(ts));
                } else {
                    user.put("lastSeen", "N/A");
                }
                users.add(user);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return users;
    }
    
    public static boolean addGroupMember(int groupId, String username, String addedBy) {
    	if(!isGroupAdmin(groupId, addedBy)) return false;
    	String sql = "INSERT INTO group_members(group_id, username) VALUES(?, ?)";
    	try(Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)){
    		ps.setInt(1,groupId);
    		ps.setString(2, username);
    		ps.executeUpdate();
    		return true;
    	} catch(SQLException e) {
    		e.printStackTrace();
    		return false;
    	}
    }
    
    public static boolean removeGroupMember(int groupId, String username, String removedBy) {
    	if(!isGroupAdmin(groupId, removedBy)) return false;
    	String sql = "DELETE FROM group_members WHERE group_id=? AND username=?";
    	try(Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)){
    		ps.setInt(1,groupId);
    		ps.setString(2, username);
    		ps.executeUpdate();
    		return true;
    	} catch(SQLException e) {
    		e.printStackTrace();
    		return false;
    	}
    }
    
    public static boolean isGroupAdmin(int groupId, String username) {
    	String sql = "SELECT 1 FROM chat_groups WHERE id=? AND admin_username=?";
    	try(Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)){
    		ps.setInt(1, groupId);
    		ps.setString(2, username);
    		ResultSet rs = ps.executeQuery();
    		return rs.next();
    	} catch(SQLException e) {
    		e.printStackTrace();
    		return false;
    	}
    }
    
    public static boolean deletePrivateMessage(String user1, String user2) {
        String sql = "DELETE FROM messages WHERE type='private' AND " +
                     "((sender_username=? AND receiver_username=?) OR (sender_username=? AND receiver_username=?))";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, user1);
            ps.setString(2, user2);
            ps.setString(3, user2);
            ps.setString(4, user1);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }
    
    public static boolean deleteSpecificMessage(int messageId, String requestedBy) {
        String sql = """
            DELETE FROM messages
            WHERE id = ?
            AND (
                sender_username = ?
                OR group_id IN (
                    SELECT id FROM chat_groups WHERE admin_username = ?
                )
            )
            """;
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, messageId);
            ps.setString(2, requestedBy);
            ps.setString(3, requestedBy);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static boolean deleteAllGroupMessages(int groupId, String requestedBy) {
        if (!isGroupAdmin(groupId, requestedBy)) return false;
        String sql = "DELETE FROM messages WHERE group_id = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, groupId);
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static boolean deletePrivateChatHistory(String user1, String user2) {
        String sql = """
            DELETE FROM messages
            WHERE type = 'private' AND (
                (sender_username = ? AND receiver_username = ?)
                OR (sender_username = ? AND receiver_username = ?)
            )
            """;
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, user1);
            ps.setString(2, user2);
            ps.setString(3, user2);
            ps.setString(4, user1);
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static boolean permanentlyRemoveGroupMember(int groupId, String username, String removedBy) {
        if (!isGroupAdmin(groupId, removedBy)) return false;
        String sql = "DELETE FROM group_members WHERE group_id=? AND username=?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, groupId);
            ps.setString(2, username);
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static boolean deleteGroupCompletely(int groupId, String requestedBy) {
        if (!isGroupAdmin(groupId, requestedBy)) return false;

        String deleteMessages = "DELETE FROM messages WHERE group_id=?";
        String deleteMembers = "DELETE FROM group_members WHERE group_id=?";
        String deleteGroup = "DELETE FROM chat_groups WHERE id=?";

        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement ps1 = conn.prepareStatement(deleteMessages);
                 PreparedStatement ps2 = conn.prepareStatement(deleteMembers);
                 PreparedStatement ps3 = conn.prepareStatement(deleteGroup)) {

                ps1.setInt(1, groupId);
                ps1.executeUpdate();

                ps2.setInt(1, groupId);
                ps2.executeUpdate();

                ps3.setInt(1, groupId);
                ps3.executeUpdate();

                conn.commit();
                return true;
            } catch (SQLException e) {
                conn.rollback();
                e.printStackTrace();
                return false;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }


}

