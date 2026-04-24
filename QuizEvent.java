package com.bajaj.quiz;

public class QuizEvent {
    private String roundId;
    private String participant;
    private int score;

    public QuizEvent() {}

    public QuizEvent(String roundId, String participant, int score) {
        this.roundId = roundId;
        this.participant = participant;
        this.score = score;
    }

    public String getRoundId() { return roundId; }
    public void setRoundId(String roundId) { this.roundId = roundId; }

    public String getParticipant() { return participant; }
    public void setParticipant(String participant) { this.participant = participant; }

    public int getScore() { return score; }
    public void setScore(int score) { this.score = score; }

    // Unique key for deduplication: roundId + participant
    public String getDedupeKey() {
        return roundId + "|" + participant;
    }

    @Override
    public String toString() {
        return "QuizEvent{roundId='" + roundId + "', participant='" + participant + "', score=" + score + "}";
    }
}
