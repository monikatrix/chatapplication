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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.*;

@WebServlet("/signin")
public class SignIn extends HttpServlet {
	private static final Logger logger = LoggerFactory.getLogger(SignIn.class);
	//maps username to sessionId
	private static Map<String, String> sessionMap = new HashMap<>();
	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException{
		HttpSession session = req.getSession(false);
		resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
		if(session!=null && session.getAttribute("email")!=null) {
			String email = (String) session.getAttribute("email");
			resp.getWriter().write("{\"email\": \""+email+"\"}");
		}
		else {
			resp.getWriter().write("{\"email\":null}");
		}
	}
	
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {
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
        
        if(usernameFromCookie!=null && authTokenFromCookie!=null && validateSession(usernameFromCookie)) {
        	resp.sendRedirect("chat.html");
        	return;
        }
        
        logger.info("Sign-in attempt for{}",email);

        try (Connection conn = DBHelper.getConnection()) {
            PreparedStatement stmt = conn.prepareStatement(
                    "SELECT username, email, password FROM users WHERE email=?");
            stmt.setString(1, email);
            ResultSet rs = stmt.executeQuery();
            
            if(rs.next()) {
            	if (!PasswordUtil.checkPassword(password, rs.getString("password"))) {
            		logger.warn("Invalid sign-in credentials for{}", email);
            		resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            		gson.toJson(Map.of("error", "Invalid email or password"),resp.getWriter());
            		return;
            	}
            	
            	String username = rs.getString("username");
            	
            	String authToken = UUID.randomUUID().toString();
            	
            	sessionMap.put(username, authToken);
            	 Timestamp expiryTime = new Timestamp(System.currentTimeMillis() + (30 * 60 * 1000));

                 PreparedStatement updateStmt = conn.prepareStatement(
                         "UPDATE users SET token_expiry=? WHERE username=?");
                 updateStmt.setTimestamp(1, expiryTime);
                 updateStmt.setString(2, username);
                 updateStmt.executeUpdate();
            	
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
            	
            	logger.info("User {} signed in successfully", username);
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
        } catch (Exception e) {
        	logger.error("Error during sign-in process",e);
            e.printStackTrace();
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            gson.toJson(Map.of("error", "Internal server error"), resp.getWriter());
        }

    }
    public static boolean validateSession(String username) {
    	try(Connection conn = DBHelper.getConnection()){
    		PreparedStatement stmt = conn.prepareStatement(
    				"SELECT token_expiry FROM users WHERE username=?");
    		stmt.setString(1, username);
    		
    		ResultSet rs = stmt.executeQuery();
    		
    		if(rs.next()) {
    			Timestamp expiry = rs.getTimestamp("token_expiry");
    			boolean valid = expiry!=null && expiry.after(new Timestamp(System.currentTimeMillis()));
    			if(!valid) {
    				logger.warn("Session expired for user {}", username);
    			}
    			return valid;
    		}
    	}catch(SQLException e) {
    		logger.error("Error validating session for user {}", username, e);
    	}
    	return false;
    }
}

        