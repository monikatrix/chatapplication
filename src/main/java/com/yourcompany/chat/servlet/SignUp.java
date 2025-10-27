package com.yourcompany.chat.servlet;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.yourcompany.chat.util.DBHelper;
import com.yourcompany.chat.util.PasswordUtil;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.*;
import java.io.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;

@WebServlet("/signup")
public class SignUp extends HttpServlet {
	
	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
			resp.setContentType("text/plain");
			
			Gson gson = new Gson();
			Map<String, String> userData = gson.fromJson(
	    			req.getReader(),
	    			new TypeToken<Map<String, String>>(){}.getType()
	    			);
	    			
	    	
	    	String username = userData.get("username");
	    	String email = userData.get("email");
	    	String plainPassword = userData.get("password");

			if(username==null || email==null || plainPassword == null || plainPassword.isEmpty()) {
				resp.setStatus(HttpServletResponse.SC_BAD_GATEWAY);
				resp.getWriter().write("Invalid input");
				return;
			}
			
			String hashedPassword;
			try {
			    hashedPassword = PasswordUtil.hashPassword(plainPassword);
			} catch (Exception ex) {
			    ex.printStackTrace();
			    resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
			    resp.getWriter().write("{\"error\":\"Password hashing failed\"}");
			    return;
			}

			
			try(Connection conn = DBHelper.getConnection()){
				PreparedStatement checkStmt = conn.prepareStatement("SELECT id FROM users WHERE email=?");
				checkStmt.setString(1, email);
				ResultSet rs = checkStmt.executeQuery();
				
				if(rs.next()) {
					resp.setStatus(HttpServletResponse.SC_CONFLICT);
					resp.getWriter().write("User already exists");
					return;
				}
				
				PreparedStatement stmt = conn.prepareStatement(
						"INSERT INTO users (username, email, password) VALUES (?,?,?)");
				stmt.setString(1, username);
	            stmt.setString(2, email);
	            stmt.setString(3, hashedPassword);
	            stmt.executeUpdate();

	            gson.toJson(Map.of(
	            		"status", "success",
	            		"message"," User registered successfully"
	            		),resp.getWriter());
				
			}
			catch(SQLException e) {
				e.printStackTrace();
				resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
				gson.toJson(Map.of("error","Database error"),resp.getWriter());
			}
			
	}
	
}
