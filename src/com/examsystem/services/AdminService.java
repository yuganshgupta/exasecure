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

    public boolean softDeleteExam(int examId) {
        return examDAO.softDeleteExam(examId);
    }

    public boolean updateExam(int examId, String title, int durationMinutes) {
        return examDAO.updateExam(examId, title, durationMinutes);
    }

    // --- Question Management ---
    public boolean addQuestionWithOptions(int examId, String questionText, String[] optionsText, boolean[] isCorrectArray, String questionType) {
        if (optionsText == null || optionsText.length == 0) return false;
        
        List<String> validOptions = new ArrayList<>();
        List<Boolean> validIsCorrect = new ArrayList<>();
        
        for (int i = 0; i < optionsText.length; i++) {
            String opt = optionsText[i];
            if (opt != null && !opt.trim().isEmpty()) {
                validOptions.add(opt.trim());
                validIsCorrect.add(isCorrectArray != null && i < isCorrectArray.length ? isCorrectArray[i] : false);
            }
        }
        
        if (validOptions.size() < 2) return false; 
        
        // Validation for SINGLE vs MULTI
        long correctCount = validIsCorrect.stream().filter(b -> b).count();
        if ("SINGLE".equals(questionType) && correctCount != 1) return false;
        if ("MULTI".equals(questionType) && correctCount < 1) return false;

        // Note: For legacy correctOptionNumber column in questions table, we just store the first correct option (or 1)
        int primaryCorrectOption = 1;
        for (int i = 0; i < validIsCorrect.size(); i++) {
            if (validIsCorrect.get(i)) {
                primaryCorrectOption = i + 1;
                break;
            }
        }

        int qId = questionDAO.addQuestion(examId, questionText, primaryCorrectOption, questionType);
        if (qId <= 0) return false;

        for (int i = 0; i < validOptions.size(); i++) {
            optionDAO.addOption(qId, i + 1, validOptions.get(i), validIsCorrect.get(i));
        }
        return true;
    }

    public boolean softDeleteQuestion(int questionId) {
        return questionDAO.softDeleteQuestion(questionId);
    }

    public boolean updateQuestion(Question question) {
        return questionDAO.updateQuestion(question);
    }

    public boolean softDeleteOption(int optionId) {
        return optionDAO.softDeleteOption(optionId);
    }

    public boolean updateOption(Option option) {
        return optionDAO.updateOption(option);
    }

    public boolean addQuestionToExam(int examId, String questionText, String[] optionsText, boolean[] isCorrectArray, String questionType) {
        return addQuestionWithOptions(examId, questionText, optionsText, isCorrectArray, questionType);
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



    public void saveStudentAnswer(int attemptId, int questionId, String optionsCsv, boolean correct) {
        studentAnswerDAO.saveAnswer(attemptId, questionId, optionsCsv, correct, new java.sql.Timestamp(System.currentTimeMillis()));
    }

    public List<Integer> getStudentSelectedOptions(int attemptId, int questionId) {
        return studentAnswerDAO.getStudentSelectedOptions(attemptId, questionId);
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

    public boolean softDeleteUser(int id) {
        return userDAO.softDeleteUser(id);
    }
}