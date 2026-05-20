import java.util.ArrayList;

public class Renderer {
    public static void printMap(GameMap map, ArrayList<Team> teams) {
        printMap(map, teams, new ArrayList<PowerUp>());
    }

    public static void printMap(GameMap map, ArrayList<Team> teams, ArrayList<PowerUp> powerUps) {
        char[][] display = new char[map.getRows()][map.getCols()];

        for (int row = 0; row < map.getRows(); row++) {
            for (int col = 0; col < map.getCols(); col++) {
                TileType tile = map.getTile(new Position(row, col));
                if (tile == TileType.WALL) {
                    display[row][col] = '#';
                } else if (tile == TileType.HEALING_POINT) {
                    display[row][col] = '+';
                } else {
                    display[row][col] = '.';
                }
            }
        }

        for (int i = 0; i < powerUps.size(); i++) {
            PowerUp powerUp = powerUps.get(i);
            Position position = powerUp.getPosition();
            if (powerUp.getType() == PowerUpType.HEALTH) {
                display[position.getRow()][position.getCol()] = 'H';
            } else if (powerUp.getType() == PowerUpType.ATTACK) {
                display[position.getRow()][position.getCol()] = 'A';
            } else if (powerUp.getType() == PowerUpType.RANGE) {
                display[position.getRow()][position.getCol()] = 'L';
            } else if (powerUp.getType() == PowerUpType.MEGA_HEALTH) {
                display[position.getRow()][position.getCol()] = 'M';
            } else if (powerUp.getType() == PowerUpType.POWER_CORE) {
                display[position.getRow()][position.getCol()] = 'P';
            } else {
                display[position.getRow()][position.getCol()] = 'C';
            }
        }

        for (int i = 0; i < teams.size(); i++) {
            Team team = teams.get(i);
            ArrayList<Unit> units = team.getUnits();
            for (int j = 0; j < units.size(); j++) {
                Unit unit = units.get(j);
                if (unit.isAlive()) {
                    display[unit.getRow()][unit.getCol()] = team.getSymbol();
                }
            }
        }

        for (int row = 0; row < display.length; row++) {
            String line = "";
            for (int col = 0; col < display[row].length; col++) {
                line = line + display[row][col] + " ";
            }
            System.out.println(line);
        }
    }

    public static void printStatus(ArrayList<Team> teams) {
        System.out.println("Status:");
        for (int i = 0; i < teams.size(); i++) {
            Team team = teams.get(i);
            ArrayList<Unit> units = team.getUnits();
            for (int j = 0; j < units.size(); j++) {
                Unit unit = units.get(j);
                String state = "alive";
                if (!unit.isAlive()) {
                    state = "defeated";
                } else if (unit.isDefending()) {
                    state = "defending";
                }
                System.out.println("  "
                        + team.getName()
                        + "#"
                        + unit.getId()
                        + " "
                        + unit.getName()
                        + " HP "
                        + unit.getHealth()
                        + "/"
                        + unit.getMaxHealth()
                        + " "
                        + state
                        + " at "
                        + unit.getPosition());
            }
        }
    }
}
