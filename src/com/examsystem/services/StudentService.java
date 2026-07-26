package com.examsystem.services;

import com.examsystem.dao.ExamDAO;
import com.examsystem.models.Exam;

import java.util.List;

/**
 * Kept for parity with the previous project organization.
 * The GUI directly uses DAOs for student views, but this remains for maintainability.
 */
public class StudentService {
    private final ExamDAO examDAO = new ExamDAO();

    public List<Exam> listAllExams() {
        return examDAO.getAllExams();
    }
}