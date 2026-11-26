package com.telehealth.frontend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import tools.jackson.databind.ObjectMapper;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TelehealthApiService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${gateway.url}")
    private String gatewayUrl;

    private HttpHeaders buildHeaders(String jwtToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("DPoP", "123.456.789");
        if (jwtToken != null && !jwtToken.isBlank()) {
            headers.set("Authorization", "DPoP " + jwtToken);
        }
        return headers;
    }

    private List<Map<String, Object>> sendGetRequestWithTokenReturnsList(String token, String url) {
        ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                url, HttpMethod.GET, new HttpEntity<>(buildHeaders(token)),
                new ParameterizedTypeReference<>() {}
        );
        return response.getBody() != null ? response.getBody() : List.of();
    }

    private Map<String, Object> sendGetRequestWithTokenReturnsEntity(String token, String url) {
        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                url, HttpMethod.GET, new HttpEntity<>(buildHeaders(token)),
                new ParameterizedTypeReference<>() {}
        );
        return response.getBody();
    }

    public String extractErrorMessage(Exception e, String fallback) {
        if (e instanceof HttpStatusCodeException statusEx) {
            try {
                Map<?, ?> body = objectMapper.readValue(statusEx.getResponseBodyAsString(), Map.class);
                Object errorMsg = body.get("error");
                if (errorMsg != null && !errorMsg.toString().isBlank()) {
                    return errorMsg.toString();
                }
            } catch (Exception ignored) {}
        }
        return fallback;
    }

    // ----- Doctors & Profiles -----

    public List<Map<String, Object>> getAllDoctors(String specialty) {
        String url = gatewayUrl + "/api/v1/profiles/doctors"
                + (specialty != null && !specialty.isBlank() ? "?specialty=" + specialty : "");
        return sendGetRequestWithTokenReturnsList(null, url);
    }

    public Map<String, Map<String, Object>> getDoctorsMap() {
        return getAllDoctors(null).stream()
                .collect(Collectors.toMap(d -> String.valueOf(d.get("doctorId")), d -> d, (a, b) -> a));
    }

    public Set<String> getAllSpecialties() {
        return getAllDoctors(null).stream()
                .map(d -> String.valueOf(d.get("specialty")))
                .collect(Collectors.toSet());
    }

    public Map<String, Object> getMyPatientProfile(String token) {
        String url = gatewayUrl + "/api/v1/profiles/patient/me";
        try {
            return sendGetRequestWithTokenReturnsEntity(token, url);
        } catch (Exception e) {
            return null;
        }
    }

    public void updateMyPatientProfile(String token, String firstName, String lastName) {
        String url = gatewayUrl + "/api/v1/profiles/patient/me";
        Map<String, String> body = Map.of("firstName", firstName, "lastName", lastName);
        restTemplate.exchange(url, HttpMethod.PUT, new HttpEntity<>(body, buildHeaders(token)), Map.class);
    }

    // ----- Appointments & Slots -----

    public List<Map<String, Object>> getAvailableSlots(String doctorId, String startIso, String endIso) {
        String url = String.format("%s/api/v1/appointments/slots/available?doctorId=%s&start=%s&end=%s",
                gatewayUrl, doctorId, startIso, endIso);
        return sendGetRequestWithTokenReturnsList(null, url);
    }

    public void generateWeeklySchedule(String token, Map<String, Object> scheduleRequest) {
        String url = gatewayUrl + "/api/v1/appointments/slots/generate-weekly";
        restTemplate.exchange(url, HttpMethod.POST, new HttpEntity<>(scheduleRequest, buildHeaders(token)), String.class);
    }

    public List<Map<String, Object>> getMyAppointments(String token) {
        String url = gatewayUrl + "/api/v1/appointments/my";
        return sendGetRequestWithTokenReturnsList(token, url);
    }

    public List<Map<String, Object>> getDoctorAppointments(String token, String doctorId) {
        String url = gatewayUrl + "/api/v1/appointments/doctor/" + doctorId;
        try {
            return sendGetRequestWithTokenReturnsList(token, url);
        } catch (Exception e) {
            return List.of();
        }
    }

    public void bookAppointment(String token, Long slotId, String notes) {
        String url = gatewayUrl + "/api/v1/appointments";
        Map<String, Object> body = new HashMap<>();
        body.put("slotId", slotId);
        body.put("notes", notes);
        restTemplate.exchange(url, HttpMethod.POST, new HttpEntity<>(body, buildHeaders(token)), Map.class);
    }

    public void completeAppointment(String token, Long appointmentId) {
        String url = gatewayUrl + "/api/v1/appointments/" + appointmentId + "/complete";
        restTemplate.exchange(url, HttpMethod.PATCH, new HttpEntity<>(buildHeaders(token)), Map.class);
    }

    public Map<String, Object> getMfaDetails(String token) {
        String url = gatewayUrl + "/api/v1/auth/2fa/status";
        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(buildHeaders(token)),
                    new ParameterizedTypeReference<>() {}
            );
            return response.getBody() != null ? response.getBody() : Map.of(
                    "enabled", false, "factor", "EMAIL_OTP");
        } catch (Exception e) {
            return Map.of("enabled", false, "factor", "EMAIL_OTP");
        }
    }

    public Map<String, Object> enrollTotp(String token) {
        String url = gatewayUrl + "/api/v1/auth/mfa/totp/enroll";
        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                url, HttpMethod.POST, new HttpEntity<>(buildHeaders(token)),
                new ParameterizedTypeReference<>() {}
        );
        return response.getBody();
    }

    public void switchMfaFactor(String token, String factor) {
        String url = gatewayUrl + "/api/v1/auth/mfa/factor";
        restTemplate.exchange(url, HttpMethod.PATCH,
                new HttpEntity<>(Map.of("factor", factor), buildHeaders(token)), Map.class);
    }

    public void update2faStatus(String token, boolean enabled) {
        String url = gatewayUrl + "/api/v1/auth/2fa";
        restTemplate.exchange(url, HttpMethod.PATCH, new HttpEntity<>(
                Map.of("enabled", enabled), buildHeaders(token)), Map.class);
    }

    // ----- Consents & Medical Records -----

    public List<Map<String, Object>> getMyConsents(String token) {
        String url = gatewayUrl + "/api/v1/consents/my";
        return sendGetRequestWithTokenReturnsList(token, url);
    }

    public void setConsent(String token, String doctorId, boolean granted) {
        String url = gatewayUrl + "/api/v1/consents";
        Map<String, Object> body = Map.of("doctorId", doctorId, "granted", granted);
        restTemplate.exchange(url, HttpMethod.POST, new HttpEntity<>(body, buildHeaders(token)), Map.class);
    }

    public List<Map<String, Object>> getMyMedicalRecords(String token) {
        String url = gatewayUrl + "/api/v1/medical-records/my";
        return sendGetRequestWithTokenReturnsList(token, url);
    }

    public List<Map<String, Object>> getPatientHistoryForDoctor(String token, String doctorId, String patientPseudonym) {
        String url = String.format("%s/api/v1/medical-records/patient/%s?doctorId=%s",
                gatewayUrl, patientPseudonym, doctorId);
        return sendGetRequestWithTokenReturnsList(token, url);
    }

    public void createMedicalRecord(String token, String doctorId, String patientPseudonym,
                                    Long appointmentId, String diagnosis, String consultationNotes) {
        String url = gatewayUrl + "/api/v1/medical-records";
        Map<String, Object> body = new HashMap<>();
        body.put("doctorId", doctorId);
        body.put("patientPseudonym", patientPseudonym);
        body.put("appointmentId", appointmentId);
        body.put("diagnosis", diagnosis);
        body.put("consultationNotes", consultationNotes);
        restTemplate.exchange(url, HttpMethod.POST, new HttpEntity<>(body, buildHeaders(token)), Map.class);
    }

    // ----- GDPR & Admin Operations -----

    public void requestDeletionOtp(String token) {
        String url = gatewayUrl + "/api/v1/auth/request-otp";
        restTemplate.exchange(url, HttpMethod.POST, new HttpEntity<>(buildHeaders(token)), Map.class);
    }

    public void eraseMyAccount(String token, String otpCode) {
        String url = gatewayUrl + "/api/v1/auth/account?otp=" + otpCode;
        restTemplate.exchange(url, HttpMethod.DELETE, new HttpEntity<>(buildHeaders(token)), Map.class);
    }

    public void adminCreateDoctor(String token, String email, String tempPassword,
                                  String firstName, String lastName, String specialty, String photoUrl) {
        String authUrl = gatewayUrl + "/api/v1/auth/admin/doctors";
        Map<String, String> authBody = Map.of("email", email, "password", tempPassword);
        ResponseEntity<Map<String, Object>> authResp = restTemplate.exchange(
                authUrl, HttpMethod.POST, new HttpEntity<>(authBody, buildHeaders(token)),
                new ParameterizedTypeReference<>() {}
        );
        String doctorId = String.valueOf(Objects.requireNonNull(authResp.getBody()).get("doctorId"));

        String profileUrl = gatewayUrl + "/api/v1/profiles/doctors";
        Map<String, Object> profileBody = new HashMap<>();
        profileBody.put("doctorId", doctorId);
        profileBody.put("firstName", firstName);
        profileBody.put("lastName", lastName);
        profileBody.put("specialty", specialty);
        profileBody.put("photoUrl", photoUrl);
        restTemplate.exchange(profileUrl, HttpMethod.POST, new HttpEntity<>(profileBody, buildHeaders(token)), Map.class);
    }

    public void adminDeleteUser(String token, String userId, String otpCode) {
        String url = gatewayUrl + "/api/v1/auth/admin/users/" + userId + "?otp=" + otpCode;
        restTemplate.exchange(url, HttpMethod.DELETE, new HttpEntity<>(buildHeaders(token)), Map.class);
    }

    public List<Map<String, Object>> adminGetAllPatients(String token) {
        String url = gatewayUrl + "/api/v1/auth/admin/patients";
        try {
            return sendGetRequestWithTokenReturnsList(token, url);
        } catch (Exception e) {
            return List.of();
        }
    }
}
