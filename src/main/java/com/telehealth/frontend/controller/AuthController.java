package com.telehealth.frontend.controller;

import com.telehealth.frontend.service.AuthClientService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.HttpClientErrorException;

import java.util.List;
import java.util.Map;

@Controller
@RequiredArgsConstructor
public class AuthController {

    private final AuthClientService authClientService;

    @GetMapping("/")
    public String index() {
        return "redirect:/login";
    }

    @GetMapping("/logout")
    public String logout(HttpSession session) {
        session.invalidate();
        return "redirect:/login";
    }

    private String redirectByRole(HttpSession session) {
        List<String> roles = (List<String>) session.getAttribute("userRoles");
        if (roles != null) {
            if (roles.contains("ROLE_ADMIN")) {
                return "redirect:/admin/dashboard";
            }
            if (roles.contains("ROLE_DOCTOR")) {
                return "redirect:/doctor/dashboard";
            }
        }
        return "redirect:/patient/dashboard";
    }

    @GetMapping("/dashboard")
    public String dashboardRedirect(HttpSession session) {
        if (session.getAttribute("jwtToken") == null) {
            return "redirect:/login";
        }
        return redirectByRole(session);
    }

    @GetMapping("/login")
    public String loginPage() {
        return "login";
    }

    @PostMapping("/login")
    public String handleLogin(@RequestParam String email,
                              @RequestParam String password,
                              HttpSession session,
                              Model model) {
        try {
            Map<String, Object> result = authClientService.login(email, password);

            if (Boolean.TRUE.equals(result.get("requires2fa"))) {
                session.setAttribute("pendingEmail", email);
                return "redirect:/verify-2fa";
            }

            session.setAttribute("jwtToken", result.get("token"));
            session.setAttribute("userRoles", result.get("roles"));
            session.setAttribute("userId", result.get("id"));
            return redirectByRole(session);
        } catch (HttpClientErrorException.Unauthorized e) {
            model.addAttribute("error",
                    "Authorization error: email or password is incorrect.");
            return "login";
        } catch (Exception e) {
            model.addAttribute("error",
                    "Authorization error: unable to connect to authentication service.");
            return "login";
        }
    }

    @GetMapping("/verify-2fa")
    public String verify2faPage(HttpSession session, Model model) {
        String email = (String) session.getAttribute("pendingEmail");
        if (email == null) {
            return "redirect:/login";
        }
        model.addAttribute("email", email);
        return "verify-2fa";
    }

    @PostMapping("/verify-2fa")
    public String handleVerify2fa(@RequestParam String code,
                                  HttpSession session,
                                  Model model) {
        String email = (String) session.getAttribute("pendingEmail");
        if (email == null) {
            return "redirect:/login";
        }

        try {
            Map<String, Object> result = authClientService.verify2fa(email, code);
            session.setAttribute("jwtToken", result.get("token"));
            session.setAttribute("userRoles", result.get("roles"));
            session.setAttribute("userId", result.get("id"));
            session.removeAttribute("pendingEmail");
            return redirectByRole(session);
        } catch (Exception e) {
            model.addAttribute("error", "Invalid or expired verification code.");
            model.addAttribute("email", email);
            return "verify-2fa";
        }
    }

    @GetMapping("/register")
    public String registerPage() {
        return "register";
    }

    @PostMapping("/register")
    public String handleRegister(@RequestParam String email,
                                 @RequestParam String password,
                                 @RequestParam(defaultValue = "false") boolean enable2fa,
                                 Model model) {
        try {
            authClientService.register(email, password, enable2fa);
            return "redirect:/login?registered=true";
        } catch (HttpClientErrorException e) {
            model.addAttribute("error",
                    "Registration error: email is already taken or invalid data.");
            return "register";
        } catch (Exception e) {
            model.addAttribute("error",
                    "Registration error: service unavailable.");
            return "register";
        }
    }
}
