package bot;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;

import java.awt.*;
import java.util.*;
import java.util.List;

public class Room
{
    final List<Player> players;
    final List<Player> teamOne;
    final List<Player> teamTwo;
    final Map<Integer, Player> pickPool;
    Member host;
    private int totalElo;
    private int draftNum;
    int spotsLeft;
    int one, two, three, four, five, six, seven, eight;
    private final int ident;
    private boolean drafting;
    private boolean running;
    private boolean active;
    Player captainOne;
    Player captainTwo;
    Player firstPick;
    Player secondPick;
    private Player currentPick;

    enum Team
    {
        ONE,
        TWO
    }

    Room(int delim)
    {
        ident = delim;
        host = null;
        draftNum = 1;
        spotsLeft = 10;
        totalElo = 0;
        players = new ArrayList<>(10);
        teamOne = new ArrayList<>(5);
        teamTwo = new ArrayList<>(5);
        pickPool = new HashMap<>(10);
        drafting = false;
        running = false;
        active = false;
        captainOne = null;
        captainTwo = null;
        firstPick = null;
        secondPick = null;
        currentPick = null;
    }

    void addPlayer(Player player)
    {
        if (spotsLeft > 0)
        {
            players.add(player);
            --spotsLeft;
        }
    }

    boolean removePlayer(String mention)
    {
        for (Player player : players)
        {
            if (player.getMember().getAsMention().equals(mention))
            {
                System.out.println("Kicked (" + player.getIgn() + ")");
                players.remove(player);
                ++spotsLeft;
                return true;
            }
        }

        return false;
    }

    void assignCaptains()
    {
        players.sort(Comparator.comparing(Player::getElo));
        Collections.reverse(players);

        captainOne = players.get(0);
        captainTwo = players.get(1);

        pickPool.put(1, players.get(2));
        pickPool.put(2, players.get(3));
        pickPool.put(3, players.get(4));
        pickPool.put(4, players.get(5));
        pickPool.put(5, players.get(6));
        pickPool.put(6, players.get(7));
        pickPool.put(7, players.get(8));
        pickPool.put(8, players.get(9));
    }

    boolean contains(String id)
    {
        for (Player player : players)
        {
            if (player.getId().equals(id))
            {
                Inhouse.chatChannel.sendMessage(String.format("<@%s> Please wait for your game to finish!", player.getId())).complete();
                return true;
            }
        }

        return false;
    }

    void addPlayerToTeam(Player player, Team team)
    {
        switch (team)
        {
            case ONE:
                teamOne.add(player);
                break;

            case TWO:
                teamTwo.add(player);
                break;
        }
    }

    void repick()
    {
        System.out.println("Repicking");
        if (draftNum == 2)
        {
            Player repick = teamOne.get(1);
            --draftNum;
            pickPool.put(one, repick);
            teamOne.remove(repick);
            setCurrentPick();
        }
        else if (draftNum == 5)
        {
            Player repick = teamOne.get(2);
            --draftNum;
            pickPool.put(four, repick);
            teamOne.remove(repick);
            setCurrentPick();
        }
        else if (draftNum == 6)
        {
            Player repick = teamOne.get(3);
            --draftNum;
            pickPool.put(five, repick);
            teamOne.remove(repick);
            setCurrentPick();
        }
        else if (draftNum == 9)
        {
            Player repick = teamOne.get(4);
            --draftNum;
            drafting = true;
            pickPool.put(eight, repick);
            teamOne.remove(repick);
            setCurrentPick();
        }
        else if (draftNum == 3)
        {
            Player repick = teamTwo.get(1);
            --draftNum;
            pickPool.put(two, repick);
            teamTwo.remove(repick);
            setCurrentPick();
        }
        else if (draftNum == 4)
        {
            Player repick = teamTwo.get(2);
            --draftNum;
            pickPool.put(three, repick);
            teamTwo.remove(repick);
            setCurrentPick();
        }
        else if (draftNum == 7)
        {
            Player repick = teamTwo.get(3);
            --draftNum;
            pickPool.put(six, repick);
            teamTwo.remove(repick);
            setCurrentPick();
        }
        else if (draftNum == 8)
        {
            Player repick = teamTwo.get(4);
            --draftNum;
            pickPool.put(seven, repick);
            teamTwo.remove(repick);
            setCurrentPick();
        }
    }

    boolean addPlayerToTeam(int pick)
    {
        Player picked = pickPool.get(pick);

        if (picked != null)
        {
            if ((draftNum == 1) || (draftNum == 4) || (draftNum == 5) || (draftNum == 8))
            {
                teamOne.add(picked);
            }
            else if ((draftNum == 2) || (draftNum == 3) || (draftNum == 6) || (draftNum == 7))
            {
                teamTwo.add(picked);
            }

            pickPool.remove(pick);
            System.out.println(draftNum + "(" + picked.getIgn() + ")");

            if (draftNum <= 7)
            {
                ++draftNum;
                setCurrentPick();
            }
            else
            {
                ++draftNum;
                drafting = false;
            }
            return true;
        }

        return false;
    }

    int averageElo(Team team)
    {
        if (team == Team.ONE)
        {
            int sum = 0;
            for (Player player : teamOne)
            {
                sum += player.getElo();
            }
            return (sum / 5);
        }
        else
        {
            int sum = 0;
            for (Player player : teamTwo)
            {
                sum += player.getElo();
            }
            return (sum / 5);
        }
    }

    MessageEmbed teamEmbed(Team team)
    {
        EmbedBuilder teamsEmbed = new EmbedBuilder();
        teamsEmbed.setColor(new Color(0, 153, 255));

        if (team == Team.ONE)
        {
            teamsEmbed.setTitle("Team One");
            teamsEmbed.addField("__Blue Side Final Player Roster__",
                    "```\n1. " + teamOne.get(0).getIgn() + "\n" +
                            "2. " + teamOne.get(1).getIgn() + "\n" +
                            "3. " + teamOne.get(2).getIgn() + "\n" +
                            "4. " + teamOne.get(3).getIgn() + "\n" +
                            "5. " + teamOne.get(4).getIgn() + "\n```"
                    , false);
            teamsEmbed.setFooter("Captain: " + firstPick.getIgn(),
                    firstPick.getMember().getUser().getEffectiveAvatarUrl());
        }
        else
        {
            teamsEmbed.setTitle("Team Two");
            teamsEmbed.addField("__Red Side Final Player Roster__",
                    "```\n1. " + teamTwo.get(0).getIgn() + "\n" +
                            "2. " + teamTwo.get(1).getIgn() + "\n" +
                            "3. " + teamTwo.get(2).getIgn() + "\n" +
                            "4. " + teamTwo.get(3).getIgn() + "\n" +
                            "5. " + teamTwo.get(4).getIgn() + "\n```"
                    , false);
            teamsEmbed.setFooter("Captain: " + secondPick.getIgn(),
                    secondPick.getMember().getUser().getEffectiveAvatarUrl());
        }

        return teamsEmbed.build();
    }

    private void setCurrentPick()
    {
        if ((draftNum == 1) || (draftNum == 4) || (draftNum == 5) || (draftNum == 8))
        {
            currentPick = firstPick;
        }
        else if ((draftNum == 2) || (draftNum == 3) || (draftNum == 6) || (draftNum == 7))
        {
            currentPick = secondPick;
        }
    }

    void setCurrentPick(Player player)
    {
        currentPick = player;
    }

    Player getCurrentPick()
    {
        return currentPick;
    }

    int getNum()
    {
        return ident;
    }

    int getTotalElo()
    {
        return totalElo;
    }

    int getBlueElo()
    {
        int elo = 0;
        for (Player p : teamOne)
            elo += p.getElo();
        return elo;
    }

    int getRedElo()
    {
        int elo = 0;
        for (Player p : teamTwo)
            elo += p.getElo();
        return elo;
    }

    void setTotalElo()
    {
        for (Player p : players)
            totalElo += p.getElo();
    }

    Member getHost()
    {
        return host;
    }

    boolean isRunning()
    {
        return running;
    }

    boolean isDrafting()
    {
        return drafting;
    }

    boolean isActive() { return active; }

    boolean isFull()
    {
        return spotsLeft <= 0;
    }

    void setDrafting()
    {
        drafting = true;
    }

    void setRunning()
    {
        running = true;
    }

    void setActive()
    {
        active = true;
    }

    void clear()
    {
        host = null;
        draftNum = 1;
        spotsLeft = 10;
        totalElo = 0;
        players.clear();
        teamOne.clear();
        teamTwo.clear();
        pickPool.clear();
        drafting = false;
        running = false;
        active = false;
        captainOne = null;
        captainTwo = null;
        firstPick = null;
        secondPick = null;
        currentPick = null;
    }

    void redraft()
    {
        draftNum = 1;
        totalElo = 0;
        teamOne.clear();
        teamTwo.clear();
        pickPool.clear();
        drafting = false;
        running = false;
        active = false;
        captainOne = null;
        captainTwo = null;
        firstPick = null;
        secondPick = null;
        currentPick = null;
    }
}
