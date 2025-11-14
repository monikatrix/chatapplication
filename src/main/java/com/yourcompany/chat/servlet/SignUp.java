package com.yourcompany.chat.servlet;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.yourcompany.chat.util.DBHelper;
import com.yourcompany.chat.util.PasswordUtil;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;

@WebServlet("/signup")
public class SignUp extends HttpServlet {
	private static final Logger logger = LoggerFactory.getLogger(SignUp.class);
	
	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
			resp.setContentType("application/json");
			
			Gson gson = new Gson();
			Map<String, String> userData = gson.fromJson(
	    			req.getReader(),
	    			new TypeToken<Map<String, String>>(){}.getType()
	    			);
	    			
	    	
	    	String username = userData.get("username");
	    	String email = userData.get("email");
	    	String plainPassword = userData.get("password");

			if(username==null || email==null || plainPassword == null || plainPassword.isEmpty()) {
				logger.warn("Invalid signup input: username={}, email={}",username, email);
				resp.setStatus(HttpServletResponse.SC_BAD_GATEWAY);
				resp.getWriter().write("Invalid input");
				return;
			}
			
			String hashedPassword = PasswordUtil.hashPassword(plainPassword);;
			
			try(Connection conn = DBHelper.getConnection()){
				PreparedStatement checkStmt = conn.prepareStatement("SELECT id FROM users WHERE email=?");
				checkStmt.setString(1, email);
				ResultSet rs = checkStmt.executeQuery();
				
				if(rs.next()) {
					logger.info("User already exists: {}", email);
					resp.setStatus(HttpServletResponse.SC_CONFLICT);
					gson.toJson(Map.of("error", "User already exists"), resp.getWriter());
					return;
				}
				
				PreparedStatement stmt = conn.prepareStatement(
						"INSERT INTO users (username, email, password) VALUES (?,?,?)");
				stmt.setString(1, username);
	            stmt.setString(2, email);
	            stmt.setString(3, hashedPassword);
	            stmt.executeUpdate();
	            
	            logger.info("New user registered successfully: {}",email);

	            gson.toJson(Map.of(
	            		"status", "success",
	            		"message"," User registered successfully"
	            		),resp.getWriter());
				
			}
			catch(Exception e) {
				logger.error("Error during signup process", e);
				resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
				gson.toJson(Map.of("error","Internal server error"),resp.getWriter());
			}
			
	}
	
}
