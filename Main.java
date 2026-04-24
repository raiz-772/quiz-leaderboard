package com.bajaj.quiz;

public class Main {
    public static void main(String[] args) {
        String regNo = "RA2311026010662";

        QuizService quizService = new QuizService(regNo);
        quizService.run();
    }
}
