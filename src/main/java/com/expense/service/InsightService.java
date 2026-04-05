package com.expense.service;

import com.expense.model.Expense;
import com.expense.model.User;
import com.expense.repository.ExpenseRepository;
import com.expense.repository.UserRepository;
import com.expense.util.AiUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class InsightService {

    private final ExpenseRepository expenseRepository;
    private final UserRepository userRepository;
    private final AiUtil aiUtil;

    private static final String SYSTEM_PROMPT = """
            You are a smart personal finance assistant. You analyze spending data and provide:
            1. Clear, actionable insights (not generic advice)
            2. Specific observations based on actual numbers
            3. Practical suggestions to reduce unnecessary spending
            4. Positive reinforcement for good habits
            Keep responses concise, friendly, and specific to the data provided.
            Use bullet points for clarity. Avoid financial jargon.
            """;

    private User getUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
    }

    /**
     * Generate a monthly spending summary with AI insights.
     */
    public Map<String, Object> getMonthlyInsights(String email) {
        User user = getUser(email);
        LocalDate start = LocalDate.now().withDayOfMonth(1);
        LocalDate end = LocalDate.now();

        List<Expense> expenses = expenseRepository.findByUserAndDateBetweenOrderByDateDesc(user, start, end);

        if (expenses.isEmpty()) {
            return Map.of(
                    "insight", "No expenses recorded this month. Start adding expenses to get personalized insights!",
                    "period", start + " to " + end,
                    "total", BigDecimal.ZERO
            );
        }

        String dataContext = buildExpenseSummary(expenses);
        String prompt = "Here is the user's expense data for this month:\n\n" + dataContext +
                "\n\nProvide 3-5 specific insights about their spending patterns, habits, and actionable suggestions.";

        String aiResponse = aiUtil.chat(SYSTEM_PROMPT, prompt);
        BigDecimal total = expenses.stream().map(Expense::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);

        return Map.of(
                "insight", aiResponse,
                "period", start + " to " + end,
                "total", total,
                "expenseCount", expenses.size()
        );
    }

    /**
     * Ask a natural language question about expenses.
     */
    public Map<String, Object> askQuestion(String email, String question) {
        User user = getUser(email);
        LocalDate start = LocalDate.now().minusMonths(3);
        LocalDate end = LocalDate.now();

        List<Expense> expenses = expenseRepository.findByUserAndDateBetweenOrderByDateDesc(user, start, end);
        String dataContext = buildExpenseSummary(expenses);

        String prompt = "User's last 3 months of expenses:\n\n" + dataContext +
                "\n\nUser question: " + question;

        String answer = aiUtil.chat(SYSTEM_PROMPT, prompt);

        return Map.of(
                "question", question,
                "answer", answer
        );
    }

    /**
     * Detect unusual or anomalous spending.
     */
    public Map<String, Object> detectAnomalies(String email) {
        User user = getUser(email);
        LocalDate start = LocalDate.now().minusMonths(3);
        LocalDate end = LocalDate.now();

        List<Expense> expenses = expenseRepository.findByUserAndDateBetweenOrderByDateDesc(user, start, end);

        if (expenses.isEmpty()) {
            return Map.of("anomalies", "Not enough data to detect anomalies.");
        }

        // Statistical anomaly: expenses > mean + 2*stddev
        List<BigDecimal> amounts = expenses.stream().map(Expense::getAmount).toList();
        double mean = amounts.stream().mapToDouble(BigDecimal::doubleValue).average().orElse(0);
        double variance = amounts.stream()
                .mapToDouble(a -> Math.pow(a.doubleValue() - mean, 2))
                .average().orElse(0);
        double stddev = Math.sqrt(variance);
        double threshold = mean + 2 * stddev;

        List<Map<String, Object>> anomalies = expenses.stream()
                .filter(e -> e.getAmount().doubleValue() > threshold)
                .map(e -> Map.<String, Object>of(
                        "id", e.getId(),
                        "title", e.getTitle(),
                        "amount", e.getAmount(),
                        "category", e.getCategory(),
                        "date", e.getDate().toString()
                ))
                .collect(Collectors.toList());

        String aiPrompt = "The user has these statistically unusual expenses (more than 2 standard deviations above mean):\n" +
                anomalies.stream().map(a -> a.get("date") + " - " + a.get("title") + ": $" + a.get("amount") + " (" + a.get("category") + ")")
                        .collect(Collectors.joining("\n")) +
                "\n\nMean spending per transaction: $" + String.format("%.2f", mean) +
                "\n\nExplain if these seem genuinely unusual and give brief advice.";

        String aiComment = anomalies.isEmpty()
                ? "No unusual spending detected. Your expenses look consistent!"
                : aiUtil.chat(SYSTEM_PROMPT, aiPrompt);

        return Map.of(
                "anomalies", anomalies,
                "mean", String.format("%.2f", mean),
                "threshold", String.format("%.2f", threshold),
                "aiComment", aiComment
        );
    }

    /**
     * Suggest a budget based on past spending patterns.
     */
    public Map<String, Object> suggestBudget(String email) {
        User user = getUser(email);
        LocalDate start = LocalDate.now().minusMonths(3);
        LocalDate end = LocalDate.now();

        List<Object[]> categoryTotals = expenseRepository.sumByCategory(user, start, end);

        if (categoryTotals.isEmpty()) {
            return Map.of("suggestion", "Add expenses first to get a personalized budget suggestion.");
        }

        Map<String, BigDecimal> avgByCategory = new LinkedHashMap<>();
        for (Object[] row : categoryTotals) {
            // 3-month data → monthly average
            avgByCategory.put((String) row[0],
                    ((BigDecimal) row[1]).divide(BigDecimal.valueOf(3), 2, java.math.RoundingMode.HALF_UP));
        }

        String dataContext = avgByCategory.entrySet().stream()
                .map(e -> e.getKey() + ": $" + e.getValue() + "/month (avg)")
                .collect(Collectors.joining("\n"));

        String prompt = "Based on this user's average monthly spending by category:\n\n" + dataContext +
                "\n\nSuggest a realistic monthly budget for each category with brief reasoning. " +
                "Identify where they can save and where spending seems appropriate.";

        String suggestion = aiUtil.chat(SYSTEM_PROMPT, prompt);

        return Map.of(
                "currentAverages", avgByCategory,
                "budgetSuggestion", suggestion,
                "basedOnMonths", 3
        );
    }

    // ---- Helper ----

    private String buildExpenseSummary(List<Expense> expenses) {
        BigDecimal total = expenses.stream().map(Expense::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, BigDecimal> byCategory = new LinkedHashMap<>();
        for (Expense e : expenses) {
            byCategory.merge(e.getCategory(), e.getAmount(), BigDecimal::add);
        }

        String categoryBreakdown = byCategory.entrySet().stream()
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                .map(en -> "  - " + en.getKey() + ": $" + en.getValue())
                .collect(Collectors.joining("\n"));

        String topExpenses = expenses.stream()
                .sorted(Comparator.comparing(Expense::getAmount).reversed())
                .limit(5)
                .map(e -> "  - " + e.getDate() + " | " + e.getTitle() + " (" + e.getCategory() + "): $" + e.getAmount())
                .collect(Collectors.joining("\n"));

        return String.format("""
                Total Expenses: $%s
                Number of Transactions: %d
                
                Spending by Category:
                %s
                
                Top 5 Expenses:
                %s
                """, total, expenses.size(), categoryBreakdown, topExpenses);
    }
}