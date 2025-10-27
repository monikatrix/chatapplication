package com.yourcompany.chat.server;

import java.io.IOException;

import javax.servlet.DispatcherType;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.yourcompany.chat.servlet.SignIn;

@WebFilter(urlPatterns = {"/*"},dispatcherTypes = {DispatcherType.REQUEST})

public class SessionFilter implements Filter {
	public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
		HttpServletRequest req = (HttpServletRequest) request;
		HttpServletResponse resp = (HttpServletResponse) response;
		String requestURI = req.getRequestURI();
		String contextPath = req.getContextPath();
		
		if (requestURI.endsWith(".css") || 
		        requestURI.endsWith(".js") || 
		        requestURI.endsWith(".png") ||
		        requestURI.endsWith(".jpg") ||
		        requestURI.endsWith(".ico")) {
		        
		        chain.doFilter(request, response);
		        return; 
		    }
		
		String usernameFromCookie = getCookieValue(req,"username");
		String authtokenFromCookie = getCookieValue(req, "authToken");
		
		boolean isValid = usernameFromCookie!=null && authtokenFromCookie!=null && 
				SignIn.validateSession(usernameFromCookie, authtokenFromCookie);
		
		
		if(isValid) {
			chain.doFilter(request, response);
		}
		else {
			final String SIGNIN_URL = contextPath + "/signin.html";
            final String SIGNIN_SERVLET = contextPath + "/signin";
            final String SIGNUP_URL = contextPath + "/signup.html";
            final String SIGNUP_SERVLET = contextPath + "/signup"; 
            
            boolean isPublicPage = requestURI.startsWith(SIGNIN_URL) || 
                                   requestURI.startsWith(SIGNIN_SERVLET) ||
                                   requestURI.startsWith(SIGNUP_URL) || 
                                   requestURI.startsWith(SIGNUP_SERVLET);

            if (isPublicPage) {
                chain.doFilter(request, response);
            } else {
                
                resp.sendRedirect(SIGNIN_URL); 
            }
		}
	}
	private String getCookieValue(HttpServletRequest req, String name) {
		if(req.getCookies()!=null) {
			for(Cookie cookie: req.getCookies()) {
				if(cookie.getName().equals(name)) {
					return cookie.getValue();
				}
			}
		}
		return null;
	}
}
