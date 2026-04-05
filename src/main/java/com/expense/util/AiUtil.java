package com.expense.util;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Multi-provider AI utility.
 *
 * Switch via application.properties:
 *   ai.provider=ollama      → uses Ollama running locally (FREE, no API key)
 *   ai.provider=openai      → uses OpenAI API
 *   ai.provider=anthropic   → uses Anthropic Claude API
 *
 * Ollama setup:
 *   1. Install Ollama: https://ollama.com/download
 *   2. Pull a model: ollama pull llama3.2
 *      (other good options: mistral, phi3, gemma2, qwen2.5)
 *   3. Ollama runs automatically on http://localhost:11434
 *   4. Set ai.provider=ollama and ai.ollama.model=llama3.2 in application.properties
 */
@Component
@Slf4j
public class AiUtil {

    // ── Provider selector ─────────────────────────────────────────────────────
    @Value("${ai.provider:ollama}")
    private String provider;

    // ── Ollama config (local, free, no API key needed) ────────────────────────
    @Value("${ai.ollama.base-url:http://localhost:11434}")
    private String ollamaBaseUrl;

    @Value("${ai.ollama.model:llama3.2}")
    private String ollamaModel;

    // ── OpenAI config ────────────────────────────────────────────────────────
    @Value("${ai.openai.api-key:}")
    private String openaiApiKey;

    @Value("${ai.openai.model:gpt-4o-mini}")
    private String openaiModel;

    @Value("${ai.openai.base-url:https://api.openai.com/v1}")
    private String openaiBaseUrl;

    // ── Anthropic config ─────────────────────────────────────────────────────
    @Value("${ai.anthropic.api-key:}")
    private String anthropicApiKey;

    @Value("${ai.anthropic.model:claude-sonnet-4-20250514}")
    private String anthropicModel;

    @Value("${ai.anthropic.base-url:https://api.anthropic.com/v1}")
    private String anthropicBaseUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    // ── Startup validation ────────────────────────────────────────────────────
    @PostConstruct
    public void validateConfig() {
        log.info("╔══════════════════════════════════════════════════╗");
        log.info("║  AiUtil — provider = [{}]", provider.toUpperCase());

        switch (provider.toLowerCase()) {
            case "ollama" -> {
                log.info("║  base-url : {}", ollamaBaseUrl);
                log.info("║  model    : {}", ollamaModel);
                log.info("║  api-key  : Not required (local)");
                log.info("║");
                log.info("║  Ollama setup (if not done yet):");
                log.info("║    1. Download: https://ollama.com/download");
                log.info("║    2. Run: ollama pull {}", ollamaModel);
                log.info("║    3. Ollama auto-starts at http://localhost:11434");
            }
            case "openai" -> {
                boolean ok = openaiApiKey != null && openaiApiKey.startsWith("sk-") && openaiApiKey.length() > 20;
                log.info("║  base-url : {}", openaiBaseUrl);
                log.info("║  model    : {}", openaiModel);
                log.info("║  api-key  : {}", ok ? "OK (sk-...)" : "INVALID — must start with sk-");
                if (!ok) log.warn("║  >>> Set ai.openai.api-key=sk-xxx in application.properties <<<");
            }
            case "anthropic" -> {
                boolean ok = anthropicApiKey != null && anthropicApiKey.length() > 20;
                log.info("║  base-url : {}", anthropicBaseUrl);
                log.info("║  model    : {}", anthropicModel);
                log.info("║  api-key  : {}", ok ? "OK" : "MISSING");
            }
            default -> log.warn("║  Unknown provider '{}' — valid: ollama, openai, anthropic", provider);
        }
        log.info("╚══════════════════════════════════════════════════╝");
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public String chat(String systemPrompt, String userMessage) {
        try {
            return switch (provider.toLowerCase()) {
                case "ollama"    -> callOllama(systemPrompt, userMessage);
                case "openai"    -> callOpenAI(systemPrompt, userMessage);
                case "anthropic" -> callAnthropic(systemPrompt, userMessage);
                default          -> {
                    log.error("Unknown AI provider: {}", provider);
                    yield "AI provider not configured.";
                }
            };
        } catch (ResourceAccessException e) {
            // Connection refused — Ollama not running
            if (provider.equalsIgnoreCase("ollama")) {
                log.error("Cannot connect to Ollama at {}. Is Ollama running? Start it with: ollama serve", ollamaBaseUrl);
                return "[Ollama not running] Please start Ollama: open a terminal and run 'ollama serve', then try again.";
            }
            log.error("AI connection failed: {}", e.getMessage());
            return "AI service is unreachable. Please try again later.";
        } catch (HttpClientErrorException e) {
            log.error("AI HTTP {} — body: {}", e.getStatusCode(), e.getResponseBodyAsString());
            return "[AI Error " + e.getStatusCode().value() + "] " + extractMessage(e.getResponseBodyAsString());
        } catch (Exception e) {
            log.error("AI call failed (provider={}): {}", provider, e.getMessage(), e);
            return "Unable to generate AI insight at this time.";
        }
    }

    public String ask(String userMessage) {
        return chat("You are a helpful personal finance assistant.", userMessage);
    }

    // ── Ollama (local, free) ──────────────────────────────────────────────────

    private String callOllama(String systemPrompt, String userMessage) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        // Ollama supports two endpoints:
        //   /api/chat  — multi-turn with roles (preferred)
        //   /api/generate — single prompt (fallback)
        // Using /api/chat for better system prompt support

        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system",  "content", systemPrompt));
        messages.add(Map.of("role", "user",    "content", userMessage));

        Map<String, Object> body = new HashMap<>();
        body.put("model",    ollamaModel);
        body.put("messages", messages);
        body.put("stream",   false);  // IMPORTANT: must be false to get a single JSON response

        log.info("Calling Ollama: model={} url={}", ollamaModel, ollamaBaseUrl);

        ResponseEntity<Map> response = restTemplate.postForEntity(
                ollamaBaseUrl + "/api/chat",
                new HttpEntity<>(body, headers),
                Map.class);

        log.info("Ollama response status: {}", response.getStatusCode());

        if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
            // Response shape: { "message": { "role": "assistant", "content": "..." }, "done": true }
            Map<String, Object> message = (Map<String, Object>) response.getBody().get("message");
            if (message != null) {
                String content = (String) message.get("content");
                if (content != null && !content.isBlank()) {
                    log.info("Ollama reply: {} chars", content.length());
                    return content.trim();
                }
            }
        }

        log.warn("Ollama unexpected response body: {}", response.getBody());
        return "Unable to generate AI insight at this time.";
    }

    // ── OpenAI ────────────────────────────────────────────────────────────────

    private String callOpenAI(String systemPrompt, String userMessage) {
        if (openaiApiKey == null || openaiApiKey.isBlank() || openaiApiKey.equals("YOUR_OPENAI_API_KEY")) {
            return "[Config Error] Set ai.openai.api-key in application.properties";
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(openaiApiKey.trim());

        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", systemPrompt));
        messages.add(Map.of("role", "user",   "content", userMessage));

        Map<String, Object> body = new HashMap<>();
        body.put("model",      openaiModel);
        body.put("max_tokens", 1024);
        body.put("messages",   messages);

        log.info("Calling OpenAI: model={}", openaiModel);
        ResponseEntity<Map> response = restTemplate.postForEntity(
                openaiBaseUrl + "/chat/completions",
                new HttpEntity<>(body, headers),
                Map.class);

        if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
            List<Map<String, Object>> choices = (List<Map<String, Object>>) response.getBody().get("choices");
            if (choices != null && !choices.isEmpty()) {
                Map<String, Object> msg = (Map<String, Object>) choices.get(0).get("message");
                if (msg != null) return (String) msg.get("content");
            }
        }
        return "Unable to generate AI insight at this time.";
    }

    // ── Anthropic (Claude) ────────────────────────────────────────────────────

    private String callAnthropic(String systemPrompt, String userMessage) {
        if (anthropicApiKey == null || anthropicApiKey.isBlank() || anthropicApiKey.equals("YOUR_ANTHROPIC_API_KEY")) {
            return "[Config Error] Set ai.anthropic.api-key in application.properties";
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-api-key",        anthropicApiKey.trim());
        headers.set("anthropic-version", "2023-06-01");

        Map<String, Object> body = new HashMap<>();
        body.put("model",      anthropicModel);
        body.put("max_tokens", 1024);
        body.put("system",     systemPrompt);
        body.put("messages",   List.of(Map.of("role", "user", "content", userMessage)));

        log.info("Calling Anthropic: model={}", anthropicModel);
        ResponseEntity<Map> response = restTemplate.postForEntity(
                anthropicBaseUrl + "/messages",
                new HttpEntity<>(body, headers),
                Map.class);

        if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
            List<Map<String, Object>> content = (List<Map<String, Object>>) response.getBody().get("content");
            if (content != null && !content.isEmpty()) {
                return (String) content.get(0).get("text");
            }
        }
        return "Unable to generate AI insight at this time.";
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String extractMessage(String body) {
        try {
            if (body != null && body.contains("\"message\"")) {
                int s = body.indexOf("\"message\"") + 11;
                while (s < body.length() && (body.charAt(s) == ' ' || body.charAt(s) == ':')) s++;
                if (body.charAt(s) == '"') s++;
                int e = body.indexOf('"', s);
                if (e > s) return body.substring(s, e);
            }
        } catch (Exception ignored) {}
        return body != null ? body : "unknown";
    }
}