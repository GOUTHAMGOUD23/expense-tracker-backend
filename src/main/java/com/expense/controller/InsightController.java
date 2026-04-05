package com.expense.controller;

import com.expense.service.InsightService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/insights")
@RequiredArgsConstructor
public class InsightController {

    private final InsightService insightService;

    /**
     * GET /api/insights/monthly
     * Returns AI-generated insights for the current month's spending.
     */
    @GetMapping("/monthly")
    public ResponseEntity<Map<String, Object>> getMonthlyInsights(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(insightService.getMonthlyInsights(userDetails.getUsername()));
    }

    /**
     * POST /api/insights/ask
     * Ask a free-form question about your expenses.
     * Body: { "question": "How much did I spend on Food last month?" }
     */
    @PostMapping("/ask")
    public ResponseEntity<Map<String, Object>> askQuestion(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody Map<String, String> body) {
        String question = body.getOrDefault("question", "").trim();
        if (question.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Question cannot be empty"));
        }
        return ResponseEntity.ok(insightService.askQuestion(userDetails.getUsername(), question));
    }

    /**
     * GET /api/insights/anomalies
     * Detect unusually high spending transactions.
     */
    @GetMapping("/anomalies")
    public ResponseEntity<Map<String, Object>> detectAnomalies(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(insightService.detectAnomalies(userDetails.getUsername()));
    }

    /**
     * GET /api/insights/budget
     * Get AI-suggested monthly budget based on spending history.
     */
    @GetMapping("/budget")
    public ResponseEntity<Map<String, Object>> suggestBudget(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(insightService.suggestBudget(userDetails.getUsername()));
    }
}