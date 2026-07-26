# 🎓 Secure Examination System

A desktop-based examination platform built using **Core Java, Swing, and JDBC**. The application provides a complete environment for conducting examinations with separate administrator and student interfaces, direct MySQL integration, automated evaluation, and basic proctoring capabilities.

This project was developed to demonstrate strong fundamentals in **Object-Oriented Programming, GUI development, database design, and software architecture** without relying on external frameworks.

---

# Features

## Authentication & User Management

- Secure login system for users
- Role-based access control
  - Administrator
  - Student
- User management interface for administrators

---

## Examination Management

Administrators can:

- Create new exams
- Add multiple-choice questions
- Manage question banks
- Configure examination details
- Review submitted examinations

Students can:

- View available exams
- Attempt examinations
- Navigate between questions
- Submit examinations manually
- Automatically submit when time expires

---

## Real-Time Examination System

- Countdown timer for each examination
- Automatic submission on timeout
- Question navigation
- Option selection
- Answer persistence during the examination

---

## Result Processing

- Automatic answer evaluation
- Score calculation
- Attempt history
- Review submitted answers
- Performance statistics

---

## Proctoring System

The application includes a lightweight desktop proctoring system.

Features include:

- Window focus detection
- Violation logging
- Timestamp recording
- Screenshot capture during violations
- Proctor log viewer for administrators

Captured screenshots are stored locally for later review.

---

## Database Integration

- MySQL database backend
- JDBC connectivity
- DAO (Data Access Object) architecture
- CRUD operations
- Separation of business logic and persistence layer

---

# Technology Stack

| Category | Technology |
|-----------|------------|
| Language | Java SE |
| GUI | Java Swing & AWT |
| Database | MySQL |
| Connectivity | JDBC |
| Architecture | DAO Pattern |
| IDE | IntelliJ IDEA / Eclipse / VS Code |

---

# Project Structure

```
SecureExaminationSystem/
│
├── dao/
│   ├── UserDAO.java
│   ├── ExamDAO.java
│   ├── QuestionDAO.java
│   ├── OptionDAO.java
│   ├── StudentAnswerDAO.java
│   └── ExamAttemptDAO.java
│
├── db/
│   ├── DatabaseConnector.java
│   └── TestDBConnection.java
│
├── gui/
│   ├── dialogs/
│   ├── exam/
│   ├── panels/
│   ├── proctor_logs/
│   ├── LoginWindow.java
│   └── StudentDashboard.java
│
├── models/
│
├── services/
│
└── Main.java
```

---

# Architecture

The project follows a layered architecture.

```
Presentation Layer (Swing GUI)
            │
            ▼
Business Services
            │
            ▼
DAO Layer
            │
            ▼
JDBC
            │
            ▼
MySQL Database
```

This separation improves maintainability and keeps database operations independent of the user interface.

---

# Database

The application uses MySQL for persistent storage.

Typical entities include:

- Users
- Exams
- Questions
- Options
- Student Answers
- Exam Attempts
- Proctor Logs

---

# Setup

## 1. Clone the repository

```bash
git clone https://github.com/your-username/secure-examination-system.git
cd secure-examination-system
```

---

## 2. Configure MySQL

Create a MySQL database.

Example:

```sql
CREATE DATABASE exam_system;
```

Import your SQL schema.

```bash
mysql -u root -p exam_system < database.sql
```

---

## 3. Configure Database Credentials

Update the database credentials inside:

```
db/DatabaseConnector.java
```

or configure them using environment variables if your project has been updated to support them.

Example:

```java
URL = jdbc:mysql://localhost:3306/exam_system
USERNAME = root
PASSWORD = your_password
```

---

## 4. Add MySQL JDBC Driver

Download MySQL Connector/J and add it to your project's classpath.

---

## 5. Compile

macOS / Linux

```bash
javac -cp ".:mysql-connector-j.jar" $(find . -name "*.java")
```

Windows

```cmd
dir /s /b *.java > sources.txt
javac -cp ".;mysql-connector-j.jar" @sources.txt
del sources.txt
```

---

## 6. Run

```bash
java Main
```

---

# Application Workflow

```
Login
   │
   ├──────────────┐
   │              │
Admin         Student
   │              │
Manage      Select Exam
Users            │
Exams            │
Questions        │
   │         Start Exam
   │              │
Review       Timer Running
Attempts          │
                  │
         Window Focus Detection
                  │
          Violation Logging
                  │
          Submit Examination
                  │
          Automatic Evaluation
                  │
            Store in Database
```

---

# Highlights

- Desktop-based examination platform
- Object-Oriented Design
- Modular architecture
- Swing-based graphical interface
- JDBC database connectivity
- DAO design pattern
- Role-based access
- Automated evaluation
- Window-focus proctoring
- Screenshot-based violation recording
- Responsive UI using SwingWorker

---

# Future Improvements

Possible enhancements include:

- Password hashing
- Email notifications
- Online examination support
- Webcam-based AI proctoring
- Face recognition
- Question randomization
- Export results to PDF
- CSV report generation
- Exam scheduling
- Analytics dashboard

---

# Learning Outcomes

This project strengthened practical knowledge of:

- Core Java
- Object-Oriented Programming
- Swing GUI development
- Event-driven programming
- JDBC
- MySQL
- DAO Pattern
- Layered software architecture
- Multithreading using SwingWorker
- Desktop application development

---

# License

This project is intended for educational and portfolio purposes.
