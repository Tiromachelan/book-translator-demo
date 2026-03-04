package com.booktranslator;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Translates text using the LibreTranslate public API.
 * Large texts are split into chunks to respect API limits.
 */
public class TranslationService {

    private static final String API_URL = "https://libretranslate.com/translate";
    private static final int CHUNK_SIZE = 500;       // characters per request
    private static final long DELAY_MS  = 1000;      // ms between requests

    private final String sourceLang;
    private final String targetLang;
    private final HttpClient httpClient;

    public TranslationService(String sourceLang, String targetLang) {
        this.sourceLang = sourceLang;
        this.targetLang = targetLang;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
    }

    /** Translates the full book text, chunking as needed. */
    public String translate(String text) throws TranslationException {
        List<String> chunks = splitIntoChunks(text, CHUNK_SIZE);
        StringBuilder result = new StringBuilder();
        int total = chunks.size();

        for (int i = 0; i < total; i++) {
            System.out.printf("Translating chunk %d / %d...%n", i + 1, total);
            String translated = translateChunk(chunks.get(i));
            result.append(translated);

            // Preserve paragraph breaks: if chunk ended with newline, keep it
            if (chunks.get(i).endsWith("\n") && !translated.endsWith("\n")) {
                result.append("\n");
            }

            if (i < total - 1) {
                try {
                    Thread.sleep(DELAY_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new TranslationException("Translation interrupted.");
                }
            }
        }

        return result.toString();
    }

    /** Sends a single chunk to the LibreTranslate API and returns the translation. */
    private String translateChunk(String chunk) throws TranslationException {
        String jsonBody = String.format(
                "{\"q\":%s,\"source\":\"%s\",\"target\":\"%s\",\"format\":\"text\"}",
                jsonEscape(chunk), sourceLang, targetLang
        );

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(60))
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new TranslationException("Network error: " + e.getMessage());
        }

        if (response.statusCode() != 200) {
            throw new TranslationException(
                    "API returned status " + response.statusCode() + ": " + response.body());
        }

        return extractTranslatedText(response.body());
    }

    /**
     * Splits text into chunks of at most maxSize characters,
     * preferring to break at sentence-ending punctuation.
     */
    static List<String> splitIntoChunks(String text, int maxSize) {
        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            if (start + maxSize >= text.length()) {
                chunks.add(text.substring(start));
                break;
            }
            // Try to break at sentence boundary within the chunk window
            int end = start + maxSize;
            int breakPoint = findBreakPoint(text, start, end);
            chunks.add(text.substring(start, breakPoint));
            start = breakPoint;
        }
        return chunks;
    }

    /** Finds the last sentence-ending punctuation before 'end', falling back to 'end'. */
    private static int findBreakPoint(String text, int start, int end) {
        for (int i = end; i > start; i--) {
            char c = text.charAt(i - 1);
            if (c == '.' || c == '!' || c == '?' || c == '\n') {
                return i;
            }
        }
        return end; // no good break point found, hard-cut
    }

    /**
     * Extracts the "translatedText" value from a LibreTranslate JSON response.
     * Uses simple string parsing to avoid requiring an external JSON library.
     */
    private static String extractTranslatedText(String json) throws TranslationException {
        String key = "\"translatedText\":\"";
        int keyIndex = json.indexOf(key);
        if (keyIndex == -1) {
            throw new TranslationException("Unexpected API response format: " + json);
        }
        int valueStart = keyIndex + key.length();
        // Find the closing quote, respecting escaped quotes
        StringBuilder sb = new StringBuilder();
        for (int i = valueStart; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '\\' && i + 1 < json.length()) {
                char next = json.charAt(i + 1);
                switch (next) {
                    case '"':  sb.append('"');  i++; break;
                    case '\\': sb.append('\\'); i++; break;
                    case 'n':  sb.append('\n'); i++; break;
                    case 'r':  sb.append('\r'); i++; break;
                    case 't':  sb.append('\t'); i++; break;
                    default:   sb.append(c); break;
                }
            } else if (c == '"') {
                break; // end of value
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /** Wraps a string in JSON quotes with necessary characters escaped. */
    private static String jsonEscape(String text) {
        StringBuilder sb = new StringBuilder("\"");
        for (char c : text.toCharArray()) {
            switch (c) {
                case '"':  sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n");  break;
                case '\r': sb.append("\\r");  break;
                case '\t': sb.append("\\t");  break;
                default:   sb.append(c);      break;
            }
        }
        sb.append("\"");
        return sb.toString();
    }

    /** Thrown when the translation API call fails. */
    public static class TranslationException extends Exception {
        public TranslationException(String message) {
            super(message);
        }
    }
}
