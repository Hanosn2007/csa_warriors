import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import javax.swing.JPanel;
import javax.swing.Timer;

public class ArenaPanel extends JPanel {
    private GameEngine engine;
    private final UnitSelectionListener selectionListener;
    private final Timer animationTimer;
    private int selectedUnitId;
    private int cellSize;
    private int boardX;
    private int boardY;
    private TurnAnimation currentAnimation;
    private long animationStartTime;

    public ArenaPanel(GameEngine engine, UnitSelectionListener selectionListener) {
        this.engine = engine;
        this.selectionListener = selectionListener;
        this.animationTimer = new Timer(16, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                repaint();
                if (System.currentTimeMillis() - animationStartTime > 520) {
                    animationTimer.stop();
                    currentAnimation = null;
                    repaint();
                }
            }
        });
        this.selectedUnitId = -1;
        this.cellSize = 40;
        this.boardX = 0;
        this.boardY = 0;
        this.currentAnimation = null;
        this.animationStartTime = 0;
        setBackground(new Color(245, 246, 248));
        setPreferredSize(new Dimension(650, 650));
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                selectAt(event.getX(), event.getY());
            }
        });
    }

    public void setEngine(GameEngine engine) {
        this.engine = engine;
        this.selectedUnitId = -1;
        this.currentAnimation = null;
        this.animationTimer.stop();
        repaint();
    }

    public void setSelectedUnitId(int selectedUnitId) {
        this.selectedUnitId = selectedUnitId;
        repaint();
    }

    public void playAnimation(TurnAnimation animation) {
        currentAnimation = animation;
        animationStartTime = System.currentTimeMillis();
        if (animation != null) {
            animationTimer.restart();
        } else {
            animationTimer.stop();
        }
        repaint();
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);

        Graphics2D g = (Graphics2D) graphics;
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        paintBackground(g);

        GameMap map = engine.getMap();
        int rows = map.getRows();
        int cols = map.getCols();
        int margin = 24;
        int availableWidth = getWidth() - margin * 2;
        int availableHeight = getHeight() - margin * 2;
        cellSize = Math.min(availableWidth / cols, availableHeight / rows);
        boardX = (getWidth() - cellSize * cols) / 2;
        boardY = (getHeight() - cellSize * rows) / 2;

        drawTiles(g, map, rows, cols);
        drawPowerUps(g);
        drawUnits(g);
        drawAnimation(g);
    }

    private void paintBackground(Graphics2D g) {
        GradientPaint paint = new GradientPaint(
                0,
                0,
                new Color(238, 242, 247),
                getWidth(),
                getHeight(),
                new Color(226, 235, 232));
        g.setPaint(paint);
        g.fillRect(0, 0, getWidth(), getHeight());
    }

    private void drawTiles(Graphics2D g, GameMap map, int rows, int cols) {
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                int x = boardX + col * cellSize;
                int y = boardY + row * cellSize;
                Position position = new Position(row, col);
                TileType tile = map.getTile(position);

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
        ArrayList<PowerUp> powerUps = engine.getPowerUps();
        for (int i = 0; i < powerUps.size(); i++) {
            PowerUp powerUp = powerUps.get(i);
            int x = boardX + powerUp.getPosition().getCol() * cellSize;
            int y = boardY + powerUp.getPosition().getRow() * cellSize;
            drawPowerUp(g, powerUp, x, y);
        }
    }

    private void drawPowerUp(Graphics2D g, PowerUp powerUp, int x, int y) {
        int centerX = x + cellSize / 2;
        int centerY = y + cellSize / 2;
        int radius = Math.max(9, cellSize / 5);

        g.setColor(new Color(255, 255, 255, 220));
        g.fillOval(centerX - radius - 4, centerY - radius - 4, (radius + 4) * 2, (radius + 4) * 2);
        g.setColor(colorForPowerUp(powerUp));
        g.fillOval(centerX - radius, centerY - radius, radius * 2, radius * 2);
        g.setColor(new Color(65, 65, 65, 80));
        g.drawOval(centerX - radius, centerY - radius, radius * 2, radius * 2);

        g.setColor(Color.WHITE);
        if (powerUp.getType() == PowerUpType.HEALTH) {
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
            if (powerUp.getType() == PowerUpType.POWER_CORE) {
                g.drawString("P", centerX - 4, centerY + 5);
            }
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

    private Color colorForPowerUp(PowerUp powerUp) {
        if (powerUp.getType() == PowerUpType.HEALTH) {
            return new Color(48, 171, 96);
        }
        if (powerUp.getType() == PowerUpType.MEGA_HEALTH) {
            return new Color(22, 141, 91);
        }
        if (powerUp.getType() == PowerUpType.ATTACK) {
            return new Color(227, 93, 71);
        }
        if (powerUp.getType() == PowerUpType.POWER_CORE) {
            return new Color(172, 55, 50);
        }
        if (powerUp.getType() == PowerUpType.RANGE) {
            return new Color(81, 125, 222);
        }
        return new Color(141, 82, 209);
    }

    private void drawUnits(Graphics2D g) {
        ArrayList<Team> teams = engine.getTeams();
        for (int i = 0; i < teams.size(); i++) {
            Team team = teams.get(i);
            ArrayList<Unit> units = team.getUnits();
            for (int j = 0; j < units.size(); j++) {
                Unit unit = units.get(j);
                if (unit.isAlive()) {
                    drawUnit(g, team, unit);
                }
            }
        }
    }

    private void drawUnit(Graphics2D g, Team team, Unit unit) {
        int x = boardX + unit.getCol() * cellSize;
        int y = boardY + unit.getRow() * cellSize;
        int pad = Math.max(5, cellSize / 8);
        int circleSize = cellSize - pad * 2;

        if (unit.getId() == engine.getLastActingUnitId()) {
            g.setColor(new Color(255, 219, 92));
            g.setStroke(new BasicStroke(4));
            g.drawOval(x + pad - 4, y + pad - 4, circleSize + 8, circleSize + 8);
            g.setStroke(new BasicStroke(1));
        }

        if (unit.getId() == selectedUnitId) {
            g.setColor(new Color(38, 38, 38));
            g.setStroke(new BasicStroke(3));
            g.drawRect(x + 3, y + 3, cellSize - 6, cellSize - 6);
            g.setStroke(new BasicStroke(1));
        }

        g.setColor(colorForTeam(team));
        g.fillOval(x + pad, y + pad, circleSize, circleSize);
        g.setColor(new Color(255, 255, 255));
        g.setStroke(new BasicStroke(2));
        g.drawOval(x + pad, y + pad, circleSize, circleSize);
        g.setStroke(new BasicStroke(1));

        String label = String.valueOf(team.getSymbol());
        g.setFont(new Font("SansSerif", Font.BOLD, Math.max(14, cellSize / 3)));
        FontMetrics metrics = g.getFontMetrics();
        int textX = x + (cellSize - metrics.stringWidth(label)) / 2;
        int textY = y + (cellSize + metrics.getAscent() - metrics.getDescent()) / 2;
        g.drawString(label, textX, textY);

        drawHealthBar(g, unit, x, y);
    }

    private void drawAnimation(Graphics2D g) {
        if (currentAnimation == null) {
            return;
        }

        long elapsed = System.currentTimeMillis() - animationStartTime;
        double progress = Math.min(1.0, elapsed / 520.0);
        int alpha = (int) (210 * (1.0 - progress));
        if (alpha < 0) {
            alpha = 0;
        }

        Position from = currentAnimation.getFrom();
        Position to = currentAnimation.getTo();
        int fromX = boardX + from.getCol() * cellSize + cellSize / 2;
        int fromY = boardY + from.getRow() * cellSize + cellSize / 2;
        int toX = boardX + to.getCol() * cellSize + cellSize / 2;
        int toY = boardY + to.getRow() * cellSize + cellSize / 2;

        if ("ATTACK".equals(currentAnimation.getType())) {
            g.setColor(new Color(222, 73, 63, alpha));
            g.setStroke(new BasicStroke(Math.max(5, cellSize / 8)));
            g.drawLine(fromX, fromY, toX, toY);
            g.setStroke(new BasicStroke(1));
        } else if ("MOVE".equals(currentAnimation.getType())) {
            g.setColor(new Color(49, 125, 220, alpha));
            g.setStroke(new BasicStroke(Math.max(4, cellSize / 10)));
            g.drawLine(fromX, fromY, toX, toY);
            g.fillOval(toX - cellSize / 5, toY - cellSize / 5, cellSize / 3, cellSize / 3);
            g.setStroke(new BasicStroke(1));
        } else if ("POWER".equals(currentAnimation.getType())) {
            int radius = (int) (cellSize * (0.35 + progress * 0.6));
            g.setColor(new Color(244, 183, 64, alpha));
            g.setStroke(new BasicStroke(4));
            g.drawOval(fromX - radius / 2, fromY - radius / 2, radius, radius);
            g.setStroke(new BasicStroke(1));
        } else if ("DEFEND".equals(currentAnimation.getType())) {
            int radius = (int) (cellSize * (0.45 + progress * 0.3));
            g.setColor(new Color(70, 136, 210, alpha));
            g.setStroke(new BasicStroke(4));
            g.drawOval(fromX - radius / 2, fromY - radius / 2, radius, radius);
            g.setStroke(new BasicStroke(1));
        }
    }

    private void drawHealthBar(Graphics2D g, Unit unit, int x, int y) {
        int barX = x + 5;
        int barY = y + 4;
        int barWidth = cellSize - 10;
        int barHeight = Math.max(4, cellSize / 10);
        double percent = (double) unit.getHealth() / unit.getMaxHealth();
        int healthWidth = (int) (barWidth * percent);

        g.setColor(new Color(116, 45, 45));
        g.fillRect(barX, barY, barWidth, barHeight);
        g.setColor(new Color(70, 181, 91));
        g.fillRect(barX, barY, healthWidth, barHeight);
        g.setColor(new Color(255, 255, 255));
        g.drawRect(barX, barY, barWidth, barHeight);
    }

    private Color colorForTeam(Team team) {
        char symbol = team.getSymbol();
        if (symbol == 'R') {
            return new Color(214, 74, 66);
        }
        if (symbol == 'B') {
            return new Color(61, 121, 210);
        }
        if (symbol == 'G') {
            return new Color(54, 150, 94);
        }
        if (symbol == 'Y') {
            return new Color(215, 154, 38);
        }
        if (symbol == 'C') {
            return new Color(36, 129, 116);
        }
        if (symbol == 'O') {
            return new Color(146, 79, 198);
        }
        return new Color(112, 116, 124);
    }

    private void selectAt(int x, int y) {
        int col = (x - boardX) / cellSize;
        int row = (y - boardY) / cellSize;
        if (row < 0 || col < 0 || row >= engine.getMap().getRows() || col >= engine.getMap().getCols()) {
            selectionListener.unitSelected(-1);
            return;
        }

        Unit unit = findLivingUnitAt(row, col);
        if (unit == null) {
            selectionListener.unitSelected(-1);
        } else {
            selectionListener.unitSelected(unit.getId());
        }
    }

    private Unit findLivingUnitAt(int row, int col) {
        ArrayList<Team> teams = engine.getTeams();
        for (int i = 0; i < teams.size(); i++) {
            ArrayList<Unit> units = teams.get(i).getUnits();
            for (int j = 0; j < units.size(); j++) {
                Unit unit = units.get(j);
                if (unit.isAlive() && unit.getRow() == row && unit.getCol() == col) {
                    return unit;
                }
            }
        }
        return null;
    }
}
