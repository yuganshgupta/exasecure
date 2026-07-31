-- Examination System - Complete Database Schema
-- Compatible with Java Swing Project (UserDAO, ExamAttemptDAO, etc.)

DROP DATABASE IF EXISTS secure_exam_db;
CREATE DATABASE secure_exam_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE secure_exam_db;

-- 1. Users Table
-- Updated to include enrollment_number and section as required by UserDAO
CREATE TABLE users (
  id INT AUTO_INCREMENT PRIMARY KEY,
  username VARCHAR(50) NOT NULL UNIQUE,
  password VARCHAR(100) NOT NULL, -- Storing as plain text for this academic project
  full_name VARCHAR(100) NOT NULL,
  enrollment_number VARCHAR(50),  -- Added for Student identification
  section VARCHAR(10),            -- Added for class section (e.g., 'A', 'B')
  role ENUM('admin','student') NOT NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;

-- 2. Exams Table
CREATE TABLE exams (
  id INT AUTO_INCREMENT PRIMARY KEY,
  title VARCHAR(255) NOT NULL,
  duration_minutes INT NOT NULL,
  created_by INT NOT NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_exams_created_by FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- 3. Questions Table
CREATE TABLE questions (
  id INT AUTO_INCREMENT PRIMARY KEY,
  exam_id INT NOT NULL,
  question_text TEXT NOT NULL,
  correct_option_number INT NOT NULL, -- 1, 2, 3, or 4
  CONSTRAINT fk_questions_exam FOREIGN KEY (exam_id) REFERENCES exams(id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- 4. Options Table
CREATE TABLE options (
  id INT AUTO_INCREMENT PRIMARY KEY,
  question_id INT NOT NULL,
  option_number INT NOT NULL,
  option_text VARCHAR(500) NOT NULL,
  CONSTRAINT fk_options_question FOREIGN KEY (question_id) REFERENCES questions(id) ON DELETE CASCADE,
  CONSTRAINT uq_question_option UNIQUE (question_id, option_number)
) ENGINE=InnoDB;

-- 5. Exam Attempts Table
-- Updated to include focus_lost_count as required by ExamAttemptDAO
CREATE TABLE exam_attempts (
  id INT AUTO_INCREMENT PRIMARY KEY,
  exam_id INT NOT NULL,
  student_id INT NOT NULL,
  start_time DATETIME NOT NULL,
  end_time DATETIME,
  score INT DEFAULT 0,
  focus_lost_count INT DEFAULT 0, -- Tracks how many times student tabbed away
  CONSTRAINT fk_attempts_exam FOREIGN KEY (exam_id) REFERENCES exams(id) ON DELETE CASCADE,
  CONSTRAINT fk_attempts_student FOREIGN KEY (student_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- 6. Student Answers Table
CREATE TABLE student_answers (
  id INT AUTO_INCREMENT PRIMARY KEY,
  attempt_id INT NOT NULL,
  question_id INT NOT NULL,
  selected_option_number INT NOT NULL,
  is_correct TINYINT(1) NOT NULL,
  answer_timestamp DATETIME NOT NULL,
  CONSTRAINT fk_answers_attempt FOREIGN KEY (attempt_id) REFERENCES exam_attempts(id) ON DELETE CASCADE,
  CONSTRAINT fk_answers_question FOREIGN KEY (question_id) REFERENCES questions(id) ON DELETE CASCADE,
  CONSTRAINT uq_attempt_question UNIQUE (attempt_id, question_id)
) ENGINE=InnoDB;

-- 7. Proctor Logs Table
-- Stores details of suspicious activities (e.g. window switching)
CREATE TABLE proctor_logs (
  id INT AUTO_INCREMENT PRIMARY KEY,
  attempt_id INT NOT NULL,
  violation_type VARCHAR(100) NOT NULL, -- e.g., "FOCUS_LOST"
  screenshot_data LONGBLOB,
  violation_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_logs_attempt FOREIGN KEY (attempt_id) REFERENCES exam_attempts(id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- ========================================================
-- SEED DATA (For Testing)
-- ========================================================

-- Insert Admin
INSERT INTO users (username, password, full_name, role) 
VALUES ('admin', 'admin123', 'System Administrator', 'admin');

-- Insert Students (with Enrollment and Section)
INSERT INTO users (username, password, full_name, enrollment_number, section, role) 
VALUES 
('student1', 'password123', 'John Doe', 'EN001', 'A', 'student'),
('student2', 'password123', 'Jane Smith', 'EN002', 'B', 'student');

-- Insert Demo Exam
INSERT INTO exams (title, duration_minutes, created_by) 
VALUES ('Java Core Concepts', 10, 1);

-- Insert Questions for Exam 1
INSERT INTO questions (exam_id, question_text, correct_option_number) VALUES
(1, 'Which keyword is used to inherit a class in Java?', 2),
(1, 'What is the default value of a boolean variable?', 1),
(1, 'Which component is used to compile, debug and execute java programs?', 1);

-- Insert Options
INSERT INTO options (question_id, option_number, option_text) VALUES
-- Q1
(1, 1, 'implements'), (1, 2, 'extends'), (1, 3, 'inherits'), (1, 4, 'instanceof'),
-- Q2
(2, 1, 'false'), (2, 2, 'true'), (2, 3, 'null'), (2, 4, '0'),
-- Q3
(3, 1, 'JDK'), (3, 2, 'JRE'), (3, 3, 'JVM'), (3, 4, 'JIT');