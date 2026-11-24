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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/patient")
@RequiredArgsConstructor
public class PatientPortalController {

    private final TelehealthApiService apiService;

    private String getToken(HttpSession session) {
        return (String) session.getAttribute("jwtToken");
    }

    @GetMapping("/dashboard")
    public String showDoctorCatalog(@RequestParam(required = false) String specialty,
                                    HttpSession session, Model model) {
        String token = getToken(session);
        if (token == null) return "redirect:/login";

        List<Map<String, Object>> doctors = apiService.getAllDoctors(specialty);
        Set<String> specialties = apiService.getAllSpecialties();

        model.addAttribute("doctors", doctors);
        model.addAttribute("specialties", specialties);
        model.addAttribute("selectedSpecialty", specialty);
        model.addAttribute("patientProfile", apiService.getMyPatientProfile(token));
        model.addAttribute("is2faEnabled", apiService.get2faStatus(token));
        return "patient/dashboard";
    }

    @PostMapping("/2fa")
    public String toggle2fa(@RequestParam(defaultValue = "false") boolean enabled,
                            HttpSession session, RedirectAttributes redirectAttributes) {
        String token = getToken(session);
        if (token == null) return "redirect:/login";
        try {
            apiService.update2faStatus(token, enabled);
            session.invalidate();
            redirectAttributes.addFlashAttribute("registered", true);
            return "redirect:/login";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error",
                    "Failed to update 2FA: " + apiService.extractErrorMessage(e, "Error"));
        }
        return "redirect:/patient/dashboard";
    }

    @PostMapping("/profile")
    public String updateProfile(@RequestParam String firstName,
                                @RequestParam String lastName,
                                HttpSession session,
                                RedirectAttributes redirectAttributes) {
        String token = getToken(session);
        if (token == null) return "redirect:/login";
        try {
            apiService.updateMyPatientProfile(token, firstName, lastName);
            redirectAttributes.addFlashAttribute("success", "Profile updated successfully!");
        } catch (Exception e) {
            String errMsg = apiService.extractErrorMessage(e, "");
            redirectAttributes.addFlashAttribute("error", "Failed to update profile: " + errMsg);
        }
        return "redirect:/patient/dashboard";
    }

    @GetMapping("/book")
    public String bookWizard(@RequestParam(required = false) String specialty,
                             @RequestParam(required = false) String doctorId,
                             @RequestParam(required = false) String date,
                             HttpSession session, Model model) {
        if (getToken(session) == null) return "redirect:/login";

        List<Map<String, Object>> allDoctors = apiService.getAllDoctors(null);
        Set<String> specialties = apiService.getAllSpecialties();

        List<Map<String, Object>> filteredDoctors = (specialty != null && !specialty.isBlank())
                ? apiService.getAllDoctors(specialty)
                : allDoctors;

        LocalDate selectedDate = (date != null && !date.isBlank()) ? LocalDate.parse(date) : LocalDate.now();
        List<Map<String, Object>> slots = List.of();
        if (doctorId != null && !doctorId.isBlank()) {
            LocalDateTime start = selectedDate.atStartOfDay();
            LocalDateTime end = selectedDate.plusDays(7).atTime(23, 59, 59);
            slots = apiService.getAvailableSlots(doctorId, start.toString(), end.toString());
        }

        model.addAttribute("specialties", specialties);
        model.addAttribute("selectedSpecialty", specialty);
        model.addAttribute("doctors", filteredDoctors);
        model.addAttribute("selectedDoctorId", doctorId);
        model.addAttribute("selectedDate", selectedDate.toString());
        model.addAttribute("slots", slots);
        return "patient/book";
    }

    @PostMapping("/book")
    public String confirmBooking(@RequestParam Long slotId,
                                 @RequestParam(required = false) String notes,
                                 HttpSession session,
                                 RedirectAttributes redirectAttributes) {
        String token = getToken(session);
        if (token == null) return "redirect:/login";
        try {
            apiService.bookAppointment(token, slotId, notes);
            redirectAttributes.addFlashAttribute("success", "Appointment booked successfully!");
            return "redirect:/patient/appointments";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Booking failed: " + e.getMessage());
            return "redirect:/patient/book";
        }
    }

    @GetMapping("/appointments")
    public String myAppointments(HttpSession session, Model model) {
        String token = getToken(session);
        if (token == null) return "redirect:/login";

        LocalDateTime now = LocalDateTime.now();
        List<Map<String, Object>> appointments = apiService.getMyAppointments(token).stream()
                .map(a -> {
                    Map<String, Object> copy = new HashMap<>(a);
                    @SuppressWarnings("unchecked")
                    Map<String, Object> slot = (Map<String, Object>) a.get("timeSlot");
                    boolean canJoin = false;
                    if (slot != null && slot.get("startTime") != null && slot.get("endTime") != null) {
                        LocalDateTime start = LocalDateTime.parse(String.valueOf(slot.get("startTime")));
                        LocalDateTime end = LocalDateTime.parse(String.valueOf(slot.get("endTime")));
                        canJoin = !now.isBefore(start.minusMinutes(5)) && !now.isAfter(end.plusMinutes(15));
                    }
                    copy.put("canJoin", canJoin);
                    return copy;
                }).toList();
        Map<String, Map<String, Object>> doctorMap = apiService.getDoctorsMap();

        model.addAttribute("appointments", appointments);
        model.addAttribute("doctorMap", doctorMap);
        return "patient/appointments";
    }

    @GetMapping("/medical-history")
    public String medicalHistoryAndConsents(HttpSession session, Model model) {
        String token = getToken(session);
        if (token == null) return "redirect:/login";

        List<Map<String, Object>> records = apiService.getMyMedicalRecords(token);
        List<Map<String, Object>> doctors = apiService.getAllDoctors(null);
        List<Map<String, Object>> consents = apiService.getMyConsents(token);

        Set<String> consentedDoctorIds = consents.stream()
                .filter(c -> Boolean.TRUE.equals(c.get("granted")))
                .map(c -> String.valueOf(c.get("doctorId")))
                .collect(Collectors.toSet());

        Map<String, Map<String, Object>> doctorMap = apiService.getDoctorsMap();

        model.addAttribute("records", records);
        model.addAttribute("doctors", doctors);
        model.addAttribute("doctorMap", doctorMap);
        model.addAttribute("consentedDoctorIds", consentedDoctorIds);
        return "patient/medical-history";
    }

    @PostMapping("/consents")
    public String toggleConsent(@RequestParam String doctorId,
                                @RequestParam boolean granted,
                                HttpSession session,
                                RedirectAttributes redirectAttributes) {
        String token = getToken(session);
        if (token == null) return "redirect:/login";
        try {
            apiService.setConsent(token, doctorId, granted);
            redirectAttributes.addFlashAttribute("success", granted ?
                    "Medical history access granted to doctor." :
                    "Medical history access revoked.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Failed to update consent: " + e.getMessage());
        }
        return "redirect:/patient/medical-history";
    }

    @GetMapping("/erase-data")
    public String eraseDataConfirmPage(HttpSession session) {
        String token = getToken(session);
        if (token == null) return "redirect:/login";
        try {
            apiService.requestDeletionOtp(token);
        } catch (Exception ignored) {}
        return "patient/erase-confirm";
    }

    @PostMapping("/erase-data")
    public String executeCryptoShredding(@RequestParam String code,
                                         HttpSession session,
                                         RedirectAttributes redirectAttributes) {
        String token = getToken(session);
        if (token == null) return "redirect:/login";
        try {
            apiService.eraseMyAccount(token, code);
            session.invalidate();
            redirectAttributes.addFlashAttribute("success",
                    "All personal data has been cryptographically shredded.");
            return "redirect:/login";
        } catch (Exception e) {
            String errMsg = apiService.extractErrorMessage(e, "Invalid verification code.");
            redirectAttributes.addFlashAttribute("error",
                    "Verification failed or erasure error: " + errMsg);
            return "redirect:/patient/erase-data";
        }
    }
}
