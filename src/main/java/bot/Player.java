package bot;

import com.opencsv.bean.CsvBindByName;
import net.dv8tion.jda.api.entities.Member;
import net.rithms.riot.api.RiotApiException;
import net.rithms.riot.api.endpoints.league.dto.LeaguePosition;
import net.rithms.riot.api.endpoints.summoner.dto.Summoner;
import net.rithms.riot.constant.Platform;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Set;

@SuppressWarnings("NullableProblems")
public class Player implements Comparable<Player>
{
    @CsvBindByName(column = "id")
    private String id;

    @CsvBindByName(column = "ign")
    private String ign;

    @CsvBindByName(column = "elo")
    private int elo;

    @CsvBindByName(column = "wins")
    private int wins;

    @CsvBindByName(column = "losses")
    private int losses;

    @CsvBindByName(column = "date")
    private String lastPlayedStr;

    @CsvBindByName(column = "streak")
    private int streak;

    @CsvBindByName(column = "prev")
    private boolean prev;
/*
    @CsvBindByName(column = "rank")
    private String rank;
*/
    private Member member;
    private LocalDate lastPlayed;
    private LocalDate decayDate;

    public Player()
    {

    }

    //Player(String _id, String _ign, int _elo, int _wins, int _losses, int _streak, boolean _prev, String _rank)
    Player(String _id, String _ign, int _elo, int _wins, int _losses, int _streak, boolean _prev)
    {
        lastPlayedStr = "12-31-2020";
        id = _id;
        ign = _ign;
        elo = _elo;
        wins = _wins;
        losses = _losses;
        streak = _streak;
        prev = _prev;
        //rank = _rank;
    }

    void win()
    {
        ++wins;

        if (prev)
            ++streak;
        else
            streak = 1;

        prev = true;
    }

    void wincancel()
    {
        --wins;

        streak = -1;

        prev = false;
    }

    void loss()
    {
        ++losses;

        if (!prev)
            --streak;
        else
            streak = -1;

        prev = false;
    }

    void losscancel()
    {
        --losses;

        streak = -1;

        prev = true;
    }

    void updateIgn(String newIgn)
    {
        ign = newIgn;
    }

    void giveElo(int lp)
    {
        elo += lp;
    }

    void setElo(int lp) {
        elo = lp;
    }

    void setWins(int win)
    {
        wins = win;
    }

    void setLosses(int loss)
    {
        losses = loss;
    }

    void takeElo(int lp)
    {
        elo -= lp;
    }

    int updateElo(int enemyAvg, double score, int friendAvg)
    {
        int old = elo;
        int change;
        double expected = 1 / (1 + Math.pow(10.0, (double)(enemyAvg - old + (1.4 * (2400 - friendAvg))) / 400));

        if (score == 0.0)
            change = (int)(expected - ((15 - (streak / 1.5)) * (2 - (1 - expected))));
        else
            change = (int)(expected + ((15 + (streak / 1.5)) * (2 - expected)));

        if (score == 0.0 && change > 0)
        {
            elo += -change;
            return -change;
        }
        elo += change;

        return change;
    }

    public int getWins()
    {
        return wins;
    }

    public int getLosses()
    {
        return losses;
    }

    public int getElo()
    {
        return elo;
    }

    public String getId()
    {
        return id;
    }

    public String getIgn()
    {
        return ign;
    }

    public boolean getPrev()
    {
        return prev;
    }

    public String getLastPlayedStr()
    {
        return lastPlayedStr;
    }

    public LocalDate getLastPlayed()
    {
        return lastPlayed;
    }

    public int getStreak()
    {
        return streak;
    }
    /*
    public String getRank()
    {
        return rank;
    }

    public void setRank(String _rank)
    {
        rank = _rank;
    }
    */
    Member getMember()
    {
        return member;
    }

    void setLastPlayedStr(String newStr)
    {
        lastPlayedStr = newStr;
    }

    void setMember(Member mem)
    {
        member = mem;
    }

    void decay(int amount)
    {
        elo -= amount;
    }

    void convertDate()
    {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM-d-yyyy");
        formatter = formatter.withLocale(Locale.ENGLISH);
        lastPlayed = LocalDate.parse(lastPlayedStr, formatter);
        decayDate = lastPlayed.plusDays(4);
    }

    void updateDate()
    {
        lastPlayed = LocalDate.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM-d-yyyy");
        lastPlayedStr = lastPlayed.format(formatter);
        decayDate = lastPlayed.plusDays(4);
    }

    LocalDate getDecayDate()
    {
        return decayDate;
    }

    @Override
    public int compareTo(Player player)
    {
        int compareQnt = player.elo;
        return compareQnt - elo;
    }

    @Override
    public String toString()
    {
        return ign;
    }
}
