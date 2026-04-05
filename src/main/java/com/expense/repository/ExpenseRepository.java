package com.expense.repository;

import com.expense.model.Expense;
import com.expense.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public interface ExpenseRepository extends JpaRepository<Expense, Long> {

    Page<Expense> findByUserOrderByDateDesc(User user, Pageable pageable);

    List<Expense> findByUserAndDateBetweenOrderByDateDesc(User user, LocalDate start, LocalDate end);

    List<Expense> findByUserAndCategoryOrderByDateDesc(User user, String category);

    Optional<Expense> findByIdAndUser(Long id, User user);

    @Query("SELECT e.category, SUM(e.amount) FROM Expense e WHERE e.user = :user AND e.date BETWEEN :start AND :end GROUP BY e.category")
    List<Object[]> sumByCategory(@Param("user") User user, @Param("start") LocalDate start, @Param("end") LocalDate end);

    @Query("SELECT SUM(e.amount) FROM Expense e WHERE e.user = :user AND e.date BETWEEN :start AND :end")
    BigDecimal sumTotalByUserAndDateRange(@Param("user") User user, @Param("start") LocalDate start, @Param("end") LocalDate end);

    @Query(value = "SELECT DATE_FORMAT(e.date, '%Y-%m') as month, SUM(e.amount) as total FROM expenses e WHERE e.user_id = :userId GROUP BY DATE_FORMAT(e.date, '%Y-%m') ORDER BY month DESC", nativeQuery = true)
    List<Object[]> monthlyTotals(@Param("userId") Long userId);

    @Query("SELECT e FROM Expense e WHERE e.user = :user AND (:category IS NULL OR e.category = :category) AND (:start IS NULL OR e.date >= :start) AND (:end IS NULL OR e.date <= :end) ORDER BY e.date DESC")
    Page<Expense> findByFilters(
            @Param("user") User user,
            @Param("category") String category,
            @Param("start") LocalDate start,
            @Param("end") LocalDate end,
            Pageable pageable
    );

    long countByUser(User user);

    List<Expense> findTop5ByUserOrderByAmountDesc(User user);
}