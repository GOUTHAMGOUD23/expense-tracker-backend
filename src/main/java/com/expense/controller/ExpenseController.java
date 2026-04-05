package com.expense.controller;

import com.expense.dto.ExpenseDTO;
import com.expense.service.ExpenseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/expenses")
@RequiredArgsConstructor
public class ExpenseController {

    private final ExpenseService expenseService;

    @PostMapping
    public ResponseEntity<ExpenseDTO> create(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody ExpenseDTO dto) {
        return ResponseEntity.ok(expenseService.createExpense(userDetails.getUsername(), dto));
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getAll(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        return ResponseEntity.ok(expenseService.getExpenses(userDetails.getUsername(), page, size, category, start, end));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ExpenseDTO> getById(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id) {
        return ResponseEntity.ok(expenseService.getExpenseById(userDetails.getUsername(), id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ExpenseDTO> update(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id,
            @Valid @RequestBody ExpenseDTO dto) {
        return ResponseEntity.ok(expenseService.updateExpense(userDetails.getUsername(), id, dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> delete(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id) {
        expenseService.deleteExpense(userDetails.getUsername(), id);
        return ResponseEntity.ok(Map.of("message", "Expense deleted successfully"));
    }

    @GetMapping("/recent")
    public ResponseEntity<List<ExpenseDTO>> getRecent(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(defaultValue = "5") int limit) {
        return ResponseEntity.ok(expenseService.getRecentExpenses(userDetails.getUsername(), limit));
    }
}