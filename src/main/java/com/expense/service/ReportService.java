package com.expense.service;

import com.expense.model.Expense;
import com.expense.model.User;
import com.expense.repository.ExpenseRepository;
import com.expense.repository.UserRepository;
import com.itextpdf.text.*;
import com.itextpdf.text.pdf.*;
import com.opencsv.CSVWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.io.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReportService {

    private final ExpenseRepository expenseRepository;
    private final UserRepository userRepository;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private User getUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
    }

    // ---- Summary Statistics ----

    public Map<String, Object> getSummary(String email, LocalDate start, LocalDate end) {
        User user = getUser(email);

        if (start == null) start = LocalDate.now().withDayOfMonth(1);
        if (end == null) end = LocalDate.now();

        BigDecimal total = expenseRepository.sumTotalByUserAndDateRange(user, start, end);
        if (total == null) total = BigDecimal.ZERO;

        List<Object[]> categoryTotals = expenseRepository.sumByCategory(user, start, end);
        Map<String, BigDecimal> byCategory = new LinkedHashMap<>();
        for (Object[] row : categoryTotals) {
            byCategory.put((String) row[0], (BigDecimal) row[1]);
        }

        List<Object[]> monthly = expenseRepository.monthlyTotals(user.getId());
        Map<String, BigDecimal> monthlyMap = new LinkedHashMap<>();
        for (Object[] row : monthly) {
            monthlyMap.put((String) row[0], (BigDecimal) row[1]);
        }

        String topCategory = byCategory.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("N/A");

        long count = expenseRepository.countByUser(user);
        BigDecimal avg = count > 0 ? total.divide(BigDecimal.valueOf(count), 2, java.math.RoundingMode.HALF_UP) : BigDecimal.ZERO;

        return Map.of(
                "total", total,
                "count", count,
                "average", avg,
                "topCategory", topCategory,
                "byCategory", byCategory,
                "monthly", monthlyMap,
                "period", Map.of("start", start.toString(), "end", end.toString())
        );
    }

    // ---- CSV Export ----

    public byte[] exportCsv(String email, LocalDate start, LocalDate end) throws IOException {
        User user = getUser(email);

        if (start == null) start = LocalDate.now().minusMonths(1);
        if (end == null) end = LocalDate.now();

        List<Expense> expenses = expenseRepository.findByUserAndDateBetweenOrderByDateDesc(user, start, end);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (CSVWriter writer = new CSVWriter(new OutputStreamWriter(out))) {
            writer.writeNext(new String[]{"Date", "Title", "Category", "Amount", "Currency", "Payment Method", "Tags", "Description"});
            for (Expense e : expenses) {
                writer.writeNext(new String[]{
                        e.getDate().format(DATE_FMT),
                        e.getTitle(),
                        e.getCategory(),
                        e.getAmount().toString(),
                        e.getCurrency(),
                        e.getPaymentMethod() != null ? e.getPaymentMethod() : "",
                        e.getTags() != null ? e.getTags() : "",
                        e.getDescription() != null ? e.getDescription() : ""
                });
            }
        }
        return out.toByteArray();
    }

    // ---- PDF Export ----

    public byte[] exportPdf(String email, LocalDate start, LocalDate end) throws Exception {
        User user = getUser(email);

        if (start == null) start = LocalDate.now().minusMonths(1);
        if (end == null) end = LocalDate.now();

        List<Expense> expenses = expenseRepository.findByUserAndDateBetweenOrderByDateDesc(user, start, end);
        BigDecimal total = expenses.stream()
                .map(Expense::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Document document = new Document(PageSize.A4, 40, 40, 50, 50);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PdfWriter.getInstance(document, out);
        document.open();

        // Title
        Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, BaseColor.DARK_GRAY);
        Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, BaseColor.WHITE);
        Font cellFont = FontFactory.getFont(FontFactory.HELVETICA, 9, BaseColor.DARK_GRAY);
        Font subFont = FontFactory.getFont(FontFactory.HELVETICA, 11, BaseColor.GRAY);

        Paragraph title = new Paragraph("Expense Report", titleFont);
        title.setAlignment(Element.ALIGN_CENTER);
        document.add(title);

        Paragraph period = new Paragraph("Period: " + start + "  to  " + end, subFont);
        period.setAlignment(Element.ALIGN_CENTER);
        period.setSpacingAfter(10);
        document.add(period);

        Paragraph totalPara = new Paragraph("Total Spent: " + expenses.get(0).getCurrency() + " " + total,
                FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, new BaseColor(33, 150, 83)));
        totalPara.setAlignment(Element.ALIGN_RIGHT);
        totalPara.setSpacingAfter(15);
        document.add(totalPara);

        // Table
        PdfPTable table = new PdfPTable(5);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{2f, 3f, 2f, 1.5f, 2f});

        String[] headers = {"Date", "Title", "Category", "Amount", "Payment"};
        for (String h : headers) {
            PdfPCell cell = new PdfPCell(new Phrase(h, headerFont));
            cell.setBackgroundColor(new BaseColor(63, 81, 181));
            cell.setPadding(6);
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            table.addCell(cell);
        }

        boolean alt = false;
        for (Expense e : expenses) {
            BaseColor bg = alt ? new BaseColor(245, 245, 250) : BaseColor.WHITE;
            addCell(table, e.getDate().format(DATE_FMT), cellFont, bg);
            addCell(table, e.getTitle(), cellFont, bg);
            addCell(table, e.getCategory(), cellFont, bg);
            addCell(table, e.getCurrency() + " " + e.getAmount(), cellFont, bg);
            addCell(table, e.getPaymentMethod() != null ? e.getPaymentMethod() : "-", cellFont, bg);
            alt = !alt;
        }

        document.add(table);
        document.close();
        return out.toByteArray();
    }

    private void addCell(PdfPTable table, String text, Font font, BaseColor bg) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setBackgroundColor(bg);
        cell.setPadding(5);
        table.addCell(cell);
    }
}