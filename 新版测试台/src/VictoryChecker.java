import java.util.ArrayList;

public class VictoryChecker {
    public static int countLivingTeams(ArrayList<Team> teams) {
        int count = 0;
        for (int i = 0; i < teams.size(); i++) {
            if (teams.get(i).hasLivingUnits()) {
                count++;
            }
        }
        return count;
    }

    public static String findWinner(ArrayList<Team> teams) {
        String winner = null;
        for (int i = 0; i < teams.size(); i++) {
            Team team = teams.get(i);
            if (team.hasLivingUnits()) {
                if (winner != null) {
                    return null;
                }
                winner = team.getName();
            }
        }
        return winner;
    }

    public static String findWinnerByLivingUnitsThenHealth(ArrayList<Team> teams) {
        String bestTeam = null;
        int bestLivingUnits = -1;
        int bestHealth = -1;
        boolean tied = false;

        for (int i = 0; i < teams.size(); i++) {
            Team team = teams.get(i);
            int livingUnits = countLivingUnits(team);
            int totalHealth = totalLivingHealth(team);
            if (livingUnits > bestLivingUnits
                    || (livingUnits == bestLivingUnits && totalHealth > bestHealth)) {
                bestTeam = team.getName();
                bestLivingUnits = livingUnits;
                bestHealth = totalHealth;
                tied = false;
            } else if (livingUnits == bestLivingUnits && totalHealth == bestHealth) {
                tied = true;
            }
        }

        if (bestLivingUnits <= 0 || tied) {
            return null;
        }
        return bestTeam;
    }

    public static int countLivingUnits(Team team) {
        int count = 0;
        ArrayList<Unit> units = team.getUnits();
        for (int i = 0; i < units.size(); i++) {
            if (units.get(i).isAlive()) {
                count++;
            }
        }
        return count;
    }

    public static int totalLivingHealth(Team team) {
        int total = 0;
        ArrayList<Unit> units = team.getUnits();
        for (int i = 0; i < units.size(); i++) {
            Unit unit = units.get(i);
            if (unit.isAlive()) {
                total += unit.getHealth();
            }
        }
        return total;
    }
}
