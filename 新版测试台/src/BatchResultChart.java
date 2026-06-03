import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.util.ArrayList;
import javax.swing.JPanel;

public class BatchResultChart extends JPanel {
    private BatchSimulationSummary summary;

    public BatchResultChart() {
        setBackground(new Color(253, 253, 251));
    }

    public void setSummary(BatchSimulationSummary summary) {
        this.summary = summary;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics;
        g.setFont(new Font("SansSerif", Font.PLAIN, 12));
        g.setColor(new Color(35, 42, 52));

        if (summary == null || summary.getTeamStats().size() == 0) {
            g.drawString("输入测试轮数后点击“开始连测”，这里会显示胜场和平均分柱状图。", 16, 28);
            return;
        }

        ArrayList<BatchTeamStats> stats = summary.getTeamStats();
        int width = getWidth();
        int left = 90;
        int top = 28;
        int rowHeight = 42;
        int maxBarWidth = Math.max(80, width - left - 130);
        int maxWins = 1;
        double maxAverage = 1.0;

        for (int i = 0; i < stats.size(); i++) {
            BatchTeamStats stat = stats.get(i);
            maxWins = Math.max(maxWins, stat.getWins());
            maxAverage = Math.max(maxAverage, stat.getAverageScore(summary.getCompletedMatches()));
        }

        g.setFont(new Font("SansSerif", Font.BOLD, 12));
        g.drawString("胜场", left, top - 8);
        g.drawString("平均分", left, top + stats.size() * rowHeight + 22);
        g.setFont(new Font("SansSerif", Font.PLAIN, 12));

        for (int i = 0; i < stats.size(); i++) {
            BatchTeamStats stat = stats.get(i);
            int y = top + i * rowHeight;
            g.setColor(new Color(35, 42, 52));
            g.drawString(stat.getTeamName(), 14, y + 18);

            int winWidth = (int) (stat.getWins() * 1.0 / maxWins * maxBarWidth);
            g.setColor(colorForTeam(stat.getTeamName()));
            g.fillRect(left, y, winWidth, 18);
            g.setColor(new Color(35, 42, 52));
            g.drawString(stat.getWins() + " 胜", left + winWidth + 8, y + 14);
        }

        int scoreTop = top + stats.size() * rowHeight + 42;
        for (int i = 0; i < stats.size(); i++) {
            BatchTeamStats stat = stats.get(i);
            int y = scoreTop + i * rowHeight;
            g.setColor(new Color(35, 42, 52));
            g.drawString(stat.getTeamName(), 14, y + 18);

            double average = stat.getAverageScore(summary.getCompletedMatches());
            int scoreWidth = (int) (average / maxAverage * maxBarWidth);
            g.setColor(colorForTeam(stat.getTeamName()).darker());
            g.fillRect(left, y, scoreWidth, 18);
            g.setColor(new Color(35, 42, 52));
            g.drawString(String.format("%.1f", average), left + scoreWidth + 8, y + 14);
        }
    }

    private Color colorForTeam(String teamName) {
        if ("Red".equals(teamName)) {
            return new Color(205, 65, 57);
        }
        if ("Blue".equals(teamName)) {
            return new Color(56, 112, 208);
        }
        if ("Green".equals(teamName)) {
            return new Color(51, 152, 94);
        }
        if ("Yellow".equals(teamName)) {
            return new Color(217, 153, 25);
        }
        return new Color(96, 103, 112);
    }
}
