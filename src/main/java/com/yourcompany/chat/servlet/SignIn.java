package com.yourcompany.chat.servlet;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.yourcompany.chat.model.User;
import com.yourcompany.chat.util.DBHelper;
import com.yourcompany.chat.util.PasswordUtil;

import javax.servlet.*;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.*;
import java.io.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.*;

@WebServlet("/signin")
public class SignIn extends HttpServlet {
	//maps username ito sessionId
	private static Map<String, String> sessionMap = new HashMap<>();

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {
    	System.out.println(new Timestamp(System.currentTimeMillis() + (30 * 60)));

    	Gson gson = new Gson();
    	Map<String, String> credentials = gson.fromJson(req.getReader(),Map.class);
    	String email = credentials.get("email");
    	String password = credentials.get("password");
    	
        resp.setContentType("application/json");
        
        Cookie[] cookies = req.getCookies();
        String usernameFromCookie = null;
        String authTokenFromCookie = null;
        
        if(cookies!=null) {
        	for(Cookie c:cookies) {
        		if(c.getName().equals("username")) {
        			usernameFromCookie = c.getValue();
        		}
        		if(c.getName().equals("authToken")) {
        			authTokenFromCookie = c.getValue();
        		}
        	}
        }
        
        if(usernameFromCookie!=null && authTokenFromCookie!=null && validateSession(usernameFromCookie, authTokenFromCookie)) {
        	resp.sendRedirect("chat.html");
        	return;
        }
        
        try (Connection conn = DBHelper.getConnection()) {
            PreparedStatement stmt = conn.prepareStatement(
                    "SELECT username, email, password FROM users WHERE email=?");
            stmt.setString(1, email);
            ResultSet rs = stmt.executeQuery();
            
            if(rs.next()) {
            	if (!PasswordUtil.checkPassword(password, rs.getString("password"))) {
            		resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            		gson.toJson(Map.of("error", "Invalid email or password"),resp.getWriter());
            		return;
            	}
            	
            	String username = rs.getString("username");
            	
            	String authToken = UUID.randomUUID().toString();
            	final long THIRTY_MINUTES_MS = 30 * 60 * 1000;
            	Timestamp expiryTime = new Timestamp(System.currentTimeMillis() + THIRTY_MINUTES_MS);
            	System.out.println(expiryTime);
            	
            	PreparedStatement updateStmt = conn.prepareStatement(
            			"UPDATE users SET auth_token=?, token_expiry=? WHERE email=?");
            	updateStmt.setString(1, authToken);
            	updateStmt.setTimestamp(2, expiryTime);
            	updateStmt.setString(3, email);
            	updateStmt.executeUpdate();
            	
            	sessionMap.put(username, authToken);
            	
            	Cookie userCookie = new Cookie("username",username);
            	Cookie authCookie = new Cookie("authToken",authToken);
            	
            	userCookie.setMaxAge(30 * 60);
            	authCookie.setMaxAge(30 * 60);
            	userCookie.setPath("/");
            	authCookie.setPath("/");
            	authCookie.setHttpOnly(true);
            	
            	resp.addCookie(userCookie);
            	resp.addCookie(authCookie);
            	
            	HttpSession session = req.getSession(true);
            	session.setAttribute("username", username);
            	session.setAttribute("email", email);
            	
            	gson.toJson(Map.of(
            			"status", "success",
            			"username", username,
            			"email", email,
            			"authtoken", authToken
            			),resp.getWriter());
            	
            }
            else {
                resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                resp.getWriter().write("Invalid email or password");
            }
        } catch (SQLException e) {
            e.printStackTrace();
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            gson.toJson(Map.of("error", "Database error"), resp.getWriter());
        }

    }
    public static boolean validateSession(String username, String authToken) {
    	try(Connection conn = DBHelper.getConnection()){
    		PreparedStatement stmt = conn.prepareStatement(
    				"SELECT token_expiry FROM users WHERE username=? AND auth_token=?");
    		stmt.setString(1, username);
    		stmt.setString(2, authToken);
    		
    		ResultSet rs = stmt.executeQuery();
    		
    		if(rs.next()) {
    			Timestamp expiry = rs.getTimestamp("token_expiry");
    			return expiry!=null && expiry.after(new Timestamp(System.currentTimeMillis()));
    		}
    	}catch(SQLException e) {
    		e.printStackTrace();
    	}
    	return false;
    }
}

        