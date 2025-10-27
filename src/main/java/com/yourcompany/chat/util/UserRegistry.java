package com.yourcompany.chat.util;

import javax.websocket.Session;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

public class UserRegistry {
    private static final Map<String, Session> users = new ConcurrentHashMap<>();

    public static void addUser(String username, Session session) {
        users.put(username, session);
    }

    public static void removeUser(String username) {
        users.remove(username);
    }

    public static Session getUserSession(String username) {
        return users.get(username);
    }

    public static Map<String, Session> getAllUsers() {
        return users;
    }
}
