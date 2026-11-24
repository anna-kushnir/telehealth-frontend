package com.telehealth.frontend.controller;

import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.client.HttpClientErrorException;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(HttpClientErrorException.class)
    public String handleApiClientError(HttpClientErrorException e, HttpSession session) {
        if (session.getAttribute("jwtToken") == null) {
            return "redirect:/login";
        }

        int statusCode = e.getStatusCode().value();
        if (statusCode == 401 || statusCode == 403) {
            return "patient/mfa-required";
        }

        throw e;
    }
}
