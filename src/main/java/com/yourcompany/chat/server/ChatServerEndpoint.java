package com.yourcompany.chat.server;
import javax.websocket.*;
import javax.websocket.server.PathParam;
import javax.websocket.server.ServerEndpoint;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.*;
import com.google.gson.Gson;
import com.yourcompany.chat.servlet.SignIn;
import com.yourcompany.chat.util.DBHelper;

@ServerEndpoint(value = "/chat/{username}", configurator = CustomConfigurator.class)
public class ChatServerEndpoint {

	private static final Logger logger = LoggerFactory.getLogger(ChatServerEndpoint.class);

    private static Set<Session> sessions = Collections.synchronizedSet(new HashSet<>());
    private static Map<String, Set<Session>> userSessions = new ConcurrentHashMap<>();
    private static Gson gson = new Gson();
    @OnOpen
    public void onOpen(Session session, EndpointConfig config, @PathParam("username") String username) throws IOException {
    	Map<String,List<String>> headers = (Map<String,List<String>>)config.getUserProperties().get("headers");
    	
    	if(!validateSession(headers, username)) {
    		session.close(new CloseReason(CloseReason.CloseCodes.VIOLATED_POLICY, "Invalid session"));
    		return;
    	}
    	boolean isNewJoin = !userSessions.containsKey(username);
    	sessions.add(session);
        userSessions.computeIfAbsent(username, k->ConcurrentHashMap.newKeySet()).add(session);
        
        DBHelper.updateUserStatus(username, true);
        broadcastUserStatus(username, "online");
        
        sendSystem(session, "Welcome " + username + "!");
        if(isNewJoin) {
        	broadcastSystem(username + " joined the chat.");
        }
        sendOnlineUsersList();
        sendAllUsersList();
        sendGroupList(username);
        broadcastUserStatus(username, "online");
        logger.info("User '{}' connected with new session", username);
    }

    @OnMessage
    public void onMessage(String message, Session session, @PathParam("username") String username) throws IOException {
    	
        try {
            Map<String, Object> msgObj = gson.fromJson(message, Map.class);
            String type = (String) msgObj.get("type");
            String content = (String) msgObj.get("content");

            switch (type.toLowerCase()) {
                case "broadcast":
                    broadcast(username, content);
                    break;

                case "private":
                    String toUser = (String) msgObj.get("to");
                    sendPrivate(username, toUser, content);
                    break;
         
                case "create-group":
                	String groupName = (String) msgObj.get("groupName");
                	List<String> members = (List<String>) msgObj.get("members");
                	createGroup(username, groupName, members);
                	break;
                	
                case "group-message":
                	String targetGroup = (String) msgObj.get("groupName");
                	sendGroupMessage(username,targetGroup,content);
                	break;
                case "list-groups":
                    sendGroupList(username);
                    break;
                
                case "add-group-member":
                	handleAddGroupMember(username, (String) msgObj.get("groupName"), (String) msgObj.get("member"));
                	break;
                	
                case "remove-group-member":
                    handleRemoveGroupMember(username, (String) msgObj.get("groupName"), (String) msgObj.get("member"));
                    break;

                case "delete-group-message":
                	Object idObj1 = msgObj.get("messageId");
                	if(idObj1 == null) {
                		System.out.println("ERROR: messageId not received from client");
                		return;
                	}
                	int messageId1 = ((Double) idObj1).intValue();
                    handleDeleteGroupMessage(username, messageId1, (String) msgObj.get("groupName"));
                    break;

                case "delete-private-message":
                	Object idObj2 = msgObj.get("messageId");
                	if(idObj2 == null) {
                		System.out.println("ERROR: messageId not received from client");
                		return;
                	}
                	int messageId2 = ((Double) idObj2).intValue();
                    handleDeletePrivateMessage(username, messageId2, (String) msgObj.get("to"));
                    break;
                	
                case "get-private-history":
                    String otherUser = (String) msgObj.get("with");
                    List<Map<String, Object>> privateMsgs = DBHelper.getPrivateChatHistory(username, otherUser);
                    sendHistoryToAllSessions(username, privateMsgs, "private-history");
                    break;
                    

                case "get-group-history":
                    String groupName1 = (String) msgObj.get("groupName");
                    List<Map<String, Object>> groupMsgs = DBHelper.getGroupChatHistory(groupName1);
//                    sendHistory(session, groupMsgs, "group-history");
                    sendHistoryToAllSessions(username, groupMsgs, "group-history");
                    break;
                    
                case "get-all-users":
                	sendAllUsersList();
                	break;
                	
                default:
                	sendSystem(session, "Invalid message type");
            }

        } catch (Exception e) {
            logger.error("Error in onMessage: {}",e.getMessage(), e);
            sendSystem(session, "Error: "+e.getMessage());
        }
    }

	@OnClose
    public void onClose(Session session, @PathParam("username") String username) throws IOException {
		sessions.remove(session);
		
		if(username!=null) {
			DBHelper.updateUserStatus(username, false);
			broadcastUserStatus(username, "offline");
		}
		Set<Session> userSet = userSessions.get(username);
		if(userSet!=null) {
			userSet.remove(session);
			if(userSet.isEmpty()) {
				userSessions.remove(username);
			
				broadcastSystem(username + " left the chat.");   
				sendOnlineUsersList();
			}
		}
	    else {
	        logger.info("WebSocket closed for untracked session "+username);
	    }
    }

    @OnError
    public void onError(Session session, Throwable throwable) {
        System.err.println("WebSocket Error: " + throwable.getMessage());
    }
    
    public static void closeUserConnection(String username) {
        Set<Session> userSet = userSessions.get(username);
        if(userSet!=null && !userSet.isEmpty()) {
        	logger.info("Initiating connection close for user '{}' - ",username);
        	userSessions.remove(username); 
           
        	for(Session s:new HashSet<>(userSet)) {
        		sessions.remove(s);
        		try {
                    s.close(new CloseReason(CloseReason.CloseCodes.NORMAL_CLOSURE, "Server logout"));
        		}
        		catch(IOException e) {
        			logger.error("Error closing WebSocket for {}: {}",username, e.getMessage(),e);
        		}
        	}
            
        }
        else {
        	logger.warn("No active Websocket found for '{}'", username);
        }
    }
    
    private void broadcast(String sender, String message) throws IOException {
    	Map<String, String> msg = Map.of("type","broadcast",
    			"sender",sender,
    			"content",message,
    			"timestamp", now());
    	String json = gson.toJson(msg);
    
        for (Session s : sessions) {
            if (s.isOpen()) {
                s.getBasicRemote().sendText(json);
            }
        }
        
        DBHelper.storeMessage(sender, DBHelper.getEmail(sender), "ALL", null, null, message, "broadcast");
     }

    private void sendPrivate(String from, String to, String message) throws IOException {
    	Map<String, String> msg = Map.of(
    			"type","private",
    			"sender",from,
    			"receiver", to,
    			"content",message,
    			"timestamp", now());
    	String json = gson.toJson(msg);
    	
    	Set<Session> recipients = new HashSet<>();
        recipients.addAll(userSessions.getOrDefault(to, Collections.emptySet()));
        recipients.addAll(userSessions.getOrDefault(from, Collections.emptySet()));

        if (recipients.isEmpty()) {
            sendSystem(getAnySession(from), "User '" + to + "' is offline.");
        } else {
            for (Session s : recipients)
                if (s.isOpen()) s.getAsyncRemote().sendText(json);
        }
        DBHelper.storeMessage(from, DBHelper.getEmail(from), to, DBHelper.getEmail(to), null, message, "private");
    }
    
    private void createGroup(String admin, String groupName, List<String> members) throws IOException{
    	if(groupName==null || groupName.trim().isEmpty()) {
    		sendSystem(getAnySession(admin),"Group name cannot be empty!");
    		return;
    	}
    	
    	boolean created = DBHelper.createGroup(groupName, admin);
    	if(!created) {
    		sendSystem(getAnySession(admin), "Group already exists!");
    		return;
    	}

    	int groupId = DBHelper.getGroupId(groupName);
    	if(groupId==-1) {
    		sendSystem(getAnySession(admin), "Error retrieving group ID!");
    		return;
    	}
    	
    	DBHelper.addGroupMember(groupId, admin, admin);
    	if(members!=null) {
    		for(String m:members) {
    			if(!m.equals(admin)) {
    				DBHelper.addGroupMember(groupId, m, admin);
    				sendSystem(getAnySession(admin), m + " added to group '"+groupName+"'");
    			}
    		}
    	}
    }
    
    private void handleAddGroupMember(String admin, String groupName, String member) throws IOException {
    	int groupId = DBHelper.getGroupId(groupName);
    	if(!DBHelper.isGroupAdmin(groupId, admin)) {
    		sendSystem(getAnySession(admin), "Only admins can add members");
    		return;
    	}
    	
    	if(DBHelper.addGroupMember(groupId, member, admin)) {
    		sendSystem(getAnySession(admin), member + " added to group '"+groupName+"'");
    	} 
    }
    
    private void handleRemoveGroupMember(String admin, String groupName, String member) throws IOException {
    	int groupId = DBHelper.getGroupId(groupName);
    	if(!DBHelper.isGroupAdmin(groupId, admin)) {
    		sendSystem(getAnySession(admin), "Only admins can remove members");
    		return;
    	}
    	
    	if(DBHelper.removeGroupMember(groupId, member, admin)) {
            sendSystem(getAnySession(admin), member + " removed from group '" + groupName + "'");
    	} 
    }
    
    private void handleDeleteGroupMessage(String username, int messageId, String groupName) throws IOException {
    	if(DBHelper.deleteSpecificMessage(messageId, username)) {
    		Map<String, Object> msg = Map.of("type","group-message-deleted", 
    											"messageId",messageId,
    											"groupName", groupName);
    		String json = gson.toJson(msg);
    		for(Session s:userSessions.getOrDefault(username, Collections.emptySet())) {
    			if(s.isOpen()) s.getBasicRemote().sendText(json);
    		}
    	}
    }
    
    private void handleDeletePrivateMessage(String username, int messageId, String toUser) throws IOException {
    	if(DBHelper.deleteSpecificMessage(messageId, username)) {
    		Map<String, Object> msg = Map.of("type","private-message-deleted", 
    											"messageId",messageId
    											);
    		Set<Session> sessionsToNotify = new HashSet<>();
    		sessionsToNotify.addAll(userSessions.getOrDefault(username, Collections.emptySet()));
    		sessionsToNotify.addAll(userSessions.getOrDefault(toUser, Collections.emptySet()));

    		String json = gson.toJson(msg);
    		for(Session s:sessionsToNotify) {
    			if(s.isOpen()) s.getBasicRemote().sendText(json);
    		}
    	}
    }
    private void sendGroupMessage(String sender, String groupName, String content) throws IOException {
    	int groupId = DBHelper.getGroupId(groupName);
    	if(groupId==-1) {
    		sendSystem(getAnySession(sender), "Group not found: "+groupName);
    		return;
    	}
    	
       List<String> members = DBHelper.getGroupMembers(groupId);
        Map<String,String> msg = Map.of(
            "type", "group-message",
            "sender", sender,
            "groupName", groupName,
            "content", content,
            "timestamp", now()
        );
        String json = gson.toJson(msg);

        for (String member : members) {
        	for(Session s:userSessions.getOrDefault(member, Collections.emptySet())) {
        		if(s.isOpen()) s.getAsyncRemote().sendText(json);
        	}
        }
        DBHelper.storeMessage(sender, DBHelper.getEmail(sender), null, null, groupId, content, "group-message");
    }

    
   
    private void sendGroupList(String username) throws IOException {
       List<String> groups = new DBHelper().getUserGroups(username);
        Map<String, Object> msg = Map.of(
        		"type","group-list",
        		"groups",groups,
        		"timestamp", now()
        		);
        
        Set<Session> sessionsForUser = userSessions.getOrDefault(username, Collections.emptySet());
        for(Session s:sessionsForUser) {
        	if(s.isOpen()) {
        		s.getBasicRemote().sendText(gson.toJson(msg));        		
        	}
        }
    }

    private void sendOnlineUsersList() throws IOException{
    	List<String> onlineUsers = new ArrayList<>(userSessions.keySet());
    	Map<String, Object> msg = Map.of(
                "type", "online-users",
                "users", onlineUsers,
                "timestamp", now()
        );
    	String json = gson.toJson(msg);
    	
    	for(Session s : sessions) {
			if(s.isOpen()) {
				s.getAsyncRemote().sendText(json);
			}
		}
    }
    
    private void sendAllUsersList() throws IOException{
    	List<Map<String, Object>> allUsers = DBHelper.getAllUsersWithStatus();
    	Map<String, Object> msg = Map.of(
    				"type", "all-users",
    				"users", allUsers,
    				"timestamp", now()
    			);
		String json = gson.toJson(msg);
		    	
    	for(Session s : sessions) {
			if(s.isOpen()) {
				s.getAsyncRemote().sendText(json);
			}
		}
    }
    
    private void sendSystem(Session session, String content) throws IOException
    {
    	if(session!=null && session.isOpen()) {
    		Map<String, String> msg = Map.of(
    				"type","system",
    				"content",content,
    				"timestamp", now());
        	session.getBasicRemote().sendText(gson.toJson(msg));
    	}
    }
    
    private boolean validateSession(Map<String, List<String>> headers, String username) {
    	if(headers==null || !headers.containsKey("Cookie")) return false;
    	
    	List<String> cookies = headers.get("Cookie");
    	for(String cookieHeader:cookies) {
    		for(String cookie:cookieHeader.split(";")) {
    			String parts[] = cookie.trim().split("=");
    			if(parts.length == 2 && parts[0].equals("username") && parts[1].equals(username)) {
    				return true;
    			}
    		}
    	}
    	return false;
    }
    
    
    private Session getAnySession(String username) {
        Set<Session> set = userSessions.get(username);
        if (set == null || set.isEmpty()) return null;
        for (Session s : set) {
            if (s != null && s.isOpen()) return s;
        }
        return null;
    }

    private void broadcastSystem(String message) throws IOException {
    	Map<String, String> msg = Map.of(
    			"type","system",
    			"content",message,
    			"timestamp", now());
    	String json = gson.toJson(msg);
      
        for (Session s : sessions) {
            if (s.isOpen()) {
                s.getBasicRemote().sendText(json);
            }
        }
        
    }
    
    private void broadcastUserStatus(String username, String status) throws IOException
    {
    	String lastSeen = status.equals("offline") ? DBHelper.getLastSeen(username) : "online";
    	Map<String, Object> msg = Map.of(
    			"type", "user-status-update",
                "username", username,
                "status", status,
                "lastSeen", lastSeen,
                "timestamp", now()
    			);
    	String json = gson.toJson(msg);
        for (Session s : sessions)
            if (s.isOpen()) s.getAsyncRemote().sendText(json);
		
    }
    
    private void sendHistoryToAllSessions(String username, List<Map<String, Object>> messages, String type) throws IOException {
        Map<String, Object> msg = new HashMap<>();
        msg.put("type", type);
        msg.put("messages", messages);

        String json = gson.toJson(msg);
        Set<Session> sessionsForUser = userSessions.getOrDefault(username, Collections.emptySet());
        for (Session s : sessionsForUser) {
            if (s.isOpen()) {
                s.getAsyncRemote().sendText(json);
            }
        }
    }

    private String now() { 
        return new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").format(new java.util.Date());
    }
   
}
