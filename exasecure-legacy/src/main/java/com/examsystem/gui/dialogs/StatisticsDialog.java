package com.examsystem.gui.dialogs;

import com.examsystem.models.AttemptSummary;
import com.examsystem.models.QuestionStatistic;
import com.examsystem.services.AdminService;
import java.awt.*;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.swing.*;

public class StatisticsDialog extends JDialog {

    // Updated Constructor to accept Service and Filters
    public StatisticsDialog(Window owner, AdminService service, int examId, String dateFilter, String sectionFilter, List<AttemptSummary> data) {
        super(owner, "Exam Statistics & Analysis", Dialog.ModalityType.APPLICATION_MODAL);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        
        setSize(900, 600);
        setLocationRelativeTo(owner);
        
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Score Distribution", new ScoreDistributionPanel(data));
        tabs.addTab("Section Performance", new SectionPerformancePanel(data));
        
        // Fetch specific question stats based on the filters selected in the main window
        List<QuestionStatistic> qStats = service.getQuestionStatistics(examId, dateFilter, sectionFilter);
        tabs.addTab("Question Analysis", new QuestionAnalysisPanel(qStats));
        
        add(tabs);
    }

    // --- Chart 1: Score Distribution ---
    private static class ScoreDistributionPanel extends JPanel {
        private final int[] buckets = new int[5]; 

        public ScoreDistributionPanel(List<AttemptSummary> data) {
            for (AttemptSummary s : data) {
                int score = s.getScore();
                if (score == 0) buckets[0]++;
                else if (score == 1) buckets[1]++;
                else if (score == 2) buckets[2]++;
                else if (score >= 3) buckets[3]++;
            }
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();
            int barWidth = w / 5;
            int maxVal = 1; 
            for(int b : buckets) maxVal = Math.max(maxVal, b);

            String[] labels = {"0", "1", "2", "3+", ""};
            Color[] colors = {Color.RED, Color.ORANGE, Color.YELLOW, Color.GREEN, Color.BLUE};

            g2.setFont(new Font("SansSerif", Font.BOLD, 14));
            g2.drawString("Score Distribution (Students vs Score)", 20, 30);

            for (int i = 0; i < 4; i++) {
                int barHeight = (int) ((double) buckets[i] / maxVal * (h - 100));
                int x = 50 + (i * (barWidth + 10));
                int y = h - 50 - barHeight;

                g2.setColor(colors[i]);
                g2.fillRect(x, y, barWidth, barHeight);
                g2.setColor(Color.BLACK);
                g2.drawRect(x, y, barWidth, barHeight);
                
                g2.setColor(Color.BLACK);
                g2.drawString(labels[i] + " Pts", x + (barWidth/2) - 15, h - 30);
                g2.drawString(String.valueOf(buckets[i]), x + (barWidth/2) - 5, y - 5);
            }
        }
    }

    // --- Chart 2: Section Comparison ---
    private static class SectionPerformancePanel extends JPanel {
        private final Map<String, Double> sectionAverages;

        public SectionPerformancePanel(List<AttemptSummary> data) {
            sectionAverages = data.stream()
                .filter(s -> s.getSection() != null && !s.getSection().isEmpty())
                .collect(Collectors.groupingBy(
                    AttemptSummary::getSection,
                    Collectors.averagingInt(AttemptSummary::getScore)
                ));
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();
            
            g2.setFont(new Font("SansSerif", Font.BOLD, 14));
            g2.drawString("Average Score by Section", 20, 30);
            
            if (sectionAverages.isEmpty()) {
                g2.drawString("No Section Data Available", w/2 - 80, h/2);
                return;
            }

            int x = 50;
            int barWidth = 100;

            for (Map.Entry<String, Double> entry : sectionAverages.entrySet()) {
                String section = entry.getKey();
                double avg = entry.getValue(); 
                int barHeight = (int) (avg * 60); 

                g2.setColor(new Color(100, 149, 237)); // Cornflower Blue
                g2.fillRect(x, h - 50 - barHeight, barWidth, barHeight);
                g2.setColor(Color.BLACK);
                g2.drawRect(x, h - 50 - barHeight, barWidth, barHeight);
                
                g2.drawString("Sec " + section, x + 30, h - 30);
                g2.drawString(String.format("Avg: %.1f", avg), x + 25, h - 55 - barHeight);
                
                x += 150;
            }
        }
    }

    // --- Chart 3: NEW Question Analysis ---
    private static class QuestionAnalysisPanel extends JPanel {
        private final List<QuestionStatistic> stats;

        public QuestionAnalysisPanel(List<QuestionStatistic> stats) {
            this.stats = stats;
            // Scrollable if many questions? 
            // For simple drawing, we assume it fits or user resizes.
            // A real JScrollPane approach requires custom Component UI, keeping it simple here.
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth();
            // Variable 'h' removed here
            
            g2.setFont(new Font("SansSerif", Font.BOLD, 14));
            String title = "Question Analysis (Green = Correct, Gray = Total)";
            if (stats.isEmpty()) title += " - NO DATA";
            g2.drawString(title, 20, 30);

            int y = 60;
            int rowHeight = 40;
            int barMaxLen = w - 300; // space for text

            // Find max total for scaling
            int maxTotal = 1;
            for(QuestionStatistic qs : stats) maxTotal = Math.max(maxTotal, qs.getTotalAttempts());

            int qNum = 1;
            for (QuestionStatistic qs : stats) {
                // Draw Question Text (truncated)
                g2.setColor(Color.BLACK);
                g2.setFont(new Font("SansSerif", Font.PLAIN, 12));
                String qText = "Q" + qNum + ": " + qs.getQuestionText();
                if (qText.length() > 30) qText = qText.substring(0, 27) + "...";
                g2.drawString(qText, 20, y + 20);

                // Draw Total Bar (Gray Background)
                int totalLen = (int) ((double) qs.getTotalAttempts() / maxTotal * barMaxLen);
                int correctLen = (int) ((double) qs.getCorrectCount() / maxTotal * barMaxLen);

                int barX = 250;
                int barY = y + 5;
                int barH = 20;

                // Background (Total Attempts)
                g2.setColor(Color.LIGHT_GRAY);
                g2.fillRect(barX, barY, totalLen, barH);
                
                // Foreground (Correct Attempts)
                g2.setColor(new Color(60, 179, 113)); // Medium Sea Green
                g2.fillRect(barX, barY, correctLen, barH);
                
                // Outline
                g2.setColor(Color.GRAY);
                g2.drawRect(barX, barY, totalLen, barH);

                // Text Stats
                g2.setColor(Color.BLACK);
                String statText = qs.getCorrectCount() + "/" + qs.getTotalAttempts() + " (" + (int)qs.getCorrectPercentage() + "%)";
                g2.drawString(statText, barX + totalLen + 10, y + 20);

                y += rowHeight;
                qNum++;
            }
        }
    }
}