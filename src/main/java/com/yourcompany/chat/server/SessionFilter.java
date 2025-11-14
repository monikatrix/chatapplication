package com.yourcompany.chat.server;

import javax.servlet.*;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.*;

import com.yourcompany.chat.servlet.SignIn;

import java.io.IOException;

@WebFilter(urlPatterns = {"/*"}, dispatcherTypes = {DispatcherType.REQUEST})
public class SessionFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse resp = (HttpServletResponse) response;

        String requestURI = req.getRequestURI();
        String contextPath = req.getContextPath(); // "/chat-application"

        if (requestURI.endsWith(".css") ||
            requestURI.endsWith(".js") ||
            requestURI.matches(".*\\.(png|jpg|jpeg|gif|ico|woff|woff2|ttf|eot|svg)$")) {
            chain.doFilter(request, response);
            return;
        }

        final String SIGNIN_PAGE = contextPath + "/signin.html";
        final String SIGNUP_PAGE = contextPath + "/signup.html";
        final String SIGNIN_SERVLET = contextPath + "/signin";
        final String SIGNUP_SERVLET = contextPath + "/signup";
        final String CHAT_PAGE = contextPath + "/chat.html";
        
        String username = getCookieValue(req, "username");
        String authToken = getCookieValue(req, "authToken");
        
        boolean isValid = username != null && authToken != null &&
        		SignIn.validateSession(username);
        
        if(isValid && (requestURI.equals(SIGNIN_PAGE) || requestURI.equals(SIGNUP_PAGE))) {
        	resp.sendRedirect(CHAT_PAGE);
        	return;
        }

        if (requestURI.equals(SIGNIN_PAGE) ||
            requestURI.equals(SIGNUP_PAGE) ||
            requestURI.equals(SIGNIN_SERVLET) ||
            requestURI.equals(SIGNUP_SERVLET)) {
            chain.doFilter(request, response);
            return;
        }

        if (isValid) {
            chain.doFilter(request, response);
        } else {
            resp.sendRedirect(SIGNIN_PAGE);
        }
    }

    private String getCookieValue(HttpServletRequest req, String name) {
        if (req.getCookies() != null) {
            for (Cookie cookie : req.getCookies()) {
                if (cookie.getName().equals(name)) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }
}
