package com.expense.service;

import com.expense.dto.ExpenseDTO;
import com.expense.model.Expense;
import com.expense.model.User;
import com.expense.repository.ExpenseRepository;
import com.expense.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ExpenseService {

    private final ExpenseRepository expenseRepository;
    private final UserRepository userRepository;

    private User getUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));
    }

    @Transactional
    public ExpenseDTO createExpense(String email, ExpenseDTO dto) {
        User user = getUser(email);
        Expense expense = mapToEntity(dto, user);
        return mapToDTO(expenseRepository.save(expense));
    }

    @Transactional
    public ExpenseDTO updateExpense(String email, Long id, ExpenseDTO dto) {
        User user = getUser(email);
        Expense expense = expenseRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new IllegalArgumentException("Expense not found"));

        expense.setTitle(dto.getTitle());
        expense.setDescription(dto.getDescription());
        expense.setAmount(dto.getAmount());
        expense.setCategory(dto.getCategory());
        expense.setDate(dto.getDate());
        expense.setCurrency(dto.getCurrency() != null ? dto.getCurrency() : "USD");
        expense.setPaymentMethod(dto.getPaymentMethod());
        expense.setReceiptUrl(dto.getReceiptUrl());
        expense.setTags(dto.getTags());

        return mapToDTO(expenseRepository.save(expense));
    }

    @Transactional
    public void deleteExpense(String email, Long id) {
        User user = getUser(email);
        Expense expense = expenseRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new IllegalArgumentException("Expense not found"));
        expenseRepository.delete(expense);
    }

    public ExpenseDTO getExpenseById(String email, Long id) {
        User user = getUser(email);
        Expense expense = expenseRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new IllegalArgumentException("Expense not found"));
        return mapToDTO(expense);
    }

    public Map<String, Object> getExpenses(String email, int page, int size,
                                           String category, LocalDate start, LocalDate end) {
        User user = getUser(email);
        Pageable pageable = PageRequest.of(page, size, Sort.by("date").descending());

        Page<Expense> expensePage = expenseRepository.findByFilters(user, category, start, end, pageable);

        return Map.of(
                "content", expensePage.getContent().stream().map(this::mapToDTO).collect(Collectors.toList()),
                "totalElements", expensePage.getTotalElements(),
                "totalPages", expensePage.getTotalPages(),
                "currentPage", expensePage.getNumber(),
                "size", expensePage.getSize()
        );
    }

    public List<ExpenseDTO> getRecentExpenses(String email, int limit) {
        User user = getUser(email);
        Pageable pageable = PageRequest.of(0, limit, Sort.by("date").descending());
        return expenseRepository.findByUserOrderByDateDesc(user, pageable)
                .getContent().stream().map(this::mapToDTO).collect(Collectors.toList());
    }

    // ---- Mappers ----

    public Expense mapToEntity(ExpenseDTO dto, User user) {
        return Expense.builder()
                .user(user)
                .title(dto.getTitle())
                .description(dto.getDescription())
                .amount(dto.getAmount())
                .category(dto.getCategory())
                .date(dto.getDate())
                .currency(dto.getCurrency() != null ? dto.getCurrency() : "USD")
                .paymentMethod(dto.getPaymentMethod())
                .receiptUrl(dto.getReceiptUrl())
                .tags(dto.getTags())
                .build();
    }

    public ExpenseDTO mapToDTO(Expense expense) {
        ExpenseDTO dto = new ExpenseDTO();
        dto.setId(expense.getId());
        dto.setTitle(expense.getTitle());
        dto.setDescription(expense.getDescription());
        dto.setAmount(expense.getAmount());
        dto.setCategory(expense.getCategory());
        dto.setDate(expense.getDate());
        dto.setCurrency(expense.getCurrency());
        dto.setPaymentMethod(expense.getPaymentMethod());
        dto.setReceiptUrl(expense.getReceiptUrl());
        dto.setTags(expense.getTags());
        return dto;
    }
}