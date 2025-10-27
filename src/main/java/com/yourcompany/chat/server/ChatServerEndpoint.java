package com.yourcompany.chat.server;
import javax.websocket.*;
import javax.websocket.server.PathParam;
import javax.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.databind.*;
import com.google.gson.Gson;
import com.yourcompany.chat.servlet.SignIn;
import com.yourcompany.chat.util.DBHelper;

@ServerEndpoint(value = "/chat/{username}", configurator = CustomConfigurator.class)
public class ChatServerEndpoint {

    private static Set<Session> sessions = Collections.synchronizedSet(new HashSet<>());
    private static Map<String, Session> userSessions = new ConcurrentHashMap<>();
    private static Map<String, Group> groups = new ConcurrentHashMap<>();

    private static Gson gson = new Gson();
    @OnOpen
    public void onOpen(Session session, EndpointConfig config, @PathParam("username") String username) throws IOException {
    	Map<String,List<String>> headers = (Map<String,List<String>>)config.getUserProperties().get("headers");
    	
    	if(!validateSession(headers, username)) {
    		session.close(new CloseReason(CloseReason.CloseCodes.VIOLATED_POLICY, "Invalid session"));
    		return;
    	}
    	boolean isNewJoin = !userSessions.containsKey(username);
    	
        userSessions.put(username, session);
        sessions.add(session);
        sendSystem(session, "Welcome " + username + "!");
        if(isNewJoin) {
        	broadcastSystem(username + " joined the chat.");
        }
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

                case "group":
                	String toList = (String) msgObj.get("to");
                    List<String> recipients = toList == null
                            ? new ArrayList<>()
                            : Arrays.asList(toList.split(","));
                    sendGroup(username, content , recipients);
                    break;
                    
                case "create-group":
                	String groupName = (String) msgObj.get("groupName");
                	List<String> members = (List<String>) msgObj.get("members");
                	createGroup(username, groupName, members);
                	broadcastGroupListToAll();
                	break;
                	
                case "group-message":
                	String targetGroup = (String) msgObj.get("groupName");
                	sendGroupMessage(username,targetGroup,content);
                	break;
                case "list-groups":
                    sendGroupList(username);
                    break;

                
                case "group-add-member":
                	addGroupMember(username,(String)msgObj.get("groupName"),(String)msgObj.get("members"));
                	break;
                	
                case "group-remove-member":
                	removeGroupMember(username,(String)msgObj.get("groupName"),(String)msgObj.get("members"));
                	break;
                	
                case "delete-group":
                	deleteGroup(username, (String) msgObj.get("groupName"));
                	break;
                	
                default:
                    session.getBasicRemote().sendText("Invalid message type!");
            }

        } catch (Exception e) {
            session.getBasicRemote().sendText("Error processing message: " + e.getMessage());
            e.printStackTrace();
        }
    }

	@OnClose
    public void onClose(Session session, @PathParam("username") String username) throws IOException {
        sessions.remove(session);
        if(userSessions.remove(username)!=null) {
        	broadcastSystem(username + " left the chat.");        	
        }
        System.out.println("WebSocket closed for user: " + username);
    }

    @OnError
    public void onError(Session session, Throwable throwable) {
        System.err.println("WebSocket Error: " + throwable.getMessage());
    }

    private boolean validateSession(Map<String, List<String>> headers, String username) {
    	if(headers==null || !headers.containsKey("Cookie")) return false;
    	
    	List<String> cookies = headers.get("Cookie");
    	for(String cookieHeader:cookies) {
    		String[] cookiePairs = cookieHeader.split(";");
    		for(String cookie:cookiePairs) {
    			String parts[] = cookie.trim().split("=");
    			if(parts.length == 2 && parts[0].equals("username") && parts[1].equals(username)) {
    				String authToken = null;
    				for(String c:cookiePairs) {
    					String[] kv = c.trim().split("=");
    					if(kv.length==2 && kv[0].equals("authToken")) {
    						authToken = kv[1];
    						break;
    					}
    				}
    				return authToken!=null && SignIn.validateSession(username, authToken);
    			}
    		}
    	}
    	return false;
    }
    private void broadcast(String sender, String message) throws IOException {
    	Map<String, String> msg = Map.of("type","broadcast","sender",sender,"content",message,"timestamp", now());
    	String json = gson.toJson(msg);
    	
        synchronized (sessions) {
            for (Session s : sessions) {
                if (s.isOpen()) {
                    s.getBasicRemote().sendText(json);
                }
            }
        }
        DBHelper.storeMessage(sender, DBHelper.getEmail(sender), "ALL", null, null, message, "broadcast");
     }

    private void broadcastSystem(String message) throws IOException {
    	Map<String, String> msg = Map.of("type","system","content",message,"timestamp", now());
    	String json = gson.toJson(msg);
    	
        synchronized (sessions) {
            for (Session s : sessions) {
                if (s.isOpen()) {
                    s.getBasicRemote().sendText(json);
                }
            }
        }
    }

    private void sendPrivate(String from, String to, String message) throws IOException {
        Session recipient = userSessions.get(to);
        Map<String, String> msg = Map.of("type","private","sender",from,"content",message,"timestamp", now());
    	String json = gson.toJson(msg);
    	
        if (recipient != null && recipient.isOpen()) {
            recipient.getBasicRemote().sendText(json);
        } else {
            sendSystem(userSessions.get(from), "User '" + to + "' not found or offline.");
        }
        DBHelper.storeMessage(from, DBHelper.getEmail(from), to, DBHelper.getEmail(to), null, message, "private");
    }

    private void sendGroup(String from, String message, List<String> recipients) throws IOException {
        Map<String, String> msg = Map.of("type", "group","sender",from, "content",message,"timestamp",now());

        for (String member : recipients) {
        	Session s = userSessions.get(member.trim());
        	if(s!=null && s.isOpen()) {
        		s.getBasicRemote().sendText(gson.toJson(msg));
        	}
        	DBHelper.storeMessage(from, DBHelper.getEmail(from), member, DBHelper.getEmail(member), null, message, "group");
        }
    }
    
    private void sendSystem(Session session, String content) throws IOException
    {
    	if(session!=null && session.isOpen()) {
    		Map<String, String> msg = Map.of("type","system","content",content,"timestamp", now());
        	session.getBasicRemote().sendText(gson.toJson(msg));
    	}
    }
    
    private void createGroup(String admin, String groupName, List<String> members) throws IOException{
    	if(groups.containsKey(groupName)) {
    		sendSystem(userSessions.get(admin),"Group already exists!");
    		return;
    	}
    	
    	Group g = new Group(groupName, admin);
    	g.members.addAll(members);
    	g.members.add(admin);
    	groups.put(groupName,g);
    	broadcastSystem("Group '"+groupName+" 'created by "+admin);
    }
    
    private void sendGroupMessage(String sender, String groupName, String content) throws IOException {
        if (groupName == null || groupName.trim().isEmpty()) {
            sendSystem(userSessions.get(sender), "No group selected to send message!");
            return;
        }

        Group g = groups.get(groupName);
        if (g == null) {
            sendSystem(userSessions.get(sender), "Group '" + groupName + "' not found.");
            return;
        }

        Map<String,String> msg = Map.of(
            "type", "group-message",
            "sender", sender,
            "groupName", groupName,
            "content", content,
            "timestamp", now()
        );

        for (String member : g.members) {
            Session s = userSessions.get(member);
            if (s != null && s.isOpen()) {
                s.getBasicRemote().sendText(gson.toJson(msg));
            }
        }
        DBHelper.storeMessage(sender, DBHelper.getEmail(sender), null, null, groupName, content, "group-message");
    }

    
    private void addGroupMember(String admin, String groupName, String newMember) throws IOException {
        Group g = groups.get(groupName);
        if (g == null || !g.admin.equals(admin)) { sendSystem(userSessions.get(admin), "Only admin can add members!"); return; }
        g.members.add(newMember);
        broadcastSystem(newMember + " added to group '" + groupName + "' by " + admin);
    }

    private void removeGroupMember(String admin, String groupName, String member) throws IOException {
        Group g = groups.get(groupName);
        if (g == null || !g.admin.equals(admin)) { sendSystem(userSessions.get(admin), "Only admin can remove members!"); return; }
        g.members.remove(member);
        broadcastSystem(member + " removed from group '" + groupName + "' by " + admin);
    }
    
    private void deleteGroup(String admin, String groupName) throws IOException{
    	Group g = groups.get(groupName);
    	if(g==null || !g.admin.equals(admin)) {
    		sendSystem(userSessions.get(admin)," Only admin can delete the group!");
    		return;
    	}
    	groups.remove(groupName);
    	broadcastSystem("Group "+groupName+" deleted by "+admin);
    }

    private void sendGroupList(String username) throws IOException {
        Session session = userSessions.get(username);
        if (session == null || !session.isOpen()) return;

        List<String> groupNames = new ArrayList<>(groups.keySet());
        Map<String, Object> msg = new HashMap<>();
        msg.put("type", "group-list");
        msg.put("groups", groupNames);
        msg.put("timestamp", now());

        session.getBasicRemote().sendText(gson.toJson(msg));
    }

    private void broadcastGroupListToAll() throws IOException {
        List<String> groupNames = new ArrayList<>(groups.keySet());
        Map<String, Object> msg = new HashMap<>();
        msg.put("type", "group-list");
        msg.put("groups", groupNames);
        msg.put("timestamp", now());
        String json = gson.toJson(msg);

        synchronized (sessions) {
            for (Session s : sessions) {
                if (s.isOpen()) {
                    s.getBasicRemote().sendText(json);
                }
            }
        }
    }

    private String now() { 
    	return new java.text.SimpleDateFormat("HH:mm").format(new Date());
    }
    
    static class Group{
    	String name;
    	String admin;
    	Set<String> members = new HashSet<>();
		public Group(String name, String admin) {
			this.name = name;
			this.admin = admin;
		}
    }
}
