package com.telehealth.frontend.controller;

import com.telehealth.frontend.service.TelehealthApiService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminPortalController {

    private final TelehealthApiService apiService;

    private String getToken(HttpSession session) {
        return (String) session.getAttribute("jwtToken");
    }

    @GetMapping("/dashboard")
    public String doctorsManagement(HttpSession session, Model model) {
        String token = getToken(session);
        if (token == null) return "redirect:/login";

        model.addAttribute("doctors", apiService.getAllDoctors(null));
        return "admin/dashboard";
    }

    @PostMapping("/doctors/create")
    public String createDoctor(@RequestParam String email,
                               @RequestParam String tempPassword,
                               @RequestParam String firstName,
                               @RequestParam String lastName,
                               @RequestParam String specialty,
                               @RequestParam(required = false) String photoUrl,
                               HttpSession session,
                               RedirectAttributes redirectAttributes) {
        String token = getToken(session);
        if (token == null) return "redirect:/login";
        try {
            String safePhoto = (photoUrl != null && !photoUrl.isBlank())
                    ? photoUrl
                    : "https://placehold.co/150x150/2563eb/white?text=" +
                    firstName.charAt(0) + lastName.charAt(0);
            apiService.adminCreateDoctor(token, email, tempPassword, firstName, lastName, specialty, safePhoto);
            redirectAttributes.addFlashAttribute("success",
                    "Doctor account and public profile created.");
        } catch (Exception e) {
            String errMsg = apiService.extractErrorMessage(e, "Bad formatted data or email is already taken.");
            redirectAttributes.addFlashAttribute("error",
                    "Failed to create doctor: " + errMsg);
        }
        return "redirect:/admin/dashboard";
    }

    @GetMapping("/patients")
    public String patientsManagement(HttpSession session, Model model) {
        String token = getToken(session);
        if (token == null) return "redirect:/login";

        model.addAttribute("patients", apiService.adminGetAllPatients(token));
        return "admin/patients";
    }

    @GetMapping("/confirm-delete")
    public String confirmDeletePage(@RequestParam String targetType,
                                    @RequestParam String targetId,
                                    @RequestParam String targetLabel,
                                    HttpSession session, Model model) {
        String token = getToken(session);
        if (token == null) return "redirect:/login";
        try {
            apiService.requestDeletionOtp(token);
        } catch (Exception ignored) {}
        model.addAttribute("targetType", targetType);
        model.addAttribute("targetId", targetId);
        model.addAttribute("targetLabel", targetLabel);
        return "admin/confirm-delete";
    }

    @PostMapping("/confirm-delete")
    public String executeDelete(@RequestParam String targetType,
                                @RequestParam String targetId,
                                @RequestParam String otpCode,
                                HttpSession session,
                                RedirectAttributes redirectAttributes) {
        String token = getToken(session);
        if (token == null) return "redirect:/login";
        try {
            apiService.adminDeleteUser(token, targetId, otpCode);
            redirectAttributes.addFlashAttribute("success",
                    "User permanently removed and keys shredded.");
        } catch (Exception e) {
            String errMsg = apiService.extractErrorMessage(e, "Invalid or expired verification code [FALLBACK].");
            redirectAttributes.addFlashAttribute("error",
                    "Deletion failed: " + errMsg);
        }
        return "DOCTOR".equals(targetType) ? "redirect:/admin/dashboard" : "redirect:/admin/patients";
    }
}
