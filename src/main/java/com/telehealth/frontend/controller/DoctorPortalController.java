package com.telehealth.frontend.controller;

import com.telehealth.frontend.service.TelehealthApiService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.DayOfWeek;
import java.util.*;

@Controller
@RequestMapping("/doctor")
@RequiredArgsConstructor
public class DoctorPortalController {

    private final TelehealthApiService apiService;

    private String getToken(HttpSession session) {
        return (String) session.getAttribute("jwtToken");
    }

    private String getDoctorId(HttpSession session) {
        Object id = session.getAttribute("userId");
        return id != null ? String.valueOf(id) : null;
    }

    @GetMapping("/dashboard")
    public String upcomingAppointments(HttpSession session, Model model) {
        String token = getToken(session);
        if (token == null) return "redirect:/login";
        String doctorId = getDoctorId(session);

        List<Map<String, Object>> all = apiService.getDoctorAppointments(token, doctorId);
        List<Map<String, Object>> upcoming = all.stream()
                .filter(a -> "PLANNED".equals(String.valueOf(a.get("status"))))
                .sorted(Comparator.comparing(a -> {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> slot = (Map<String, Object>) a.get("timeSlot");
                    return slot != null ? String.valueOf(slot.get("startTime")) : "";
                }))
                .toList();

        model.addAttribute("appointments", upcoming);
        model.addAttribute("doctorId", doctorId);
        return "doctor/dashboard";
    }

    @GetMapping("/schedule")
    public String schedulePage(HttpSession session, Model model) {
        if (getToken(session) == null) return "redirect:/login";
        model.addAttribute("daysOfWeek", Arrays.stream(DayOfWeek.values())
                .map(DayOfWeek::name)
                .toList());
        return "doctor/schedule";
    }

    @PostMapping("/schedule")
    public String generateSchedule(@RequestParam String startDate,
                                   @RequestParam String endDate,
                                   @RequestParam int slotDurationMinutes,
                                   @RequestParam Map<String, String> allParams,
                                   HttpSession session,
                                   RedirectAttributes redirectAttributes) {
        String token = getToken(session);
        if (token == null) return "redirect:/login";
        String doctorId = getDoctorId(session);

        Map<String, Map<String, String>> workingDays = new LinkedHashMap<>();
        for (String day : Arrays.stream(DayOfWeek.values())
                .map(DayOfWeek::name).toList()) {
            if ("true".equals(allParams.get("enabled_" + day))) {
                String start = allParams.get("start_" + day);
                String end = allParams.get("end_" + day);
                if (start != null &&  !start.isBlank() && end != null && !end.isBlank()) {
                    workingDays.put(day, Map.of("startTime", start, "endTime", end));
                }
            }
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("doctorId", doctorId);
        payload.put("startDate", startDate);
        payload.put("endDate", endDate);
        payload.put("slotDurationMinutes", slotDurationMinutes);
        payload.put("workingDays", workingDays);

        try {
            apiService.generateWeeklySchedule(token, payload);
            redirectAttributes.addFlashAttribute("success",
                    "Working schedule generated successfully.");
            return "redirect:/doctor/dashboard";
        } catch (Exception e) {
            String errMsg = apiService.extractErrorMessage(e, "You can add working days only for the future.");
            redirectAttributes.addFlashAttribute("error",
                    "Schedule error: " + errMsg);
            return "redirect:/doctor/schedule";
        }
    }

    @GetMapping("/history")
    public String pastAppointments(HttpSession session, Model model) {
        String token = getToken(session);
        if (token == null) return "redirect:/login";
        String doctorId = getDoctorId(session);

        List<Map<String, Object>> all = apiService.getDoctorAppointments(token, doctorId);
        List<Map<String, Object>> past = all.stream()
                .filter(a -> !"PLANNED".equals(String.valueOf(a.get("status"))))
                .sorted((a, b) -> {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> slotA = (Map<String, Object>) a.get("timeSlot");
                    @SuppressWarnings("unchecked")
                    Map<String, Object> slotB = (Map<String, Object>) b.get("timeSlot");
                    String tA = slotA != null ? String.valueOf(slotA.get("startTime")) : "";
                    String tB = slotB != null ? String.valueOf(slotB.get("startTime")) : "";
                    return tB.compareTo(tA);
                })
                .toList();

        model.addAttribute("appointments", past);
        return "doctor/history";
    }

    @GetMapping("/consultation/{appointmentId}")
    public String videoConsultation(@PathVariable Long appointmentId,
                                    @RequestParam String patientPseudonym,
                                    HttpSession session, Model model) {
        String token = getToken(session);
        if (token == null) return "redirect:/login";
        String doctorId = getDoctorId(session);

        List<Map<String, Object>> history = List.of();
        boolean consentGranted = true;
        try {
            history = apiService.getPatientHistoryForDoctor(token, doctorId, patientPseudonym);
        } catch (Exception e) {
            consentGranted = false;
        }

        model.addAttribute("appointmentId", appointmentId);
        model.addAttribute("patientPseudonym", patientPseudonym);
        model.addAttribute("roomName", "TeleHealth-Consult-" + appointmentId + "-" + patientPseudonym.substring(0, 8));
        model.addAttribute("consentGranted", consentGranted);
        model.addAttribute("history", history);
        return "doctor/consultation";
    }

    @PostMapping("/consultation/{appointmentId}/report")
    public String saveConsultationReport(@PathVariable Long appointmentId,
                                         @RequestParam String patientPseudonym,
                                         @RequestParam String diagnosis,
                                         @RequestParam String consultationNotes,
                                         HttpSession session,
                                         RedirectAttributes redirectAttributes) {
        String token = getToken(session);
        if (token == null) return "redirect:/login";
        String doctorId = getDoctorId(session);

        try {
            apiService.createMedicalRecord(token, doctorId, patientPseudonym,
                    appointmentId, diagnosis, consultationNotes);
            redirectAttributes.addFlashAttribute("success", "Medical report saved.");
            return "redirect:/doctor/dashboard";
        } catch (Exception e) {
            String errMsg = apiService.extractErrorMessage(e, "Bad formatted data or something went wrong.");
            redirectAttributes.addFlashAttribute("error",
                    "Failed to save report: " + errMsg);
            return "redirect:/doctor/consultation/" + appointmentId + "?patientPseudonym=" + patientPseudonym;
        }
    }
}
