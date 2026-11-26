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

import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/security")
@RequiredArgsConstructor
public class SecuritySettingsController {

    private final TelehealthApiService apiService;

    private String resolveHomeUrl(HttpSession session) {
        @SuppressWarnings("unchecked")
        List<String> roles = (List<String>) session.getAttribute("userRoles");
        if (roles != null && roles.contains("ROLE_ADMIN")) return "/admin/dashboard";
        if (roles != null && roles.contains("ROLE_DOCTOR")) return "/doctor/dashboard";
        return "/patient/dashboard";
    }

    @GetMapping("/mfa")
    public String mfaSettingsPage(HttpSession session, Model model) {
        String token = (String) session.getAttribute("jwtToken");
        if (token == null) return "redirect:/login";

        Map<String, Object> mfaDetails = apiService.getMfaDetails(token);
        model.addAttribute("is2faEnabled", Boolean.TRUE.equals(mfaDetails.get("enabled")));
        model.addAttribute("mfaFactor", mfaDetails.get("factor"));
        model.addAttribute("homeUrl", resolveHomeUrl(session));
        return "security/mfa-settings";
    }

    @PostMapping("/mfa/totp/enroll")
    public String enrollTotp(HttpSession session, RedirectAttributes redirectAttributes) {
        String token = (String) session.getAttribute("jwtToken");
        if (token == null) return "redirect:/login";
        try {
            Map<String, Object> enrollment = apiService.enrollTotp(token);
            redirectAttributes.addFlashAttribute("totpSecret", enrollment.get("secret"));
            redirectAttributes.addFlashAttribute("totpUri", enrollment.get("otpAuthUri"));
            redirectAttributes.addFlashAttribute("success",
                    "Scan this QR code in Google/Microsoft Authenticator before signing out.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error",
                    "failed to enroll TOTP: " + apiService.extractErrorMessage(e, "Error"));
        }
        return "redirect:/security/mfa";
    }

    @PostMapping("/mfa/factor")
    public String switchFactor(@RequestParam String factor, HttpSession session,
                               RedirectAttributes redirectAttributes) {
        String token = (String) session.getAttribute("jwtToken");
        if (token == null) return "redirect:/login";
        try {
            apiService.switchMfaFactor(token, factor);
            session.invalidate();
            redirectAttributes.addFlashAttribute("registered", true);
            return "redirect:/login";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error",
                    "Failed to switch MFA factor: " + apiService.extractErrorMessage(e, "Error"));
            return "redirect:/security/mfa";
        }
    }
}
