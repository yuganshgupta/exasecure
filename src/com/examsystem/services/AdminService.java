package com.examsystem.services;

import com.examsystem.dao.*;
import com.examsystem.models.*;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public class AdminService {
    private final ExamDAO examDAO = new ExamDAO();
    private final QuestionDAO questionDAO = new QuestionDAO();
    private final OptionDAO optionDAO = new OptionDAO();
    private final ExamAttemptDAO examAttemptDAO = new ExamAttemptDAO();
    private final StudentAnswerDAO studentAnswerDAO = new StudentAnswerDAO();
    private final UserDAO userDAO = new UserDAO();

    // --- Exam Management ---
    public int createExam(String title, int durationMinutes, int adminUserId) {
        return examDAO.createExam(title, durationMinutes, adminUserId);
    }

    public List<Exam> listAllExams() {
        return examDAO.getAllExams();
    }

    public Exam getExamById(int examId) { 
        return examDAO.getExamById(examId); 
    }

    // --- Question Management ---
    public boolean addQuestionWithOptions(int examId, String questionText, String[] optionsText, int correctOptionNumber) {
        if (optionsText == null || optionsText.length == 0) return false;
        
        List<String> validOptions = new ArrayList<>();
        for (String opt : optionsText) {
            if (opt != null && !opt.trim().isEmpty()) {
                validOptions.add(opt.trim());
            }
        }
        
        if (validOptions.size() < 2) return false; 
        if (correctOptionNumber > validOptions.size()) return false; 

        int qId = questionDAO.addQuestion(examId, questionText, correctOptionNumber);
        if (qId <= 0) return false;

        for (int i = 0; i < validOptions.size(); i++) {
            optionDAO.addOption(qId, i + 1, validOptions.get(i));
        }
        return true;
    }

    public boolean addQuestionToExam(int examId, String questionText, String[] optionsText, int correctOptionNumber) {
        return addQuestionWithOptions(examId, questionText, optionsText, correctOptionNumber);
    }

    public List<Question> getQuestionsForExam(int examId) {
        return questionDAO.getQuestionsByExamId(examId);
    }

    public List<Option> getOptionsForQuestion(int questionId) {
        return optionDAO.getOptionsByQuestionId(questionId);
    }

    // --- Results & Proctoring ---
    public List<AttemptSummary> getExamAttemptSummaries(int examId) {
        return examAttemptDAO.getAttemptSummariesByExamId(examId);
    }

    public boolean deleteExamAttempt(int attemptId) {
        return examAttemptDAO.deleteAttempt(attemptId);
    }

    public List<String> getDistinctDatesForExam(int examId) {
        return examAttemptDAO.getDistinctDatesForExam(examId);
    }

    public ExamAttempt getAttemptById(int attemptId) { 
        return examAttemptDAO.getAttemptById(attemptId); 
    }

    public List<StudentAnswer> getAnswersForAttempt(int attemptId) {
        return studentAnswerDAO.getAnswersForAttemptOrderedByTimestamp(attemptId);
    }

    public List<ProctorLog> getProctorLogs(int attemptId) {
        return examAttemptDAO.getProctorLogs(attemptId);
    }



    public void saveStudentAnswer(int attemptId, int questionId, int optionNum, boolean correct) {
        studentAnswerDAO.saveAnswer(attemptId, questionId, optionNum, correct, new java.sql.Timestamp(System.currentTimeMillis()));
    }

    public int getStudentSelectedOption(int attemptId, int questionId) {
        return studentAnswerDAO.getStudentSelectedOption(attemptId, questionId);
    }

    public List<StudentAnswerDetail> getDetailedAnswers(int attemptId) {
        return studentAnswerDAO.getDetailedAnswers(attemptId);
    }

    public List<QuestionStatistic> getQuestionStatistics(int examId, String dateFilter, String sectionFilter) {
        return studentAnswerDAO.getQuestionStatistics(examId, dateFilter, sectionFilter);
    }

    // --- User Management ---
    public int registerUser(String username, String password, String fullName, String enrollment, String section, String role) {
        return userDAO.registerUser(username, password, fullName, enrollment, section, role);
    }

    public boolean updateUser(int id, String username, String password, String fullName, String enrollment, String section, String role) {
        return userDAO.updateUser(id, username, password, fullName, enrollment, section, role);
    }

    public List<User> getAllUsers() {
        return userDAO.findAll();
    }
}