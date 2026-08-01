package com.examsystem.gui.exam;

import com.examsystem.dao.ExamAttemptDAO;
import com.examsystem.dao.OptionDAO;
import com.examsystem.dao.QuestionDAO;
import com.examsystem.dao.StudentAnswerDAO;
import com.examsystem.models.Exam;
import com.examsystem.models.Option;
import com.examsystem.models.Question;
import com.examsystem.models.User;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.event.WindowFocusListener;
import java.awt.image.BufferedImage;
import java.sql.Timestamp;
import java.util.List;
import javax.imageio.ImageIO;
import javax.swing.*;

public class ExamWindow extends JDialog {

    private final Exam exam;
    private final User student;

    private final QuestionDAO questionDAO = new QuestionDAO();
    private final OptionDAO optionDAO = new OptionDAO();
    private final ExamAttemptDAO examAttemptDAO = new ExamAttemptDAO();
    private final StudentAnswerDAO studentAnswerDAO = new StudentAnswerDAO();

    private List<Question> questions;
    private int currentIndex = 0;

    private int attemptId = -1;
    private boolean finalized = false;

    // Proctoring Fields
    private int focusLostCount = 0;
    private final int MAX_FOCUS_LOST = 3;
    private long lastFocusLostTime = 0; 
    private boolean isOverlayVisible = false;

    // UI Components
    private final JLabel timerLabel = new JLabel("Time: 00:00");
    private final JLabel questionLabel = new JLabel("Question text");
    private final JRadioButton[] optionButtons = { new JRadioButton(), new JRadioButton(), new JRadioButton(), new JRadioButton() };
    private final ButtonGroup optionGroup = new ButtonGroup();
    private final JButton prevButton = new JButton("Previous");
    private final JButton nextButton = new JButton("Next");
    private final JButton submitEarlyButton = new JButton("Submit Early");

    private Timer swingTimer;
    private int remainingSeconds;

    public ExamWindow(Frame owner, Exam exam, User student) {
        super(owner, "Exam: " + exam.getTitle(), true);
        this.exam = exam;
        this.student = student;

        // --- NEW: FULL SCREEN & KIOSK MODE ---
        setUndecorated(true); // Removes OS borders, minimize, and close buttons for strict proctoring
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        setSize(screenSize.width, screenSize.height);
        setLocation(0, 0);
        setLayout(new BorderLayout(20, 20)); // Added spacing between main areas

        // --- NEW: READABLE FONTS ---
        Font timerFont = new Font("SansSerif", Font.BOLD, 36);
        Font questionFont = new Font("SansSerif", Font.PLAIN, 28);
        Font optionFont = new Font("SansSerif", Font.PLAIN, 24);
        Font buttonFont = new Font("SansSerif", Font.BOLD, 22);

        // Top: Timer
        JPanel top = new JPanel(new BorderLayout());
        top.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20)); // Added Padding
        timerLabel.setHorizontalAlignment(SwingConstants.CENTER);
        timerLabel.setFont(timerFont); // Applied large font
        timerLabel.setForeground(Color.RED); // Make timer stand out
        top.add(timerLabel, BorderLayout.CENTER);
        add(top, BorderLayout.NORTH);

        // Center: Question
        JPanel center = new JPanel();
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));
        center.setBorder(BorderFactory.createEmptyBorder(40, 80, 40, 80)); // Heavy padding for readability

        // Wrap question text in HTML so it wraps automatically if it's too long
        questionLabel.setFont(questionFont);
        questionLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 30, 0)); // Space between Q and Options
        center.add(questionLabel);

        JPanel optionsPanel = new JPanel(new GridLayout(0, 1, 10, 15)); // Added vertical gaps between options
        for (JRadioButton rb : optionButtons) {
            rb.setFont(optionFont); // Applied readable option font
            optionGroup.add(rb);
            optionsPanel.add(rb);
            // Persist immediately when the student clicks an option
            rb.addActionListener(e -> saveCurrentAnswer());
        }
        center.add(optionsPanel);
        add(center, BorderLayout.CENTER);

        // Bottom: Navigation
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT, 20, 20));
        bottom.setBorder(BorderFactory.createEmptyBorder(20, 20, 40, 40));
        
        submitEarlyButton.setFont(buttonFont);
        prevButton.setFont(buttonFont);
        nextButton.setFont(buttonFont);
        
        bottom.add(submitEarlyButton);
        bottom.add(prevButton);
        bottom.add(nextButton);
        add(bottom, BorderLayout.SOUTH);

        // Listeners
        prevButton.addActionListener(e -> onPrevious());
        nextButton.addActionListener(e -> onNext());
        submitEarlyButton.addActionListener(e -> onSubmitEarly());

        // --- PROCTORING LOGIC ---
        addWindowFocusListener(new WindowFocusListener() {
            @Override
            public void windowGainedFocus(WindowEvent e) {
                if (finalized || isOverlayVisible) return;

                if (lastFocusLostTime > 0) {
                    long timeAway = System.currentTimeMillis() - lastFocusLostTime;
                    if (timeAway > 5000) {
                        isOverlayVisible = true;
                        try {
                            String msg = "Long Absence: " + (timeAway/1000) + "s while on Q" + (currentIndex+1);
                            examAttemptDAO.logEvent(attemptId, msg, null);
                            
                            JOptionPane.showMessageDialog(ExamWindow.this, 
                                "You were away for " + (timeAway/1000) + " seconds!", 
                                "Proctoring Warning", JOptionPane.WARNING_MESSAGE);
                        } finally {
                            isOverlayVisible = false;
                        }
                    }
                    lastFocusLostTime = 0; 
                }
            }

            @Override
            public void windowLostFocus(WindowEvent e) {
                if (finalized || isOverlayVisible) return;

                focusLostCount++;
                lastFocusLostTime = System.currentTimeMillis();
                
                String msg = "Focus lost while viewing Q" + (currentIndex + 1);
                byte[] screenshotData = takeScreenshotBytes();
                examAttemptDAO.logEvent(attemptId, msg, screenshotData);

                int remaining = MAX_FOCUS_LOST - focusLostCount;
                isOverlayVisible = true; 
                try {
                    if (remaining < 0) {
                        examAttemptDAO.logEvent(attemptId, "Limit exceeded. Auto-submitting.", null);
                        JOptionPane.showMessageDialog(ExamWindow.this, 
                            "Suspicious activity detected! Auto-submitting.", 
                            "Alert", JOptionPane.ERROR_MESSAGE);
                        finalizeAttemptAndClose();
                    } else {
                        JOptionPane.showMessageDialog(ExamWindow.this, 
                            "Warning: Focus lost! " + remaining + " attempts left.", 
                            "Warning", JOptionPane.WARNING_MESSAGE);
                    }
                } finally {
                    isOverlayVisible = false;
                }
            }
        });

        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) {
                if (!finalized) {
                    isOverlayVisible = true; 
                    try {
                        int opt = JOptionPane.showConfirmDialog(ExamWindow.this, "Close and submit?", "Confirm", JOptionPane.YES_NO_OPTION);
                        if (opt == JOptionPane.YES_OPTION) finalizeAttemptAndClose();
                        else setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
                    } finally {
                        isOverlayVisible = false;
                    }
                }
            }
        });

        loadContentAndStart();
    }

    private byte[] takeScreenshotBytes() {
        try {
            Robot robot = new Robot();
            Rectangle screenRect = new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
            BufferedImage screenFullImage = robot.createScreenCapture(screenRect);
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            ImageIO.write(screenFullImage, "jpg", baos);
            return baos.toByteArray();
        } catch (Exception e) {
            return null;
        }
    }

    private void loadContentAndStart() {
        questions = questionDAO.getQuestionsByExamId(exam.getId());
        if (questions.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No questions found.");
            dispose();
            return;
        }

        attemptId = examAttemptDAO.createAttempt(exam.getId(), student.getId(), new Timestamp(System.currentTimeMillis()));
        if (attemptId <= 0) {
            JOptionPane.showMessageDialog(this, "Failed to start exam. Please try again or contact administrator.", "Error", JOptionPane.ERROR_MESSAGE);
            dispose();
            return;
        }
        
        remainingSeconds = exam.getDurationMinutes() * 60;
        
        swingTimer = new Timer(1000, e -> {
            remainingSeconds--;
            if (remainingSeconds <= 0) {
                timerLabel.setText("Time: 00:00");
                swingTimer.stop();
                JOptionPane.showMessageDialog(this, "Time Up!");
                finalizeAttemptAndClose();
            } else {
                timerLabel.setText(String.format("Time: %02d:%02d", remainingSeconds / 60, remainingSeconds % 60));
            }
        });
        swingTimer.start();
        showCurrentQuestion();
    }

    private void showCurrentQuestion() {
        Question q = questions.get(currentIndex);
        
        // Wrap question text in HTML tags so long questions automatically wrap to the next line 
        questionLabel.setText("<html>Q" + (currentIndex + 1) + ": " + q.getQuestionText() + "</html>");

        List<Option> opts = optionDAO.getOptionsByQuestionId(q.getId());
        optionGroup.clearSelection();
        int savedSelection = studentAnswerDAO.getStudentSelectedOption(attemptId, q.getId());

        for (int i = 0; i < 4; i++) {
            if (i < opts.size()) {
                optionButtons[i].setText(opts.get(i).getOptionText());
                optionButtons[i].setVisible(true);
                if ((i + 1) == savedSelection) {
                    optionButtons[i].setSelected(true);
                }
            } else {
                optionButtons[i].setVisible(false);
            }
        }
        prevButton.setEnabled(currentIndex > 0);
        nextButton.setText(currentIndex == questions.size() - 1 ? "Submit" : "Next");
    }

    private void saveCurrentAnswer() {
        int selectedIndex = -1;
        for (int i = 0; i < 4; i++) {
            if (optionButtons[i].isVisible() && optionButtons[i].isSelected()) {
                selectedIndex = i + 1;
                break;
            }
        }
        
        if (selectedIndex != -1) {
            Question q = questions.get(currentIndex);
            boolean correct = (selectedIndex == q.getCorrectOptionNumber());
            studentAnswerDAO.saveAnswer(attemptId, q.getId(), selectedIndex, correct, new Timestamp(System.currentTimeMillis()));
        }
    }

    private void onNext() {
        if (currentIndex == questions.size() - 1) {
            finalizeAttemptAndClose();
        } else {
            saveCurrentAnswer();
            currentIndex++;
            showCurrentQuestion();
        }
    }

    private void onPrevious() {
        saveCurrentAnswer();
        if (currentIndex > 0) {
            currentIndex--;
            showCurrentQuestion();
        }
    }

    private void onSubmitEarly() {
        isOverlayVisible = true;
        try {
            UIManager.put("OptionPane.messageFont", new Font("SansSerif", Font.PLAIN, 20));
            UIManager.put("OptionPane.buttonFont", new Font("SansSerif", Font.BOLD, 18));
            if (JOptionPane.showConfirmDialog(this, "Are you sure you want to submit early?", "Confirm Submit", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
                finalizeAttemptAndClose();
            }
        } finally {
            isOverlayVisible = false;
        }
    }

    private void finalizeAttemptAndClose() {
        if (finalized) return;
        
        saveCurrentAnswer();
        finalized = true;
        if (swingTimer != null) swingTimer.stop();

        examAttemptDAO.calculateAndSetScore(attemptId, focusLostCount);
        int total = questions.size();
        int correct = studentAnswerDAO.countCorrectAnswersByAttempt(attemptId);
        
        UIManager.put("OptionPane.messageFont", new Font("SansSerif", Font.BOLD, 24));
        JOptionPane.showMessageDialog(this, "Exam Submitted!\nFinal Score: " + correct + " / " + total);
        dispose();
    }
}