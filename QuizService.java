package com.bajaj.quiz;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class QuizService {

    private static final String BASE_URL = "https://devapigw.vidalhealthtpa.com/srm-quiz-task";
    private static final int TOTAL_POLLS = 10;
    private static final int DELAY_BETWEEN_POLLS_MS = 5000; // 5 seconds mandatory

    private final String regNo;

    public QuizService(String regNo) {
        this.regNo = regNo;
    }

    public void run() {
        System.out.println("=== Quiz Leaderboard System ===");
        System.out.println("Registration Number: " + regNo);
        System.out.println();

        // Step 1: Poll API 10 times and collect all events
        Map<String, QuizEvent> deduplicatedEvents = new LinkedHashMap<>();

        for (int poll = 0; poll < TOTAL_POLLS; poll++) {
            System.out.println("--- Poll " + poll + " ---");
            try {
                PollResponse response = fetchPoll(poll);

                if (response != null && response.getEvents() != null) {
                    System.out.println("  Received " + response.getEvents().size() + " event(s) in poll " + poll);

                    for (QuizEvent event : response.getEvents()) {
                        String key = event.getDedupeKey();
                        if (!deduplicatedEvents.containsKey(key)) {
                            deduplicatedEvents.put(key, event);
                            System.out.println("  [NEW]  " + event);
                        } else {
                            System.out.println("  [DUP]  Skipping duplicate: " + event);
                        }
                    }
                } else {
                    System.out.println("  No events received or null response.");
                }

            } catch (Exception e) {
                System.err.println("  ERROR during poll " + poll + ": " + e.getMessage());
            }

            // Mandatory 5-second delay between polls (skip after last poll)
            if (poll < TOTAL_POLLS - 1) {
                System.out.println("  Waiting 5 seconds before next poll...");
                try {
                    Thread.sleep(DELAY_BETWEEN_POLLS_MS);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        System.out.println();
        System.out.println("=== Deduplication Complete ===");
        System.out.println("Total unique events: " + deduplicatedEvents.size());

        // Step 2: Aggregate scores per participant
        Map<String, Integer> scoreMap = new LinkedHashMap<>();
        for (QuizEvent event : deduplicatedEvents.values()) {
            scoreMap.merge(event.getParticipant(), event.getScore(), Integer::sum);
        }

        // Step 3: Build leaderboard sorted by totalScore descending
        List<LeaderboardEntry> leaderboard = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : scoreMap.entrySet()) {
            leaderboard.add(new LeaderboardEntry(entry.getKey(), entry.getValue()));
        }
        Collections.sort(leaderboard);

        // Step 4: Compute total score across all users
        int totalScore = leaderboard.stream().mapToInt(LeaderboardEntry::getTotalScore).sum();

        System.out.println();
        System.out.println("=== Leaderboard ===");
        for (int i = 0; i < leaderboard.size(); i++) {
            System.out.println((i + 1) + ". " + leaderboard.get(i));
        }
        System.out.println("Total Score (all users): " + totalScore);

        // Step 5: Submit leaderboard once
        System.out.println();
        System.out.println("=== Submitting Leaderboard ===");
        submitLeaderboard(leaderboard);
    }

    private PollResponse fetchPoll(int pollIndex) throws Exception {
        String urlStr = BASE_URL + "/quiz/messages?regNo=" + regNo + "&poll=" + pollIndex;
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Accept", "application/json");
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(10000);

        int responseCode = conn.getResponseCode();
        System.out.println("  GET " + urlStr + " -> HTTP " + responseCode);

        if (responseCode == 200) {
            String body = readStream(conn.getInputStream());
            return parseGetResponse(body);
        } else {
            String error = readStream(conn.getErrorStream());
            System.err.println("  Error body: " + error);
            return null;
        }
    }

    private void submitLeaderboard(List<LeaderboardEntry> leaderboard) {
        try {
            String urlStr = BASE_URL + "/quiz/submit";
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Accept", "application/json");
            conn.setDoOutput(true);
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);

            String body = buildSubmitBody(leaderboard);
            System.out.println("  Request body: " + body);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(body.getBytes(StandardCharsets.UTF_8));
            }

            int responseCode = conn.getResponseCode();
            System.out.println("  POST " + urlStr + " -> HTTP " + responseCode);

            String response;
            if (responseCode == 200 || responseCode == 201) {
                response = readStream(conn.getInputStream());
            } else {
                response = readStream(conn.getErrorStream());
            }

            System.out.println("  Response: " + response);

        } catch (Exception e) {
            System.err.println("  ERROR during submission: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // ─── Manual JSON Parsing (no external libraries needed) ─────────────────

    private PollResponse parseGetResponse(String json) {
        PollResponse response = new PollResponse();
        response.setRegNo(extractString(json, "regNo"));
        response.setSetId(extractString(json, "setId"));

        String pollIndexStr = extractString(json, "pollIndex");
        if (pollIndexStr != null) {
            try { response.setPollIndex(Integer.parseInt(pollIndexStr)); } catch (NumberFormatException ignored) {}
        }

        List<QuizEvent> events = parseEvents(json);
        response.setEvents(events);
        return response;
    }

    private List<QuizEvent> parseEvents(String json) {
        List<QuizEvent> events = new ArrayList<>();
        int eventsStart = json.indexOf("\"events\"");
        if (eventsStart == -1) return events;

        int arrayStart = json.indexOf('[', eventsStart);
        int arrayEnd = json.lastIndexOf(']');
        if (arrayStart == -1 || arrayEnd == -1) return events;

        String eventsArray = json.substring(arrayStart + 1, arrayEnd);

        // Split by object boundaries
        int depth = 0;
        int objStart = -1;
        for (int i = 0; i < eventsArray.length(); i++) {
            char c = eventsArray.charAt(i);
            if (c == '{') {
                if (depth == 0) objStart = i;
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0 && objStart != -1) {
                    String obj = eventsArray.substring(objStart, i + 1);
                    QuizEvent event = parseEvent(obj);
                    if (event != null) events.add(event);
                    objStart = -1;
                }
            }
        }
        return events;
    }

    private QuizEvent parseEvent(String obj) {
        String roundId = extractString(obj, "roundId");
        String participant = extractString(obj, "participant");
        String scoreStr = extractString(obj, "score");
        if (roundId == null || participant == null || scoreStr == null) return null;
        try {
            int score = Integer.parseInt(scoreStr.trim());
            return new QuizEvent(roundId, participant, score);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Extracts a value for a given key from a simple JSON string.
     * Works for both string values ("key":"value") and numeric values ("key":123).
     */
    private String extractString(String json, String key) {
        String searchKey = "\"" + key + "\"";
        int keyIdx = json.indexOf(searchKey);
        if (keyIdx == -1) return null;

        int colonIdx = json.indexOf(':', keyIdx + searchKey.length());
        if (colonIdx == -1) return null;

        int valueStart = colonIdx + 1;
        while (valueStart < json.length() && json.charAt(valueStart) == ' ') valueStart++;

        if (valueStart >= json.length()) return null;

        if (json.charAt(valueStart) == '"') {
            // String value
            int end = json.indexOf('"', valueStart + 1);
            if (end == -1) return null;
            return json.substring(valueStart + 1, end);
        } else {
            // Numeric or boolean value
            int end = valueStart;
            while (end < json.length() && ",}\n ".indexOf(json.charAt(end)) == -1) end++;
            return json.substring(valueStart, end).trim();
        }
    }

    private String buildSubmitBody(List<LeaderboardEntry> leaderboard) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"regNo\":\"").append(regNo).append("\",");
        sb.append("\"leaderboard\":[");
        for (int i = 0; i < leaderboard.size(); i++) {
            LeaderboardEntry e = leaderboard.get(i);
            sb.append("{");
            sb.append("\"participant\":\"").append(e.getParticipant()).append("\",");
            sb.append("\"totalScore\":").append(e.getTotalScore());
            sb.append("}");
            if (i < leaderboard.size() - 1) sb.append(",");
        }
        sb.append("]}");
        return sb.toString();
    }

    private String readStream(InputStream is) throws IOException {
        if (is == null) return "";
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
        }
        return sb.toString();
    }
}
