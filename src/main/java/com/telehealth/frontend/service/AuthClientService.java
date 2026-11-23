package com.telehealth.frontend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AuthClientService {

    private final RestTemplate restTemplate;

    @Value("${gateway.url}")
    private String gatewayUrl;

    public void register(String email, String password, boolean enable2fa) {
        String url = gatewayUrl + "/api/v1/auth/register";
        Map<String, Object> request = new HashMap<>();
        request.put("email", email);
        request.put("password", password);
        request.put("role", "ROLE_PATIENT");
        request.put("enable2fa", enable2fa);

        restTemplate.postForEntity(url, request, Map.class);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> login(String email, String password) {
        String url = gatewayUrl + "/api/v1/auth/login";
        Map<String, String> request = new HashMap<>();
        request.put("email", email);
        request.put("password", password);

        HttpHeaders headers = new HttpHeaders();
        headers.set("DPoP", "123.456.789"); // TODO: fix this sample data
        HttpEntity<Map<String, String>> entity = new HttpEntity<>(request, headers);

        ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.POST, entity, Map.class);
        Map<String, Object> body = response.getBody() != null ? new HashMap<>(response.getBody()) : new HashMap<>();

        if (response.getStatusCode() == HttpStatus.ACCEPTED) {
            body.put("requires2fa", true);
        }
        return body;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> verify2fa(String email, String code) {
        String url = gatewayUrl + "/api/v1/auth/verify-2fa";
        Map<String, String> request = new HashMap<>();
        request.put("email", email);
        request.put("code", code);

        HttpHeaders headers = new HttpHeaders();
        headers.set("DPoP", "123.456.789");
        HttpEntity<Map<String, String>> entity = new HttpEntity<>(request, headers);

        ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.POST, entity, Map.class);
        return response.getBody();
    }
}
