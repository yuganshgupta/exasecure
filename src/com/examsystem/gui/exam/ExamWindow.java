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
    private final JPanel optionsPanel = new JPanel(); // NEW instance var
    private final ButtonGroup optionGroup = new ButtonGroup();
    private final List<JToggleButton> dynamicOptionButtons = new java.util.ArrayList<>();
    private final JButton prevButton = new JButton("Previous");
    private final JButton nextButton = new JButton("Next");
    private final JButton submitEarlyButton = new JButton("Submit Early");

    private Timer swingTimer;
    private int remainingSeconds;

    public ExamWindow(Frame owner, Exam exam, User student) {
        super(owner, "Exam: " + exam.getTitle(), true);
        this.exam = exam;
        this.student = student;

        // --- FIXED: macOS MENU BAR / NOTCH INSETS ---
        setUndecorated(true);
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        Insets insets = Toolkit.getDefaultToolkit().getScreenInsets(getGraphicsConfiguration());
        
        // Calculate safe area by subtracting dock/menu bar heights
        int safeWidth = screenSize.width - insets.left - insets.right;
        int safeHeight = screenSize.height - insets.top - insets.bottom;
        
        setSize(safeWidth, safeHeight);
        setLocation(insets.left, insets.top);
        setLayout(new BorderLayout(20, 20));

        // Fonts
        Font timerFont = new Font("SansSerif", Font.BOLD, 36);
        Font questionFont = new Font("SansSerif", Font.PLAIN, 28);
        Font optionFont = new Font("SansSerif", Font.PLAIN, 24);
        Font buttonFont = new Font("SansSerif", Font.BOLD, 22);

        // Top: Timer
        JPanel top = new JPanel(new BorderLayout());
        top.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        timerLabel.setHorizontalAlignment(SwingConstants.CENTER);
        timerLabel.setFont(timerFont);
        timerLabel.setForeground(Color.RED);
        top.add(timerLabel, BorderLayout.CENTER);
        add(top, BorderLayout.NORTH);

        // --- FIXED: ALIGNMENT & STRETCHING ---
        // Outer center panel to anchor content to the top
        JPanel centerOuter = new JPanel(new BorderLayout());
        
        // Inner container to hold Question and Options neatly
        JPanel contentContainer = new JPanel();
        contentContainer.setLayout(new BoxLayout(contentContainer, BoxLayout.Y_AXIS));
        contentContainer.setBorder(BorderFactory.createEmptyBorder(40, 80, 40, 80));

        questionLabel.setFont(questionFont);
        questionLabel.setAlignmentX(Component.LEFT_ALIGNMENT); // Left align text

        // Changed from GridLayout to BoxLayout to prevent vertical stretching
        optionsPanel.setLayout(new BoxLayout(optionsPanel, BoxLayout.Y_AXIS));
        optionsPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        // Add to inner container with strict spacing
        contentContainer.add(questionLabel);
        contentContainer.add(Box.createVerticalStrut(40)); // 40px space between question and options
        contentContainer.add(optionsPanel);

        // Anchor inner container to the NORTH (Top) so it never stretches downward
        centerOuter.add(contentContainer, BorderLayout.NORTH);
        add(centerOuter, BorderLayout.CENTER);

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
                final int qIdx = currentIndex;
                
                // M3 FIX: Offload screenshot capture + BLOB insert to background thread
                new Thread(() -> {
                    String msg = "Focus lost while viewing Q" + (qIdx + 1);
                    byte[] screenshotData = takeScreenshotBytes();
                    examAttemptDAO.logEvent(attemptId, msg, screenshotData);
                }).start();

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

    /** Escape HTML entities to prevent injection via question/option text. */
    private static String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;").replace("<", "&lt;")
                   .replace(">", "&gt;").replace("\"", "&quot;");
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
        
        questionLabel.setText("<html><div style='width: 600px;'>Q" + (currentIndex + 1) + ": " + escapeHtml(q.getQuestionText()) + "</div></html>");

        List<Option> opts = q.getOptions();
        optionGroup.clearSelection();
        optionsPanel.removeAll();
        dynamicOptionButtons.clear();
        
        List<Integer> savedSelections = studentAnswerDAO.getStudentSelectedOptions(attemptId, q.getId());
        Font optionFont = new Font("SansSerif", Font.PLAIN, 24);
        
        boolean isMulti = "MULTI".equals(q.getQuestionType());

        for (int i = 0; i < opts.size(); i++) {
            JToggleButton btn = isMulti ? new JCheckBox() : new JRadioButton();
            btn.setFont(optionFont);
            btn.setText("<html><div style='width: 600px;'>" + escapeHtml(opts.get(i).getOptionText()) + "</div></html>");
            if (!isMulti) {
                optionGroup.add(btn);
            }
            if (savedSelections.contains(i + 1)) {
                btn.setSelected(true);
            }
            btn.addActionListener(e -> saveCurrentAnswer());
            
            dynamicOptionButtons.add(btn);
            optionsPanel.add(btn);
            optionsPanel.add(Box.createVerticalStrut(15));
        }
        
        optionsPanel.revalidate();
        optionsPanel.repaint();

        prevButton.setEnabled(currentIndex > 0);
        nextButton.setText(currentIndex == questions.size() - 1 ? "Submit" : "Next");
    }

    private void saveCurrentAnswer() {
        Question q = questions.get(currentIndex);
        List<Integer> selectedOptions = new java.util.ArrayList<>();
        
        for (int i = 0; i < dynamicOptionButtons.size(); i++) {
            if (dynamicOptionButtons.get(i).isSelected()) {
                selectedOptions.add(i + 1);
            }
        }
        
        // ALWAYS save, even if empty — prevents preserving a stale correct answer
        boolean correct = false;
        if (!selectedOptions.isEmpty()) {
            if ("MULTI".equals(q.getQuestionType())) {
                // Use Sets for deduplication and order-independent comparison
                java.util.Set<Integer> selectedSet = new java.util.TreeSet<>(selectedOptions);
                java.util.Set<Integer> correctSet = new java.util.TreeSet<>();
                for (int i = 0; i < q.getOptions().size(); i++) {
                    if (q.getOptions().get(i).isCorrect()) {
                        correctSet.add(i + 1);
                    }
                }
                correct = selectedSet.equals(correctSet);
            } else {
                correct = selectedOptions.size() == 1 && selectedOptions.get(0) == q.getCorrectOptionNumber();
            }
        }
        
        String selectedCsv = selectedOptions.isEmpty() ? ""
            : String.join(",", selectedOptions.stream().map(String::valueOf).toArray(String[]::new));
        studentAnswerDAO.saveAnswer(attemptId, q.getId(), selectedCsv, correct, new Timestamp(System.currentTimeMillis()));
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