package com.yourcompany.chat.servlet;

import java.io.IOException;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.yourcompany.chat.server.ChatServerEndpoint;

@WebServlet("/logout")
public class LogoutServlet extends HttpServlet {
	private static final Logger logger = LoggerFactory.getLogger(SignIn.class);

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException{
		Cookie cookies[] = req.getCookies();
		String username = null;
		
		if(cookies!=null) {
			for(Cookie c:cookies) {
				if(c.getName().equals("username")) {
					username = c.getValue();
					c.setValue("");
					c.setPath("/");
					c.setMaxAge(0);
					resp.addCookie(c);
				}
				if(c.getName().equals("authToken")) {
					c.setValue("");
					c.setPath("/");
					c.setMaxAge(0);
					resp.addCookie(c);
				}
			}
		}
		
		HttpSession session = req.getSession(false);
		if(session!=null) {
			session.invalidate();
			logger.info("HTTP session invalidated for user {}",username);
		}
		if(username!=null) {
			logger.info("User {} logged out and HTTP session invalidated.", username);
			ChatServerEndpoint.closeUserConnection(username);
		}
		
		resp.sendRedirect("signin.html");
	}
}
