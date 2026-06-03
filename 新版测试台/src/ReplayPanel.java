import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.util.ArrayList;
import javax.swing.JPanel;

public class ReplayPanel extends JPanel {
    private ReplayMatch match;
    private ReplaySnapshot snapshot;
    private int cellSize;
    private int boardX;
    private int boardY;

    public ReplayPanel() {
        setBackground(new Color(245, 246, 248));
        cellSize = 40;
        boardX = 0;
        boardY = 0;
    }

    public void setSnapshot(ReplayMatch match, ReplaySnapshot snapshot) {
        this.match = match;
        this.snapshot = snapshot;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics;
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        paintBackground(g);

        if (match == null || snapshot == null) {
            g.setColor(new Color(35, 42, 52));
            g.drawString("选择一局后开始回放。", 20, 28);
            return;
        }

        int rows = match.getRows();
        int cols = match.getCols();
        int margin = 24;
        int availableWidth = getWidth() - margin * 2;
        int availableHeight = getHeight() - margin * 2;
        cellSize = Math.min(availableWidth / cols, availableHeight / rows);
        boardX = (getWidth() - cellSize * cols) / 2;
        boardY = (getHeight() - cellSize * rows) / 2;

        drawTiles(g, rows, cols);
        drawPowerUps(g);
        drawUnits(g);
    }

    private void paintBackground(Graphics2D g) {
        g.setColor(new Color(232, 239, 243));
        g.fillRect(0, 0, getWidth(), getHeight());
    }

    private void drawTiles(Graphics2D g, int rows, int cols) {
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                int x = boardX + col * cellSize;
                int y = boardY + row * cellSize;
                TileType tile = match.getTile(row, col);

                if (tile == TileType.WALL) {
                    g.setColor(new Color(62, 68, 78));
                } else if (tile == TileType.HEALING_POINT) {
                    g.setColor(new Color(218, 247, 225));
                } else {
                    g.setColor(new Color(255, 253, 247));
                }
                g.fillRoundRect(x + 1, y + 1, cellSize - 2, cellSize - 2, 10, 10);

                g.setColor(new Color(203, 210, 216));
                g.drawRoundRect(x + 1, y + 1, cellSize - 2, cellSize - 2, 10, 10);

                if (tile == TileType.HEALING_POINT) {
                    drawHealingMark(g, x, y);
                }
            }
        }
    }

    private void drawHealingMark(Graphics2D g, int x, int y) {
        int centerX = x + cellSize / 2;
        int centerY = y + cellSize / 2;
        int size = Math.max(8, cellSize / 5);
        g.setColor(new Color(57, 155, 84));
        g.setStroke(new BasicStroke(Math.max(2, cellSize / 18)));
        g.drawLine(centerX - size, centerY, centerX + size, centerY);
        g.drawLine(centerX, centerY - size, centerX, centerY + size);
        g.setStroke(new BasicStroke(1));
    }

    private void drawPowerUps(Graphics2D g) {
        ArrayList<ReplayPowerUpState> powerUps = snapshot.getPowerUps();
        for (int i = 0; i < powerUps.size(); i++) {
            ReplayPowerUpState powerUp = powerUps.get(i);
            int x = boardX + powerUp.getCol() * cellSize;
            int y = boardY + powerUp.getRow() * cellSize;
            drawPowerUp(g, powerUp, x, y);
        }
    }

    private void drawPowerUp(Graphics2D g, ReplayPowerUpState powerUp, int x, int y) {
        int centerX = x + cellSize / 2;
        int centerY = y + cellSize / 2;
        int radius = Math.max(9, cellSize / 5);

        g.setColor(new Color(255, 255, 255, 220));
        g.fillOval(centerX - radius - 4, centerY - radius - 4, (radius + 4) * 2, (radius + 4) * 2);
        g.setColor(colorForPowerUp(powerUp.getType()));
        g.fillOval(centerX - radius, centerY - radius, radius * 2, radius * 2);
        g.setColor(new Color(65, 65, 65, 80));
        g.drawOval(centerX - radius, centerY - radius, radius * 2, radius * 2);

        g.setColor(Color.WHITE);
        if (powerUp.getType() == PowerUpType.HEALTH || powerUp.getType() == PowerUpType.MEGA_HEALTH) {
            int size = Math.max(5, radius / 2);
            g.setStroke(new BasicStroke(3));
            g.drawLine(centerX - size, centerY, centerX + size, centerY);
            g.drawLine(centerX, centerY - size, centerX, centerY + size);
            g.setStroke(new BasicStroke(1));
        } else if (powerUp.getType() == PowerUpType.ATTACK || powerUp.getType() == PowerUpType.POWER_CORE) {
            Polygon polygon = new Polygon();
            polygon.addPoint(centerX, centerY - radius + 4);
            polygon.addPoint(centerX + radius - 4, centerY + radius - 4);
            polygon.addPoint(centerX - radius + 4, centerY + radius - 4);
            g.fillPolygon(polygon);
        } else if (powerUp.getType() == PowerUpType.RANGE) {
            g.setStroke(new BasicStroke(3));
            g.drawOval(centerX - radius / 2, centerY - radius / 2, radius, radius);
            g.drawLine(centerX + radius / 3, centerY + radius / 3, centerX + radius, centerY + radius);
            g.setStroke(new BasicStroke(1));
        } else {
            g.setFont(new Font("SansSerif", Font.BOLD, Math.max(12, radius)));
            FontMetrics metrics = g.getFontMetrics();
            String text = "C";
            g.drawString(text, centerX - metrics.stringWidth(text) / 2, centerY + metrics.getAscent() / 3);
        }
    }

    private void drawUnits(Graphics2D g) {
        ArrayList<ReplayUnitState> units = snapshot.getUnits();
        for (int i = 0; i < units.size(); i++) {
            ReplayUnitState unit = units.get(i);
            if (unit.isAlive()) {
                drawUnit(g, unit);
            }
        }
    }

    private void drawUnit(Graphics2D g, ReplayUnitState unit) {
        int x = boardX + unit.getCol() * cellSize;
        int y = boardY + unit.getRow() * cellSize;
        int pad = Math.max(5, cellSize / 8);
        int circleSize = cellSize - pad * 2;

        if (unit.getId() == snapshot.getLastActingUnitId()) {
            g.setColor(new Color(255, 219, 92));
            g.setStroke(new BasicStroke(4));
            g.drawOval(x + pad - 4, y + pad - 4, circleSize + 8, circleSize + 8);
            g.setStroke(new BasicStroke(1));
        }

        g.setColor(colorForTeam(unit.getTeamName()));
        g.fillOval(x + pad, y + pad, circleSize, circleSize);
        g.setColor(new Color(255, 255, 255));
        g.setStroke(new BasicStroke(2));
        g.drawOval(x + pad, y + pad, circleSize, circleSize);
        g.setStroke(new BasicStroke(1));

        String label = unit.getTeamName().substring(0, 1);
        g.setFont(new Font("SansSerif", Font.BOLD, Math.max(14, cellSize / 3)));
        FontMetrics metrics = g.getFontMetrics();
        int textX = x + (cellSize - metrics.stringWidth(label)) / 2;
        int textY = y + (cellSize + metrics.getAscent() - metrics.getDescent()) / 2;
        g.drawString(label, textX, textY);
        drawHealthBar(g, unit, x, y);
    }

    private void drawHealthBar(Graphics2D g, ReplayUnitState unit, int x, int y) {
        int barX = x + 5;
        int barY = y + 4;
        int barWidth = cellSize - 10;
        int barHeight = Math.max(4, cellSize / 10);
        double percent = 0.0;
        if (unit.getMaxHealth() > 0) {
            percent = (double) unit.getHealth() / unit.getMaxHealth();
        }
        int healthWidth = (int) (barWidth * percent);

        g.setColor(new Color(116, 45, 45));
        g.fillRect(barX, barY, barWidth, barHeight);
        g.setColor(new Color(70, 181, 91));
        g.fillRect(barX, barY, healthWidth, barHeight);
        g.setColor(new Color(255, 255, 255));
        g.drawRect(barX, barY, barWidth, barHeight);
    }

    private Color colorForPowerUp(PowerUpType type) {
        if (type == PowerUpType.HEALTH) {
            return new Color(48, 171, 96);
        }
        if (type == PowerUpType.MEGA_HEALTH) {
            return new Color(22, 141, 91);
        }
        if (type == PowerUpType.ATTACK) {
            return new Color(227, 93, 71);
        }
        if (type == PowerUpType.POWER_CORE) {
            return new Color(172, 55, 50);
        }
        if (type == PowerUpType.RANGE) {
            return new Color(81, 125, 222);
        }
        return new Color(141, 82, 209);
    }

    private Color colorForTeam(String teamName) {
        if ("Red".equals(teamName)) {
            return new Color(214, 74, 66);
        }
        if ("Blue".equals(teamName)) {
            return new Color(61, 121, 210);
        }
        if ("Green".equals(teamName)) {
            return new Color(54, 150, 94);
        }
        if ("Yellow".equals(teamName)) {
            return new Color(215, 154, 38);
        }
        return new Color(112, 116, 124);
    }
}
