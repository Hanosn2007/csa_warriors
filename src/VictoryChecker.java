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
}
