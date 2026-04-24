package com.bajaj.quiz;

public class LeaderboardEntry implements Comparable<LeaderboardEntry> {
    private String participant;
    private int totalScore;

    public LeaderboardEntry(String participant, int totalScore) {
        this.participant = participant;
        this.totalScore = totalScore;
    }

    public String getParticipant() { return participant; }
    public void setParticipant(String participant) { this.participant = participant; }

    public int getTotalScore() { return totalScore; }
    public void setTotalScore(int totalScore) { this.totalScore = totalScore; }

    @Override
    public int compareTo(LeaderboardEntry other) {
        // Sort descending by totalScore
        return Integer.compare(other.totalScore, this.totalScore);
    }

    @Override
    public String toString() {
        return participant + " -> " + totalScore;
    }
}
