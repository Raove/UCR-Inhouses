package bot;

import net.dv8tion.jda.api.*;
import net.dv8tion.jda.api.entities.*;
import net.rithms.riot.api.ApiConfig;
import net.rithms.riot.api.RiotApi;
import net.rithms.riot.api.RiotApiException;
import net.rithms.riot.api.endpoints.league.dto.LeaguePosition;
import net.rithms.riot.api.endpoints.summoner.dto.Summoner;
import net.rithms.riot.constant.Platform;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.List;

public class Hub
{
    private final Leaderboard ladder = new Leaderboard();
    private final Blacklist blist;

    private final Stack<List<Player>> backups;

    private final RiotApi api;

    private boolean pendingHostInput;
    private boolean pendingPickorderInput;
    private boolean pendingDraftInput;
    private boolean pendingConfirm;

    boolean bluewin;
    public int picknum;

    private Message msgToDelete;
    private Message hostMessage;
    private Message message1;
    private Message opgg;
    public Message hostPing;
    private Message rosterMessage;
    private Message rosterPing;
    private Message pickorderMessage;
    public int activeRooms;
    public boolean currentActiveHost;

    private final Room rooms[];
    private Room currentRoom;
    private Room fakeroom;

    String iconLink = "https://i.imgur.com/uJCE6CI.png";
    LocalDate ldate;

    enum Input
    {
        HOST,
        PICKORDER,
        DRAFT,
        NONE
    }

    Hub()
    {
        //Setup Blacklist
        blist = new Blacklist("blacklist.txt");

        //Setup League API.
        ApiConfig config = new ApiConfig().setKey("RGAPI-3487139b-9678-4c64-a7be-cf3ab9964861");
        api = new RiotApi(config);

        //Create and assign the three rooms.
        rooms = new Room[3];
        rooms[0] = new Room(1);
        rooms[1] = new Room(2);
        rooms[2] = new Room(3);

        fakeroom = new Room(4);

        //Stack for leaderboard backups.
        backups = new Stack<>();

        //State Variables.
        pendingHostInput = false;
        pendingPickorderInput = false;
        pendingDraftInput = false;
        pendingConfirm = false;

        //Objects to hold and declareWinner messages send by bot
        hostMessage = null;
        message1 = null;
        hostPing = null;
        rosterMessage = null;
        rosterPing = null;
        msgToDelete = null;
        pickorderMessage = null;

        currentRoom = rooms[0];
        currentActiveHost = false;
        activeRooms = 0;

        //Push the current ladder onto the backup stack
        backup();
    }

    void host(Member host, Message message, boolean popMessage)
    {
        if (!currentActiveHost && activeRooms < 3)
        {
            System.out.println("Host made by (" + message.getMember().getUser().getName() + ")");
            currentActiveHost = true;
            setCurrentRoom();
            currentRoom.host = host;
            currentRoom.setActive();

            if (popMessage)
            {
                ++activeRooms;
                updateHostEmbed();
            }
        }
    }

    void host(Member host, Message message, String ign)
    {
        String realIgn = validateIgn(ign);
        currentRoom.host = host;

        if (!currentActiveHost && activeRooms <3)
        {
            if (!realIgn.equals(""))
            {
                host(host, message, false);
                if (!ladder.exists(host.getUser().getId()))
                {
                    Player player = new Player(host.getUser().getId(), realIgn, 2400, 0, 0, 0, false/*, getRank(realIgn)*/);
                    Inhouse.chatChannel.sendMessage(String.format("<@%s> registered to ladder with **%s**.",
                            host.getUser().getId(), realIgn)).complete();
                    currentRoom.addPlayer(player);
                    ladder.addPlayerToLadder(player);
                    System.out.println("Added (" + player.getIgn() + ") to the ladder");
                    player.setMember(host);
                }
                else
                {
                    currentRoom.addPlayer(ladder.getPlayer(host.getUser().getId()));
                    ladder.getPlayer(host.getUser().getId()).setMember(host);
                    ladder.getPlayer(host.getUser().getId()).updateIgn(realIgn);
                }
                ++activeRooms;
                updateHostEmbed();
            }
        }
    }

    void join(Member member)
    {
        if(!currentActiveHost){
            return;
        }

        if (blist.check(member.getUser().getId()))
        {
            Inhouse.chatChannel.sendMessage(String.format("%s This account has been blacklisted due to a previous ban.",
                    member.getAsMention())).complete();
            return;
        }
        if (currentActiveHost && !currentRoom.isFull()) //&& !currentRoom.contains(member.getUser().getId()) &&
                //!rooms[0].contains(member.getUser().getId()) && !rooms[1].contains(member.getUser().getId()) &&
                //!rooms[2].contains(member.getUser().getId()) && !rooms[3].contains(member.getUser().getId())) //CHECK FOR DOUBLE
        {
            if (!ladder.exists(member.getUser().getId()))
            {
                Inhouse.chatChannel.sendMessage(String.format("%s You're attempting to join a game without having an ign " +
                                "registered, please use ``!register ign`` here first or join with ``!ign ign``."
                        , member.getAsMention())).complete();
            }
            else
            {
                currentRoom.addPlayer(ladder.getPlayer(member.getUser().getId()));
                ladder.getPlayer(member.getUser().getId()).setMember(member);
            }
            updateHostEmbed();

            if (currentRoom.isFull())
                full();
        }
    }

    void changeName(String message)
    {
        String msgArray[] = message.split(" ");
        StringBuilder ign = new StringBuilder("");
        if (msgArray.length > 2)
        {
            String id = msgArray[1].replaceAll("[^0-9]", "");
            if (ladder.exists(id))
            {
                for (int i = 0; i < msgArray.length; ++i)
                {
                    if (i > 1)
                    {
                        ign.append(msgArray[i]);
                    }
                }

                String realIgn = validateIgn(ign.toString());
                if (!realIgn.equals(""))
                {
                    Player player = ladder.getPlayer(id);
                    String oldName = player.getIgn();
                    player.updateIgn(realIgn);
                    Inhouse.chatChannel.sendMessage(String.format("<@%s> changed from **%s** to **%s**.",
                            id, oldName, realIgn)).complete();
                    Inhouse.chatChannel.sendMessage(String.format("<@%s> changed from **%s** to **%s**.",
                            id, oldName, realIgn)).complete();
                    ladder.write();
                }
            }
        }
    }

    void setElo(String message, Message msg) {

        String msgArray[] = message.split(" ");
        String id = msg.getAuthor().getId();
        if (msgArray.length > 1) {
            id = msgArray[1].replaceAll("[^\\d]", "");
        }
        int elo = Integer.parseInt(msgArray[2]);

        if (ladder.exists(id)) {
            List<Player> listCopy = new ArrayList<>(ladder.getLadder());
            listCopy.sort(Comparator.comparing(Player::getElo));
            Collections.reverse(listCopy);

            for (int i = 0; i < listCopy.size(); ++i) {
                if (listCopy.get(i).getId().equals(id)) {
                    Player local = listCopy.get(i);
                    local.setElo(elo);
                    msg.getChannel().sendMessage(String.format("Discord user <@%s> elo has been set to %s!.",
                            id, elo)).complete();
                    ladder.write();
                }
            }
        }
    }

    void setWin(String message, Message msg) {

        String msgArray[] = message.split(" ");
        String id = msg.getAuthor().getId();
        if (msgArray.length > 1) {
            id = msgArray[1].replaceAll("[^\\d]", "");
        }
        int elo = Integer.parseInt(msgArray[2]);

        if (ladder.exists(id)) {
            List<Player> listCopy = new ArrayList<>(ladder.getLadder());
            listCopy.sort(Comparator.comparing(Player::getElo));
            Collections.reverse(listCopy);

            for (int i = 0; i < listCopy.size(); ++i) {
                if (listCopy.get(i).getId().equals(id)) {
                    Player local = listCopy.get(i);
                    local.setWins(elo);
                    msg.getChannel().sendMessage(String.format("Discord user <@%s> now has %s wins!.",
                            id, elo)).complete();
                    ladder.write();
                }
            }
        }
    }

    void setLoss(String message, Message msg) {

        String msgArray[] = message.split(" ");
        String id = msg.getAuthor().getId();
        if (msgArray.length > 1) {
            id = msgArray[1].replaceAll("[^\\d]", "");
        }
        int elo = Integer.parseInt(msgArray[2]);

        if (ladder.exists(id)) {
            List<Player> listCopy = new ArrayList<>(ladder.getLadder());
            listCopy.sort(Comparator.comparing(Player::getElo));
            Collections.reverse(listCopy);

            for (int i = 0; i < listCopy.size(); ++i) {
                if (listCopy.get(i).getId().equals(id)) {
                    Player local = listCopy.get(i);
                    local.setLosses(elo);
                    msg.getChannel().sendMessage(String.format("Discord user <@%s> now has %s losses!.",
                            id, elo)).complete();
                    ladder.write();
                }
            }
        }
    }


    void blacklist(Member member, String message, Message msg)
    {
        String array[] = message.split(" ");
        StringBuilder tag = new StringBuilder();
        String display = "";
        boolean ign = false;

        if (array.length > 1)
        {
            if (array[1].startsWith("<@"))
            {
                tag = new StringBuilder(array[1].replaceAll("[^0-9]", ""));
            }
            else
            {
                for (int i = 0; i < array.length; ++i)
                    if (i != 0)
                        tag.append(array[i]);

                display = validateIgn(tag.toString());
                if (display.equals(""))
                    return;
                else
                    ign = true;
            }

            if (!blist.check(tag.toString()))
            {
                blist.add(tag.toString().toLowerCase().replaceAll("\\s+", ""));

                if (ign)
                    msg.getChannel().sendMessage(String.format("Summoner **%s** has been blacklisted.",
                            display)).complete();
                else
                    msg.getChannel().sendMessage(String.format("Discord user <@%s> has been blacklisted.",
                            tag.toString())).complete();
            }
            else
            {
                msg.getChannel().sendMessage(String.format("%s This account is already blacklisted.",
                        member.getAsMention())).complete();
            }
        }
    }

    void remove(Member member, String message, Message msg)
    {
        String array[] = message.split(" ");
        String tag = "";
        String display = "";
        boolean ign = false;

        if (array.length > 1)
        {
            if (array[1].startsWith("<@"))
            {
                tag = array[1].replaceAll("[^0-9]", "");
            }
            else
            {
                for (int i = 0; i < array.length; ++i)
                    if (i != 0)
                        tag += array[i];

                display = validateIgn(tag);
                if (display.equals(""))
                    return;
                else
                    ign = true;
            }

            if (blist.remove(tag.toLowerCase().replaceAll("\\s+", "")))
            {
                if (ign)
                    msg.getChannel().sendMessage(String.format("Summoner **%s** has been whitelisted.",
                            display)).complete();
                else
                    msg.getChannel().sendMessage(String.format("Discord user <@%s> has been whitelisted.",
                            tag)).complete();
            }
            else
            {
                msg.getChannel().sendMessage(String.format("%s This account is not blacklisted.",
                        member.getAsMention())).complete();
            }
        }
    }

    void giveElo(String message, Message msg) {

        String msgArray[] = message.split(" ");
        String id = msg.getAuthor().getId();
        if (msgArray.length > 1) {
            id = msgArray[1].replaceAll("[^\\d]", "");
        }
        int elo = Integer.parseInt(msgArray[2]);

        if (ladder.exists(id)) {
            List<Player> listCopy = new ArrayList<>(ladder.getLadder());
            listCopy.sort(Comparator.comparing(Player::getElo));
            Collections.reverse(listCopy);

            for (int i = 0; i < listCopy.size(); ++i) {
                if (listCopy.get(i).getId().equals(id)) {
                    Player local = listCopy.get(i);
                    local.giveElo(elo);
                    local.win();
                    msg.getChannel().sendMessage(String.format("Discord user <@%s> has been granted %s elo and a win!.",
                            id, elo)).complete();
                    ladder.write();
                }
            }
        }
    }

    void takeElo(String message, Message msg)
    {
        String msgArray[] = message.split(" ");
        String id = msg.getAuthor().getId();
        if (msgArray.length > 1) {
            id = msgArray[1].replaceAll("[^\\d]", "");
        }
        int elo = Integer.parseInt(msgArray[2]);

        if (ladder.exists(id)) {
            List<Player> listCopy = new ArrayList<>(ladder.getLadder());
            listCopy.sort(Comparator.comparing(Player::getElo));
            Collections.reverse(listCopy);

            for (int i = 0; i < listCopy.size(); ++i) {
                if (listCopy.get(i).getId().equals(id)) {
                    Player local = listCopy.get(i);
                    local.takeElo(elo);
                    local.wincancel();
                    msg.getChannel().sendMessage(String.format("Discord user <@%s> has lost %s elo!.",
                            id, elo)).complete();
                    ladder.write();
                }
            }
        }
    }

    void list()
    {
        Date date = new Date();
        SimpleDateFormat formatter = new SimpleDateFormat("HH:mm aa");
        formatter.setTimeZone(TimeZone.getTimeZone("PST"));
        EmbedBuilder listEmbed = new EmbedBuilder();
        ArrayList<String> list = blist.getList();

        listEmbed.setDescription("\n");
        listEmbed.setTitle("Blacklist");
        listEmbed.setColor(Color.BLACK);
        listEmbed.setFooter(String.format("Today at %s", formatter.format(date)), iconLink);

        for (String tag : list)
        {
            char s[] = tag.toCharArray();
            if(Character.isDigit(s[1]))
                listEmbed.appendDescription("<@" + tag + ">\n");
            else
                listEmbed.appendDescription("IGN: " +tag + "\n");
        }

        Inhouse.staffChannel.sendMessage(listEmbed.build()).complete();
    }

    void lobbies(Message message)
    {
        EmbedBuilder lobbies = new EmbedBuilder();
        int lobby = 1;
        for (Room room: rooms)
        {
            StringBuilder status = new StringBuilder("*Inactive*\n");
            if (room.isActive())
            {
                status = new StringBuilder("*Active*\nStatus : ");
                if (room.isDrafting())
                    status.append("Drafting\n");
                else if (room.isRunning())
                    status.append("In Game\n");
                else
                    status.append("Open\n");
                status.append("Host : ").append(room.host.getAsMention());
            }
            lobbies.addField("__Game " + lobby++ + "__", status.toString(), true);
            lobbies.setColor(new Color(255, 0, 0));
            lobbies.setAuthor("Games", iconLink, iconLink);
        }
        message.getChannel().sendMessage(lobbies.build()).complete();
    }

    void games(Message message)
    {
        int count = 0;
        boolean playing = false;
        for (Room room : rooms)
        {
            ++count;
            if (room.isRunning())
            {
                int total = 0;
                for (Player player : ladder.getLadder())
                {
                    total += (player.getLosses() + player.getWins());
                }

                total /= 10;
                playing = true;
                EmbedBuilder game = new EmbedBuilder();
                game.setColor(new Color(0, 153, 255));
                game.setTitle("Game #" + count + " (" + (total + count) + ")");
                game.addField("__Blue Team__",
                        "```\n1. " + room.teamOne.get(0) + "\n" +
                                "2. " + room.teamOne.get(1) + "\n" +
                                "3. " + room.teamOne.get(2) + "\n" +
                                "4. " + room.teamOne.get(3) + "\n" +
                                "5. " + room.teamOne.get(4) + "\n```"
                        , false);
                game.addField("__Red Team__",
                        "```\n1. " + room.teamTwo.get(0) + "\n" +
                                "2. " + room.teamTwo.get(1) + "\n" +
                                "3. " + room.teamTwo.get(2) + "\n" +
                                "4. " + room.teamTwo.get(3) + "\n" +
                                "5. " + room.teamTwo.get(4) + "\n```"
                        , false);
                game.setFooter("Host: " + room.host.getEffectiveName(), room.host.getUser().getEffectiveAvatarUrl());
                game.setDescription("There are a total of **" + total + "** games played this season.");
                message.getChannel().sendMessage(game.build()).complete();
            }
        }

        if (!playing)
        {
            int total = 0;
            for (Player player : ladder.getLadder())
            {
                total += (player.getLosses() + player.getWins());
            }

            total /= 10;

            EmbedBuilder games = new EmbedBuilder();
            games.setColor(new Color(0, 153, 255));
            games.setTitle("No Games Being Played!");
            games.setDescription("Although there aren't any games going on right now, there have been **" +
                    total + "** played this season.");
            message.getChannel().sendMessage(games.build()).complete();
        }
    }

    void ladder(Message message)
    {
        SimpleDateFormat formatter = new SimpleDateFormat("HH:mm aa");
        formatter.setTimeZone(TimeZone.getTimeZone("PST"));
        Date date = new Date();
        int page = 0;
        String contents[] = message.getContentRaw().split(" ");

        if (contents.length > 1)
            page = Integer.parseInt(contents[1]) - 1;

        int startRank = page * 20;
        int endRank = startRank + 20;

        List<Player> listCopy = new ArrayList<>(ladder.getLadder());
        listCopy.sort(Comparator.comparing(Player::getElo));
        Collections.reverse(listCopy);

        StringBuilder desc = new StringBuilder("```css\nRank " + String.format("%-17s", "IGN") + "\tElo\tWin\tLoss\tStreak\n```");
        StringBuilder descTwo = new StringBuilder("```fix\n");
        StringBuilder descThree = new StringBuilder("```yaml\n");
        for (int i = startRank; i < endRank; ++i)
        {
            if (i < listCopy.size())
            {
                Player local = listCopy.get(i);
                if (local != null)
                {
                    if (i > 4)
                    {
                        descThree.append(i + 1)
                                .append(i > 8 ? ". " : ".  ")
                                .append(String.format("%-18s\t", local.getIgn()))
                                .append(String.format("%-4s\t", local.getElo()))
                                .append(String.format("%-3s\t", local.getWins()))
                                .append(String.format("%-3s\t", local.getLosses()) + " " + local.getStreak()+ "\n");
                    }
                    else
                    {
                        descTwo.append(i + 1)
                                .append(".  ")
                                .append(String.format("%-18s\t", local.getIgn()))
                                .append(String.format("%-4s\t", local.getElo()))
                                .append(String.format("%-3s\t", local.getWins()))
                                .append(String.format("%-3s\t", local.getLosses()) + " " + local.getStreak()+ "\n");
                    }
                }
            }
        }
        descTwo.append("```");
        descThree.append("```");

        EmbedBuilder lbBuilder = new EmbedBuilder();
        lbBuilder.addField("Leaderboards - Page " + (page == 0 ? 1 : page + 1), desc.toString(), false);

        if (page == 0)
            lbBuilder.addField("", descTwo.toString(), false);
        lbBuilder.addField("", descThree.toString(),false);

        lbBuilder.setColor(new Color(0, 153, 255));
        lbBuilder.setFooter("Today at " + formatter.format(date), iconLink);
        message.getChannel().sendMessage(lbBuilder.build()).complete();
    }

    void most(Message message)
    {
        SimpleDateFormat formatter = new SimpleDateFormat("HH:mm aa");
        formatter.setTimeZone(TimeZone.getTimeZone("PST"));
        Date date = new Date();
        int page = 0;
        String contents[] = message.getContentRaw().split(" ");
        if (contents.length > 1)
            page = Integer.parseInt(contents[1]) - 1;

        int startRank = page * 10;
        int endRank = startRank + 10;

        List<Player> listCopy = new ArrayList<>(ladder.getLadder());
        listCopy.sort(Comparator.comparing(x -> (x.getWins() + x.getLosses())));
        Collections.reverse(listCopy);
        StringBuilder desc = new StringBuilder("```css\nRank " + String.format("%-17s", "IGN") + "\tElo\tGames\n```");
        StringBuilder descThree = new StringBuilder("```yaml\n");

        for (int i = startRank; i < endRank; ++i)
        {
            if (i < listCopy.size())
            {
                Player local = listCopy.get(i);

                if (local != null)
                {
                    descThree.append(i + 1)
                            .append(i > 8 ? ". " : ".  ")
                            .append(String.format("%-18s\t", local.getIgn()))
                            .append(String.format("%-4s\t", local.getElo()))
                            .append(String.format("%-3s\t", local.getWins() + local.getLosses()) + "\n");
                }
            }
        }

        descThree.append("```");

        EmbedBuilder lbBuilder = new EmbedBuilder();
        lbBuilder.setColor(new Color(0, 153, 255));
        lbBuilder.addField("Leaderboards - Page " + (page == 0 ? 1 : page + 1), desc.toString(), false);
        lbBuilder.addField("", descThree.toString(), false);
        lbBuilder.setFooter("Today at " + formatter.format(date), iconLink);
        message.getChannel().sendMessage(lbBuilder.build()).complete();
    }

    void mostwins(Message message)
    {
        SimpleDateFormat formatter = new SimpleDateFormat("HH:mm aa");
        formatter.setTimeZone(TimeZone.getTimeZone("PST"));
        Date date = new Date();
        int page = 0;
        String contents[] = message.getContentRaw().split(" ");
        if (contents.length > 1)
            page = Integer.parseInt(contents[1]) - 1;

        int startRank = page * 10;
        int endRank = startRank + 10;

        List<Player> listCopy = new ArrayList<>(ladder.getLadder());
        listCopy.sort(Comparator.comparing(Player::getWins));
        Collections.reverse(listCopy);
        StringBuilder desc = new StringBuilder("```css\nRank " + String.format("%-17s", "IGN") + "\tElo\tWins\n```");
        StringBuilder descThree = new StringBuilder("```yaml\n");

        for (int i = startRank; i < endRank; ++i)
        {
            if (i < listCopy.size())
            {
                Player local = listCopy.get(i);

                if (local != null)
                {
                    descThree.append(i + 1)
                            .append(i > 8 ? ". " : ".  ")
                            .append(String.format("%-18s\t", local.getIgn()))
                            .append(String.format("%-4s\t", local.getElo()))
                            .append(String.format("%-3s\t", local.getWins() + local.getLosses()) + "\n");
                }
            }
        }

        descThree.append("```");

        EmbedBuilder lbBuilder = new EmbedBuilder();
        lbBuilder.setColor(new Color(0, 153, 255));
        lbBuilder.addField("Most Wins Leaderboards - Page " + (page == 0 ? 1 : page + 1), desc.toString(), false);
        lbBuilder.addField("", descThree.toString(), false);
        lbBuilder.setFooter("Today at " + formatter.format(date),
                iconLink);
        message.getChannel().sendMessage(lbBuilder.build()).complete();
    }


    void commands()
    {
        SimpleDateFormat formatter = new SimpleDateFormat("HH:mm aa");
        formatter.setTimeZone(TimeZone.getTimeZone("PST"));
        Date date = new Date();
        EmbedBuilder cmdBuilder = new EmbedBuilder();
        cmdBuilder.setColor(new Color(28, 112, 255));
        cmdBuilder.setTitle("Bot Commands");
        cmdBuilder.addField("!host","Host an empty lobby", false);
        cmdBuilder.addField("!cancel","Cancel a host", false);
        cmdBuilder.addField("!captains","Start drafting with captains picking when lobby is full", false);
        cmdBuilder.addField("!random","Randomly draft players in a lobby", false);
        cmdBuilder.addField("!remove discord@","Kick a player from a full lobby", false);
        cmdBuilder.addField("!room # blue/red","Input win for match and side", false);
        cmdBuilder.addField("!room # clear","Clears current game", false);
        cmdBuilder.addField("!giveElo @discord #","Gives discord user X elo and gives a win", false);
        cmdBuilder.addField("!takeElo @discord #","Takes X elo from discord user and takes a win", false);
        cmdBuilder.addField("!undo","Returns ladder to previous standings", false);
        cmdBuilder.addField("!games","Shows you the current games", false);
        cmdBuilder.addField("!setelo discord@ 1234","Sets X person to X elo", false);
        cmdBuilder.addField("!setwins discord@ 123","Sets X person to X wins", false);
        cmdBuilder.addField("!setlosses discord@ 123","Sets X person to X losses", false);
        cmdBuilder.addField("!lobbies","Shows you the current hosts", false);
        cmdBuilder.addField("!list","Shows you the blacklist", false);
        cmdBuilder.addField("!whitelist @discord/IGN","Removes discord/IGN from blacklist", false);
        cmdBuilder.addField("!blacklist @discord/IGN","Adds discord/IGN to blacklist", false);
        cmdBuilder.addField("!repick","Undo a pick and allow the captain to repick", false);
        cmdBuilder.addField("!redraft","Undo the entire draft, can kick players", false);
        cmdBuilder.setFooter("Today at " + formatter.format(date), iconLink);
        Inhouse.staffChannel.sendMessage(cmdBuilder.build()).complete();
    }

    void usercommands(Message msg)
    {
        SimpleDateFormat formatter = new SimpleDateFormat("HH:mm aa");
        formatter.setTimeZone(TimeZone.getTimeZone("PST"));
        Date date = new Date();
        EmbedBuilder cmdBuilder = new EmbedBuilder();
        cmdBuilder.setColor(new Color(28, 112, 255));
        cmdBuilder.setTitle("Bot Commands");
        cmdBuilder.addField("!join","Sign up to host with your ign", false);
        cmdBuilder.addField("!drop","Drop from unfilled lobby", false);
        cmdBuilder.addField("!stats","Displays your stats", false);
        cmdBuilder.addField("!games","Shows you the current games", false);
        cmdBuilder.addField("!lobbies","Shows you the current games", false);
        //cmdBuilder.addField("!decay","Displays the next decay check", true);
        cmdBuilder.addField("!most","Shows you the players that have no life", false);
        cmdBuilder.addField("!mostwins","Shows you the players that have the most wins", false);
        cmdBuilder.addField("!ladder #","Displays ladder page, no number shows first page", false);
        cmdBuilder.setFooter("Today at " + formatter.format(date), iconLink);
        msg.getChannel().sendMessage(cmdBuilder.build()).complete();
    }

    void register(Member member, String ign, Message message)
    {
        if (!ladder.exists(message.getAuthor().getId())) {
            String realIgn = validateIgn(ign);
            Player player = new Player(member.getUser().getId(), realIgn, 2400, 0, 0,
                    0, false/*, getRank(realIgn)*/);
            Inhouse.chatChannel.sendMessage(String.format("<@%s> registered to ladder with register as **" +
                    player.getIgn() + "**.", member.getUser().getId())).complete();
            ladder.addPlayerToLadder(player);
            ladder.write();
            message.getChannel().sendMessage(String.format("<@%s> you are now registered to the ladder. " +
                    "Use !stats in <#"+Inhouse.chatId+"> to see your standing!", member.getUser().getId())).complete();
        }
        else {
            message.getChannel().sendMessage(String.format("<@%s> you are already registered to the ladder. " +
                    "If you wish to change name, ask a mod.", member.getUser().getId())).complete();
        }
    }

    void stats(Message message)
    {
        SimpleDateFormat formatter = new SimpleDateFormat("HH:mm aa");
        formatter.setTimeZone(TimeZone.getTimeZone("PST"));
        Date date = new Date();
        String contents[] = message.getContentRaw().split(" ");
        String id = message.getAuthor().getId();
        if (contents.length > 1)
        {
            id = contents[1].replaceAll("[^\\d]", "" );
        }

        if (ladder.exists(message.getAuthor().getId()))
        {
            List<Player> listCopy = new ArrayList<>(ladder.getLadder());
            listCopy.sort(Comparator.comparing(Player::getElo));
            Collections.reverse(listCopy);

            for (int i = 0; i < listCopy.size(); ++i)
            {
                if (listCopy.get(i).getId().equals(id))
                {
                    Player local = listCopy.get(i);

                    if (local != null)
                    {
                        EmbedBuilder lbBuilder = new EmbedBuilder();
                        StringBuilder desc = new StringBuilder("```css\nRank " + String.format("%-18s", "IGN") +
                                "\tElo\tWins\tLoss\tStreak\n```");
                        StringBuilder descTwo = new StringBuilder("```yaml\n");
                        descTwo.append(i + 1)
                                .append(i > 8 ? ". " : ".  ")
                                .append(String.format("%-18s\t", local.getIgn()))
                                .append(String.format("%-4s\t", local.getElo()))
                                .append(String.format("%-3s\t", local.getWins()))
                                .append(String.format("%-3s\t", local.getLosses()) + " " + local.getStreak()+ "\n")
                                .append("```");
                        /*if (i < 20)
                            descTwo.append("_*Will lose_ **")
                                    .append(15)
                                    .append("** _elo on_ **")
                                    .append(local.getDecayDate())
                                    .append("** _and each following day that they remain inactive._");*/
                        lbBuilder.setColor(new Color(0, 153, 255));
                        String msgArray[] = local.getIgn().split(" ");
                        StringBuilder ign = new StringBuilder();
                        for (String aMsgArray : msgArray) ign.append(aMsgArray);
                        lbBuilder.setAuthor("*CLICK ME FOR OP.GG*", "http://na.op.gg/summoner/userName=" +
                                ign, "https://play-lh.googleusercontent.com/UdvXlkugn0bJcwiDkqHKG5IElodmv-oL4kHlNAklSA2sdlVWhojsZKaPE-qFPueiZg");
                        lbBuilder.setFooter("Today at " + formatter.format(date), iconLink);
                        lbBuilder.addField("Stats for "+ local.getIgn(), desc.toString(), false);
                        lbBuilder.addField("", descTwo.toString(), false);
                        message.getChannel().sendMessage(lbBuilder.build()).complete();
                    }
                }
            }
        }
        else {
            message.getChannel().sendMessage(String.format("<@%s> User is **NOT** registered to the ladder. Use " +
                    "``!register`` to register.", message.getMember().getUser().getId())).complete();
        }
    }

    void cancel(Member member)
    {
        if (currentActiveHost)
        {
            EmbedBuilder cancelEmbed = new EmbedBuilder();
            cancelEmbed.setAuthor("Game " + (currentRoom.getNum()), iconLink, iconLink);
            cancelEmbed.setFooter("Host: " + currentRoom.host.getEffectiveName(),
                    currentRoom.host.getUser().getEffectiveAvatarUrl());
            cancelEmbed.setDescription("**Canceled**");
            cancelEmbed.setColor(new Color(255, 0, 0));

            currentActiveHost = false;
            currentRoom.clear();
            switchInput(Input.NONE);
            --activeRooms;
            clearMessages();

            Inhouse.hostChannel.sendMessage(cancelEmbed.build()).complete();
        }
    }

    void kick(Message message)
    {
        String msgArray[] = message.getContentRaw().split(" ");
        if (msgArray.length == 2)
        {
            if (currentRoom.removePlayer(msgArray[1]))
            {
                updateHostEmbed();
            }
        }
    }

    void drop(Message message)
    {
        if (currentRoom.removePlayer(message.getMember().getAsMention()))
        {
            System.out.println("(" + message.getMember().getEffectiveName() + ") dropped from host");
            updateHostEmbed();
        }
    }

    void undo(Message message)
    {
        undoladder();
        System.out.println("Game Undone");
        System.out.println("---------------------------------------------------");
        Inhouse.hostChannel.sendMessage("**Ladder rolled back to previous rankings.**").complete();
    }

    private boolean permissable(Member member)
    {
        for (Role role : member.getRoles())
        {
            if (role.getName().equals("Coach"))
            {
                return true;
            }
        }
        return false;
    }

    void hostInput(Message message)
    {
        if (permissable(message.getMember()))// || message.getMember().getUser().getId().equals(currentRoom.host.getUser().getId()))
        {
            if (message.getContentRaw().equals("!cancel"))
            {
                System.out.println("Host canceled by (" + message.getMember().getEffectiveName() + ")");
                System.out.println("---------------------------------------------------");
                cancel(message.getMember());
            }
            else if (message.getContentRaw().startsWith("!remove "))
            {
                String msgArray[] = message.getContentRaw().split(" ");
                if (msgArray.length == 2)
                {
                    if (currentRoom.removePlayer(msgArray[1]))
                    {
                        switchInput(Input.NONE);

                        if (rosterMessage != null)
                        {
                            rosterMessage.delete().complete();
                            rosterPing.delete().complete();
                            rosterMessage = null;

                            updateHostEmbed();
                        }
                    }
                }
            }
            else if (permissable(message.getMember()) && message.getContentRaw().equals("!captains"))
            {
                System.out.println("("+ currentRoom.host.getUser().getName() + ") started draft");
                switchInput(Input.PICKORDER);
                rosterPing.delete().complete();
                rosterPing = null;
                updateRosterEmbed();
                pickorderMessage = Inhouse.hostChannel.sendMessage("Drafting has begun! Captain Two (" +
                        currentRoom.captainTwo.getMember().getAsMention() +
                        ")," +
                        " decide if you'd rather have" +
                        " **!first** or **!second** pick.")
                        .complete();
                EmbedBuilder OPGG = new EmbedBuilder();
                OPGG.setColor(new Color(0, 153, 255));


                String msgArray0[] = currentRoom.players.get(0).getIgn().split(" ");
                StringBuilder ign0 = new StringBuilder();
                for (String aMsgArray0 : msgArray0) ign0.append(aMsgArray0);
                String msgArray1[] = currentRoom.players.get(1).getIgn().split(" ");
                StringBuilder ign1 = new StringBuilder();
                for (String aMsgArray1 : msgArray1) ign1.append(aMsgArray1);
                String msgArray2[] = currentRoom.players.get(2).getIgn().split(" ");
                StringBuilder ign2 = new StringBuilder();
                for (String aMsgArray2 : msgArray2) ign2.append(aMsgArray2);
                String msgArray3[] = currentRoom.players.get(3).getIgn().split(" ");
                StringBuilder ign3 = new StringBuilder();
                for (String aMsgArray3 : msgArray3) ign3.append(aMsgArray3);
                String msgArray4[] = currentRoom.players.get(4).getIgn().split(" ");
                StringBuilder ign4 = new StringBuilder();
                for (String aMsgArray4 : msgArray4) ign4.append(aMsgArray4);
                String msgArray5[] = currentRoom.players.get(5).getIgn().split(" ");
                StringBuilder ign5 = new StringBuilder();
                for (String aMsgArray5 : msgArray5) ign5.append(aMsgArray5);
                String msgArray6[] = currentRoom.players.get(6).getIgn().split(" ");
                StringBuilder ign6 = new StringBuilder();
                for (String aMsgArray6 : msgArray6) ign6.append(aMsgArray6);
                String msgArray7[] = currentRoom.players.get(7).getIgn().split(" ");
                StringBuilder ign7 = new StringBuilder();
                for (String aMsgArray7 : msgArray7) ign7.append(aMsgArray7);
                String msgArray8[] = currentRoom.players.get(8).getIgn().split(" ");
                StringBuilder ign8 = new StringBuilder();
                for (String aMsgArray8 : msgArray8) ign8.append(aMsgArray8);
                String msgArray9[] = currentRoom.players.get(9).getIgn().split(" ");
                StringBuilder ign9 = new StringBuilder();
                for (String aMsgArray9 : msgArray9) ign9.append(aMsgArray9);

                OPGG.setAuthor("CLICK ME FOR MULTI OPGG OF INHOUSE", "http://na.op.gg/multi/query=" +
                        ign0 + "%2C" +
                        ign1 + "%2C" +
                        ign2 + "%2C" +
                        ign3 + "%2C" +
                        ign4 + "%2C" +
                        ign5 + "%2C" +
                        ign6 + "%2C" +
                        ign7 + "%2C" +
                        ign8 + "%2C" +
                        ign9, "https://play-lh.googleusercontent.com/UdvXlkugn0bJcwiDkqHKG5IElodmv-oL4kHlNAklSA2sdlVWhojsZKaPE-qFPueiZg");
                opgg = Inhouse.hostChannel.sendMessage(OPGG.build()).complete();
            }
            else if (permissable(message.getMember()) && message.getContentRaw().equals("!random"))
            {
                switchInput(Input.PICKORDER);
                rosterPing.delete().complete();
                rosterPing = null;
                pickorderMessage = Inhouse.hostChannel.sendMessage("Drafting has begun! Random pick was chosen!")
                        .complete();
                randompick();
            }
        }
    }

    void randompick()
    {
        switchInput(Input.DRAFT);
        currentRoom.setDrafting();
        ArrayList<Integer> numbers = new ArrayList<>();
        for (int i = 0; i < 10; i++)
        {
            numbers.add(i);
        }
        Collections.shuffle(numbers);
        Collections.shuffle(numbers);

        currentRoom.captainOne = currentRoom.players.get(numbers.get(9));
        currentRoom.addPlayerToTeam(currentRoom.captainOne, Room.Team.ONE);
        currentRoom.captainTwo = currentRoom.players.get(numbers.get(8));
        currentRoom.addPlayerToTeam(currentRoom.captainTwo, Room.Team.TWO);
        currentRoom.firstPick = currentRoom.captainOne;
        currentRoom.secondPick = currentRoom.captainTwo;

        currentRoom.pickPool.put(1, currentRoom.players.get(numbers.get(7)));
        currentRoom.pickPool.put(2, currentRoom.players.get(numbers.get(6)));
        currentRoom.pickPool.put(3, currentRoom.players.get(numbers.get(5)));
        currentRoom.pickPool.put(4, currentRoom.players.get(numbers.get(4)));
        currentRoom.pickPool.put(5, currentRoom.players.get(numbers.get(3)));
        currentRoom.pickPool.put(6, currentRoom.players.get(numbers.get(2)));
        currentRoom.pickPool.put(7, currentRoom.players.get(numbers.get(1)));
        currentRoom.pickPool.put(8, currentRoom.players.get(numbers.get(0)));
        updateRosterTeamsEmbed();

        for (int i = 0; i <8; i++){
            try {
                Thread.sleep(1000);
            } catch (InterruptedException ex) {

            }
            if (i == 0){
                currentRoom.addPlayerToTeam(3);
            }
            else if (i == 1){
                currentRoom.addPlayerToTeam(4);
            }
            else if (i == 2){
                currentRoom.addPlayerToTeam(7);
            }
            else if (i == 3){
                currentRoom.addPlayerToTeam(2);
            }
            else if (i == 4){
                currentRoom.addPlayerToTeam(5);
            }
            else if (i == 5){
                currentRoom.addPlayerToTeam(1);
            }
            else if (i == 6){
                currentRoom.addPlayerToTeam(6);
            }
            else if (i == 7){
                currentRoom.addPlayerToTeam(8);
            }
            updateRosterTeamsEmbed();
        }
        try {
            Thread.sleep(1000);
        } catch (InterruptedException ex) {

        }
        pickorderMessage.delete().complete();
        updateRosterTeamsEmbed();
        msgToDelete = Inhouse.hostChannel.sendMessage(currentRoom.teamOne.get(0).getMember().getAsMention() + " " +
                currentRoom.teamOne.get(1).getMember().getAsMention() + " " +
                currentRoom.teamOne.get(2).getMember().getAsMention() + " " +
                currentRoom.teamOne.get(3).getMember().getAsMention() + " " +
                currentRoom.teamOne.get(4).getMember().getAsMention() + " " +
                currentRoom.teamTwo.get(0).getMember().getAsMention() + " " +
                currentRoom.teamTwo.get(1).getMember().getAsMention() + " " +
                currentRoom.teamTwo.get(2).getMember().getAsMention() + " " +
                currentRoom.teamTwo.get(3).getMember().getAsMention() + " " +
                currentRoom.teamTwo.get(4).getMember().getAsMention() + "\n" +
                "\nRandom draft has finished, please join the server and your voice rooms. Good luck " +
                "and have fun!\n\n" + currentRoom.getHost().getAsMention() +
                " Drafting has finished, please start the game with ``!start`` if the game is ready, " +
                "otherwise you may redraft, or cancel this host.").complete();
    }

    void pickorderInput(Message message)
    {
        picknum = 0;
        if (message.getMember().getUser().getId().equals(currentRoom.captainTwo.getId()))
            if (message.getContentRaw().equals("!first") || message.getContentRaw().equals("!second")) {
                if (message.getContentRaw().equals("!first")) {
                    System.out.println("First pick (" + currentRoom.captainTwo.getIgn() + ")");
                    System.out.println("Second pick (" + currentRoom.captainOne.getIgn() + ")");
                    currentRoom.firstPick = currentRoom.captainTwo;
                    currentRoom.secondPick = currentRoom.captainOne;
                    currentRoom.addPlayerToTeam(currentRoom.captainTwo, Room.Team.ONE);
                    currentRoom.addPlayerToTeam(currentRoom.captainOne, Room.Team.TWO);
                } else if (message.getContentRaw().equals("!second")) {
                    System.out.println("First pick (" + currentRoom.captainOne.getIgn() + ")");
                    System.out.println("Second pick (" + currentRoom.captainTwo.getIgn() + ")");
                    currentRoom.firstPick = currentRoom.captainOne;
                    currentRoom.secondPick = currentRoom.captainTwo;
                    currentRoom.addPlayerToTeam(currentRoom.captainOne, Room.Team.ONE);
                    currentRoom.addPlayerToTeam(currentRoom.captainTwo, Room.Team.TWO);
                }

                pickorderMessage.delete().complete();
                currentRoom.setCurrentPick(currentRoom.firstPick);
                switchInput(Input.DRAFT);
                currentRoom.setDrafting();
                msgToDelete = Inhouse.hostChannel.sendMessage(currentRoom.firstPick.getMember().getAsMention() +
                        " Pick your first player.").complete();

                updateRosterTeamsEmbed();
            }
    }

    void repick()
    {
        currentRoom.repick();
        msgToDelete.delete().complete();
        updateRosterTeamsEmbed();
        msgToDelete = Inhouse.hostChannel.sendMessage(currentRoom.getCurrentPick().getMember().getAsMention() +
                " Pick a player.").complete();
    }

    void draftInput(Message message)
    {
        if (message.getMember().getUser().getId().equals(currentRoom.getCurrentPick().getId()))
            try {
                int pick = Integer.parseInt(message.getContentRaw());
                System.out.print(currentRoom.getCurrentPick().getIgn() + " picked ");
                picknum++;
                if (picknum == 1) currentRoom.one = pick;
                else if (picknum == 2) currentRoom.two = pick;
                else if (picknum == 3) currentRoom.three = pick;
                else if (picknum == 4) currentRoom.four = pick;
                else if (picknum == 5) currentRoom.five = pick;
                else if (picknum == 6) currentRoom.six = pick;
                else if (picknum == 7) currentRoom.seven = pick;
                else if (picknum == 8) currentRoom.eight = pick;

                if (currentRoom.addPlayerToTeam(pick) && currentRoom.isDrafting()) {
                    msgToDelete.delete().complete();
                    updateRosterTeamsEmbed();
                    msgToDelete = Inhouse.hostChannel.sendMessage(currentRoom.getCurrentPick().getMember().getAsMention() +
                            " Pick a player.").complete();
                }
                else if (!currentRoom.isDrafting())
                {
                    msgToDelete.delete().complete();
                    updateRosterTeamsEmbed();
                    msgToDelete = Inhouse.hostChannel.sendMessage(currentRoom.teamOne.get(0).getMember().getAsMention() + " " +
                            currentRoom.teamOne.get(1).getMember().getAsMention() + " " +
                            currentRoom.teamOne.get(2).getMember().getAsMention() + " " +
                            currentRoom.teamOne.get(3).getMember().getAsMention() + " " +
                            currentRoom.teamOne.get(4).getMember().getAsMention() + " " +
                            currentRoom.teamTwo.get(0).getMember().getAsMention() + " " +
                            currentRoom.teamTwo.get(1).getMember().getAsMention() + " " +
                            currentRoom.teamTwo.get(2).getMember().getAsMention() + " " +
                            currentRoom.teamTwo.get(3).getMember().getAsMention() + " " +
                            currentRoom.teamTwo.get(4).getMember().getAsMention() + "\n" +
                            "Draft has finished, please join your voice rooms. Good luck " +
                            "and have fun in the rift!\n\n" + currentRoom.getHost().getAsMention() +
                            " Drafting has finished, please start the game with ``!start`` if the game is ready, " +
                            "otherwise you may redraft, repick, or cancel this host.").complete();
                }
            } catch (NumberFormatException ignored) {

            }
    }

    public void startgame() {
        try {
            System.out.println("DRAFTING FINISHED");
            clearMessages();

            //Inhouse.channel.sendMessage(currentRoom.teamEmbed(Room.Team.ONE)).complete();
            //Inhouse.channel.sendMessage(currentRoom.teamEmbed(Room.Team.TWO)).complete();
            //Inhouse.channel.sendMessage("Host is " + currentRoom.host.getEffectiveName()).complete();

            try {
                File imageFile = new File("vs.png");
                BufferedImage background = ImageIO.read(imageFile);
                Graphics g = background.getGraphics();
                g.setFont(g.getFont().deriveFont(68f));
                g.setColor(new Color(51, 153, 255));

                g.drawString(currentRoom.teamOne.get(0).getIgn(), 60, 172);
                g.drawString(currentRoom.teamOne.get(1).getIgn(), 60, 344);
                g.drawString(currentRoom.teamOne.get(2).getIgn(), 60, 516);
                g.drawString(currentRoom.teamOne.get(3).getIgn(), 60, 688);
                g.drawString(currentRoom.teamOne.get(4).getIgn(), 60, 860);

                g.setColor(new Color(233, 62, 62));
                g.drawString(currentRoom.teamTwo.get(0).getIgn(), 1155, 172);
                g.drawString(currentRoom.teamTwo.get(1).getIgn(), 1155, 344);
                g.drawString(currentRoom.teamTwo.get(2).getIgn(), 1155, 516);
                g.drawString(currentRoom.teamTwo.get(3).getIgn(), 1155, 688);
                g.drawString(currentRoom.teamTwo.get(4).getIgn(), 1155, 860);

                //g.setColor(new Color(51, 153, 255));
                //g.drawString("Host is " + currentRoom.host.getEffectiveName(), 50, 1035);

                ImageIO.write(background, "png", new File("temp2.png"));
                g.dispose();

                int total = 0;
                for (Player player : ladder.getLadder()) {
                    total += (player.getLosses() + player.getWins());
                }

                total /= 10;

                if (activeRooms == 2) {
                    total += 1;
                } else if (activeRooms == 3) {
                    total += 2;
                }
                Inhouse.hostChannel.sendMessage("Room #" + getCurrentRoom() + " Game #" + (total++ + 1) + "! Good luck and have fun!").queue();
                Inhouse.hostChannel.sendFile(new File("temp2.png")).queue();
            } catch (IOException e) {
                e.printStackTrace();
            }
            for (Player player : currentRoom.players) {
                player.updateDate();
            }
            currentRoom.setRunning();
            currentActiveHost = false;
            switchInput(Input.NONE);
        }
        catch (Exception e)
        {

        }
    }

    private void undoladder()
    {
        ladder.setLadder(backups.pop());
        ladder.write();
    }

    void declareWinner(Message message)
    {
        String msgArray[] = message.getContentRaw().split(" ");
        if (msgArray.length == 3 && (msgArray[2].equals("blue") || msgArray[2].equals("red")))
        {
            try
            {
                backup();
                Room game = rooms[(Integer.parseInt(msgArray[1]) - 1)];
                backuproom(game);
                int averageBlue = game.averageElo(Room.Team.ONE);
                int averageRed = game.averageElo(Room.Team.TWO);

                List<Integer> oldWinnerElos = new ArrayList<>();
                List<Integer> oldLoserElos = new ArrayList<>();
                List<Player> winners = new ArrayList<>();
                List<Player> losers = new ArrayList<>();

                String winningTeam = msgArray[2].toLowerCase().substring(0, 1).toUpperCase() +
                        msgArray[2].toLowerCase().substring(1);

                if (winningTeam.equals("Blue"))
                {
                    bluewin = true;
                    System.out.println("Win given to team Blue");
                    System.out.println("Loss given to team Red");
                    winners = game.teamOne;
                    losers = game.teamTwo;
                    for (Player player : game.teamOne)
                    {
                        player.win();
                        oldWinnerElos.add(player.updateElo(averageRed, 1.0, averageBlue));
                    }
                    for (Player player : game.teamTwo)
                    {
                        player.loss();
                        oldLoserElos.add(player.updateElo(averageBlue, 0.0, averageRed));
                    }
                }
                else if (winningTeam.equals("Red"))
                {
                    bluewin = false;
                    System.out.println("Win given to team Red");
                    System.out.println("Loss given to team Blue");
                    winners = game.teamTwo;
                    losers = game.teamOne;
                    for (Player player : game.teamOne)
                    {
                        player.loss();
                        oldLoserElos.add(player.updateElo(averageRed, 0.0, averageBlue));
                    }
                    for (Player player : game.teamTwo)
                    {
                        player.win();
                        oldWinnerElos.add(player.updateElo(averageBlue, 1.0, averageRed));
                    }
                }

                createWinEmbed(winners, oldWinnerElos);
                createLossEmbed(losers, oldLoserElos);
                //MessageEmbed winEmbed = createWinEmbed(winners, oldWinnerElos);
                //Inhouse.hostChannel.sendMessage(winEmbed).complete();
                //MessageEmbed lossEmbed = createLossEmbed(losers, oldLoserElos);
                //Inhouse.hostChannel.sendMessage(lossEmbed).complete();

                ladder.write();
                game.clear();
                switchInput(Input.NONE);
                --activeRooms;

            }
            catch (NumberFormatException e)
            {
                e.printStackTrace();
            }
        }
        else if (msgArray.length == 3 && msgArray[2].equals("clear"))
        {
            Room game = rooms[(Integer.parseInt(msgArray[1]) - 1)];
            game.clear();
            switchInput(Input.NONE);
            Inhouse.hostChannel.sendMessage("**Game Cleared. Thanks for playing!**").complete();
            System.out.println("Game Cleared by (" + message.getMember().getEffectiveName() + ")");
            System.out.println("---------------------------------------------------");
            --activeRooms;
        }
    }

    void initDates()
    {
        Inhouse.chatChannel.sendMessage("Bot has started. I am now awake! UwU").complete();
        for (Player player : ladder.getLadder())
            player.convertDate();
    }

    void decayCountdown()
    {
        SimpleDateFormat formatter = new SimpleDateFormat("HH:mm aa");
        formatter.setTimeZone(TimeZone.getTimeZone("PST"));
        Date date = new Date();
        boolean decay = false;

        EmbedBuilder counterEmbed = new EmbedBuilder();

        List<Player> listCopy = new ArrayList<>(ladder.getLadder());
        listCopy.sort(Comparator.comparing(Player::getElo));
        Collections.reverse(listCopy);
        StringBuilder decayList = new StringBuilder("```\n");

        for (int i = 0; i < 20; ++i) {
            Player p = listCopy.get(i);
            if (ChronoUnit.DAYS.between(ldate.plusDays(1), p.getDecayDate()) < 0) {
                if (p.getElo() > 434) {
                    decay = true;
                    decayList.append("[").append(i + 1).append("] ");
                    decayList.append(String.format("%-16s", p.getIgn()))
                            .append(" \n");
                }
            }
        }
        decayList.append("```");

        long hours = ChronoUnit.HOURS.between(date.toInstant(), Inhouse.nextDecayCheck.toInstant());
        long minutes = ChronoUnit.MINUTES.between(date.toInstant(), Inhouse.nextDecayCheck.toInstant());
        long seconds = ChronoUnit.SECONDS.between(date.toInstant(), Inhouse.nextDecayCheck.toInstant());

        counterEmbed.setFooter("Today at " + formatter.format(date), iconLink);
        counterEmbed.setColor(new Color(0, 112, 52));
        counterEmbed.setAuthor("Daily Decay",iconLink, iconLink);
        counterEmbed.setDescription("Next inactivity check is in **" + hours + "** Hours, **" +
                (minutes - (hours * 60)) + "** Minutes, **" + (seconds - (minutes * 60)) + "** Seconds.");
        counterEmbed.addField("Players decaying at next inactivity decay check", (decay) ?
                decayList.toString() : "**No players up are up for decay.**", false);

        Inhouse.chatChannel.sendMessage(counterEmbed.build()).complete();
    }

    Leaderboard getHubLadder()
    {
        return ladder;
    }

    private void full()
    {
        currentRoom.assignCaptains();
        currentRoom.setTotalElo();

        RosterEmbed();
        switchInput(Input.HOST);
    }

    void redraft()
    {
        System.out.println("Redrafting");
        try
        {
            rosterMessage.delete().complete();
            opgg.delete().complete();
            try
            {
                msgToDelete.delete().complete();
            }
            catch (Exception ignored)
            {

            }
            pickorderMessage.delete().complete();
        }
        catch (Exception ignored)
        {

        }

        currentRoom.redraft();
        currentRoom.assignCaptains();
        currentRoom.setTotalElo();

        RosterEmbed();
        switchInput(Input.HOST);
    }

    private void updateHostEmbed()
    {
        EmbedBuilder hostEmbed = new EmbedBuilder();
        hostEmbed.setTitle("Players in Lobby");
        hostEmbed.setColor(new Color(0, 153, 255));
        hostEmbed.setDescription("**New game has begun!** Type `!join` to enter\n" +
                "```yaml\n" +
                " 1.  " + ((currentRoom.players.size() > 0) ? currentRoom.players.get(0).getIgn() : " ") + "\n" +
                " 2.  " + ((currentRoom.players.size() > 1) ? currentRoom.players.get(1).getIgn() : " ") + "\n" +
                " 3.  " + ((currentRoom.players.size() > 2) ? currentRoom.players.get(2).getIgn() : " ") + "\n" +
                " 4.  " + ((currentRoom.players.size() > 3) ? currentRoom.players.get(3).getIgn() : " ") + "\n" +
                " 5.  " + ((currentRoom.players.size() > 4) ? currentRoom.players.get(4).getIgn() : " ") + "\n" +
                " 6.  " + ((currentRoom.players.size() > 5) ? currentRoom.players.get(5).getIgn() : " ") + "\n" +
                " 7.  " + ((currentRoom.players.size() > 6) ? currentRoom.players.get(6).getIgn() : " ") + "\n" +
                " 8.  " + ((currentRoom.players.size() > 7) ? currentRoom.players.get(7).getIgn() : " ") + "\n" +
                " 9.  " + ((currentRoom.players.size() > 8) ? currentRoom.players.get(8).getIgn() : " ") + "\n" +
                " 10. " + ((currentRoom.players.size() > 9) ? currentRoom.players.get(9).getIgn() : " ") + "\n" +
                "```");
        hostEmbed.setFooter("Host: " + currentRoom.host.getEffectiveName(),
                currentRoom.host.getUser().getEffectiveAvatarUrl());

        if (hostMessage != null)
        {
            hostMessage = hostMessage.editMessage(hostEmbed.build()).complete();
        }
        else
        {
            hostPing = Inhouse.hostChannel.sendMessage("<@&494929485158940692> <@&737770473114566666> <@&747310230395813898> \n").complete();
            hostMessage = Inhouse.hostChannel.sendMessage(hostEmbed.build()).complete();
        }
    }

    private void updateRosterEmbed()
    {
        if (rosterMessage != null)
        {
            EmbedBuilder roster = new EmbedBuilder();
            roster.setTitle("Game");
            roster.setColor(new Color(0, 153, 255));
            roster.setFooter("Host: " + currentRoom.host.getEffectiveName(),
                    currentRoom.host.getUser().getEffectiveAvatarUrl());

            StringBuilder desc = new StringBuilder("```yaml\n");
            desc.append(" C1")
                    .append(". ")
                    .append(currentRoom.captainOne.getIgn())
                    .append("\n");
            desc.append(" C2")
                    .append(". ")
                    .append(currentRoom.captainTwo.getIgn())
                    .append("\n");
            for (int i : currentRoom.pickPool.keySet())
            {
                if(i%2 == 0)
                {
                    desc.append(" "+ i)
                            .append(".  ")
                            .append(currentRoom.pickPool.get(i))
                            .append("\n");
                }
                else
                {
                    desc.append(" "+ i)
                            .append(".  ")
                            .append(currentRoom.pickPool.get(i))
                            .append("\n");
                }
            }

            desc.append("```");
            roster.addField("Signed Up Players for Current Game", desc.toString(), false);

            rosterMessage = rosterMessage.editMessage(roster.build()).complete();
        }
    }

    private void updateRosterTeamsEmbed()
    {
        if (rosterMessage != null)
        {
            EmbedBuilder roster = new EmbedBuilder();
            roster.setTitle("Draft Phase");
            roster.setColor(new Color(0, 153, 255));
            roster.setFooter("Host: " + currentRoom.host.getEffectiveName(),
                    currentRoom.host.getUser().getEffectiveAvatarUrl());

            StringBuilder desc = new StringBuilder("```yaml\n");
            for (int i : currentRoom.pickPool.keySet())
            {
                if (i%2 == 0)
                {
                    desc.append(" [")
                            .append(i)
                            .append("] ")
                            .append(currentRoom.pickPool.get(i))
                            .append("\n");
                }
                else
                {
                    desc.append(" [")
                            .append(i)
                            .append("] ")
                            .append(currentRoom.pickPool.get(i))
                            .append("\n");
                }
            }
            desc.append("\nGOOD LUCK ON THE RIFT!```");

            roster.addField("Remaining Pick Pool", desc.toString(), true);
            roster.addField("Game Info", "Host: **" +
                    currentRoom.host.getEffectiveName() +
                    "**\nBlue Captain: **" +
                    currentRoom.captainOne.getMember().getEffectiveName() +
                    "**\nRed Captain: **" +
                    currentRoom.captainTwo.getMember().getEffectiveName() + "**", true);

            roster.addField("Blue Team", "```ini\n" +
                    "[1. " + String.format("%-18s", ((currentRoom.teamOne.size() > 0) ?
                    currentRoom.teamOne.get(0).getIgn() : " ")) + "]" + "\n" +
                    "[2. " + String.format("%-18s", ((currentRoom.teamOne.size() > 1) ?
                    currentRoom.teamOne.get(1).getIgn() : " ")) + "]" + "\n" +
                    "[3. " + String.format("%-18s", ((currentRoom.teamOne.size() > 2) ?
                    currentRoom.teamOne.get(2).getIgn() : " ")) + "]" + "\n" +
                    "[4. " + String.format("%-18s", ((currentRoom.teamOne.size() > 3) ?
                    currentRoom.teamOne.get(3).getIgn() : " ")) + "]" + "\n" +
                    "[5. " + String.format("%-18s", ((currentRoom.teamOne.size() > 4) ?
                    currentRoom.teamOne.get(4).getIgn() : " ")) + "]" + "\n" +
                    "```", false);

            roster.addField("Red Team", "```css\n" +
                    "[1. " + String.format("%-18s", ((currentRoom.teamTwo.size() > 0) ?
                    currentRoom.teamTwo.get(0).getIgn() : " ")) + "]" + "\n" +
                    "[2. " + String.format("%-18s", ((currentRoom.teamTwo.size() > 1) ?
                    currentRoom.teamTwo.get(1).getIgn() : " ")) + "]" + "\n" +
                    "[3. " + String.format("%-18s", ((currentRoom.teamTwo.size() > 2) ?
                    currentRoom.teamTwo.get(2).getIgn() : " ")) + "]" + "\n" +
                    "[4. " + String.format("%-18s", ((currentRoom.teamTwo.size() > 3) ?
                    currentRoom.teamTwo.get(3).getIgn() : " ")) + "]" + "\n" +
                    "[5. " + String.format("%-18s", ((currentRoom.teamTwo.size() > 4) ?
                    currentRoom.teamTwo.get(4).getIgn() : " ")) + "]" + "\n" +
                    "```", true);

            rosterMessage = rosterMessage.editMessage(roster.build()).complete();
        }
    }

    private void RosterEmbed()
    {
        if (hostMessage != null)
        {
            hostMessage.delete().complete();
            hostPing.delete().complete();
            hostMessage = null;
            hostPing = null;
        }

        EmbedBuilder roster = new EmbedBuilder();
        roster.setTitle("Inhouse Game");
        roster.addField("__Signed Up Players for Current Game__",
                "```yaml\n 1.  " + currentRoom.players.get(0).getIgn() /*+ "(" + currentRoom.players.get(0).getRank() + ")"*/ + "\n" +
                        " 2.  " + currentRoom.players.get(1).getIgn() /*+ "(" + currentRoom.players.get(1).getRank() + ")"*/ + "\n" +
                        " 3.  " + currentRoom.players.get(2).getIgn() /*+ "(" + currentRoom.players.get(2).getRank() + ")"*/ + "\n" +
                        " 4.  " + currentRoom.players.get(3).getIgn() /*+ "(" + currentRoom.players.get(3).getRank() + ")"*/ + "\n" +
                        " 5.  " + currentRoom.players.get(4).getIgn() /*+ "(" + currentRoom.players.get(4).getRank() + ")"*/ + "\n" +
                        " 6.  " + currentRoom.players.get(5).getIgn() /*+ "(" + currentRoom.players.get(5).getRank() + ")"*/ + "\n" +
                        " 7.  " + currentRoom.players.get(6).getIgn() /*+ "(" + currentRoom.players.get(6).getRank() + ")"*/ + "\n" +
                        " 8.  " + currentRoom.players.get(7).getIgn() /*+ "(" + currentRoom.players.get(7).getRank() + ")"*/ + "\n" +
                        " 9.  " + currentRoom.players.get(8).getIgn() /*+ "(" + currentRoom.players.get(8).getRank() + ")"*/ + "\n" +
                        " 10. " + currentRoom.players.get(9).getIgn() /*+ "(" + currentRoom.players.get(9).getRank() + ")"*/ + "```"
                , false);
        roster.setColor(new Color(0, 153, 255));
        roster.setFooter("Host: " + currentRoom.host.getEffectiveName(),
                currentRoom.host.getUser().getEffectiveAvatarUrl());
        rosterMessage = Inhouse.hostChannel.sendMessage(roster.build()).complete();
        rosterPing = Inhouse.hostChannel.sendMessage("**Lobby Full.** Wait for " +
                currentRoom.host.getAsMention() +
                " to begin the draft phase with captains picking use **!captains**. Or if you want to randomize teams go with **!random**")
                .complete();
    }

    public int getCurrentRoom()
    {
        if (rooms[2].isRunning())
        {
            return 3;
        }
        else if (rooms[1].isRunning())
        {
            return 2;
        }
        else if (rooms[0].isRunning())
        {
            return 1;
        }
        return 1;
    }

    private void setCurrentRoom()
    {
        if (!rooms[0].isRunning())
        {
            currentRoom = rooms[0];
        }
        else if (!rooms[1].isRunning())
        {
            currentRoom = rooms[1];
        }
        else if (!rooms[2].isRunning())
        {
            currentRoom = rooms[2];
        }
    }

    private void clearMessages()
    {
        if (hostMessage != null)
        {
            hostMessage.delete().complete();
            hostMessage = null;
        }

        if (hostPing != null)
        {
            hostPing.delete().complete();
            hostPing = null;
        }

        if (rosterMessage != null)
        {
            rosterMessage.delete().complete();
            rosterMessage = null;
        }

        if (rosterPing != null)
        {
            rosterPing.delete().complete();
            rosterPing = null;
        }

        if (msgToDelete != null)
        {
            msgToDelete.delete().complete();
            msgToDelete = null;
        }
    }

    private void backuproom(Room room)
    {
        fakeroom.clear();

        for (int i = 0; i < 10 ; i++){
            fakeroom.players.add(room.players.get(i));
        }

        fakeroom.teamOne.clear();
        fakeroom.teamTwo.clear();

        for(int i = 0; i < 5; i++)
        {
            for (Player p : ladder.getLadder())
            {
                if(p.getId() == room.teamOne.get(i).getId())
                {
                    fakeroom.teamOne.add(p);
                }
            }
        }

        for(int i = 0; i < 5; i++)
        {
            for (Player p : ladder.getLadder())
            {
                if(p.getId() == room.teamTwo.get(i).getId())
                {
                    fakeroom.teamTwo.add(p);
                }
            }
        }

        fakeroom.host = room.host;
        fakeroom.captainOne = room.captainOne;
        fakeroom.captainTwo = room.captainTwo;
        fakeroom.firstPick = room.firstPick;
        fakeroom.secondPick = room.secondPick;

        fakeroom.setTotalElo();

    }

    private void backup()
    {
        List<Player> backupLadder = new ArrayList<>();

        for (Player p : ladder.getLadder())
        {
            Player copy = new Player(p.getId(), p.getIgn(), p.getElo(), p.getWins(), p.getLosses(), p.getStreak(),
                    p.getPrev()/*, p.getRank()*/);
            copy.setMember(p.getMember());
            copy.setLastPlayedStr(p.getLastPlayedStr());
            backupLadder.add(copy);
        }

        backups.push(backupLadder);
    }

    private String getRank(String ign){
        String rank = "";
        try{
            Summoner summoner = api.getSummonerByName(Platform.NA, ign);
            Set<LeaguePosition> positions = api.getLeaguePositionsBySummonerId(Platform.NA,summoner.getId());
            for(LeaguePosition p : positions){
                if(!p.getQueueType().equals("RANKED-SOLO-5x5")) continue;
                System.out.println(p.getRank());
                System.out.println(p.getTier());
                rank = p.getRank();
            }
        }
        catch (RiotApiException | IllegalArgumentException e){
            System.out.println(e);
            rank = "?";
        }
        return rank;
    }

    private String validateIgn(String ign)
    {
        try
        {
            Summoner summoner = api.getSummonerByName(Platform.NA, ign);
            return summoner.getName();
        }
        catch (RiotApiException | IllegalArgumentException e)
        {
            return "";
        }
    }

    private void switchInput(Input newInputType)
    {
        pendingHostInput = false;
        pendingPickorderInput = false;
        pendingDraftInput = false;
        pendingConfirm = false;

        switch (newInputType)
        {
            case HOST:
                pendingHostInput = true;
                break;

            case PICKORDER:
                pendingPickorderInput = true;
                break;

            case DRAFT:
                pendingDraftInput = true;
                break;

            case NONE:
                break;
        }
    }

    private void createWinEmbed(List<Player> winners, List<Integer> elos)
    {
        try
        {
            File imageFile;
            imageFile = new File("postGameScreen.png");
            BufferedImage background = ImageIO.read(imageFile);
            Graphics g = background.getGraphics();
            g.setFont(g.getFont().deriveFont(68f));
            g.setColor(new Color(255, 255, 255));

            int pos = 135;
            for (Player player : winners)
            {
                g.drawString(player.getIgn(),  30, pos);
                pos += 200;
                System.out.print(player.getIgn() + ", ");
            }
            System.out.println(elos.toString());
            pos = 135;
            for (Integer elo : elos)
            {
                g.drawString( "+" + elo.toString() + "", 790, pos);
                pos += 200;
            }

            ImageIO.write(background, "png", new File("temp.png"));
            g.dispose();

            int total = 0;
            for (Player player : ladder.getLadder())
            {
                total += (player.getLosses() + player.getWins());
            }

            total /= 10;

            //EmbedBuilder imageEmbed = new EmbedBuilder();
            //imageEmbed.setColor(new Color(0, 0, 195));
            Inhouse.hostChannel.sendMessage("Victory! Game #" + total + "").complete();
            Inhouse.hostChannel.sendFile(new File("temp.png")).complete();
            //return imageEmbed.build();
        }
        catch (IOException e)
        {
            e.printStackTrace();
            //return null;
        }
    }

    private void createLossEmbed(List<Player> losers, List<Integer> elos)
    {
        try
        {
            File imageFile;
            imageFile = new File("postGameScreen.png");
            BufferedImage background = ImageIO.read(imageFile);
            Graphics g = background.getGraphics();
            g.setFont(g.getFont().deriveFont(68f));
            g.setColor(new Color(255, 255, 255));

            int pos = 135;
            for (Player player : losers)
            {
                g.drawString(player.getIgn(),  30, pos);
                pos += 200;
                System.out.print(player.getIgn() + ", ");
            }
            System.out.println(elos.toString());
            pos = 135;
            for (Integer elo : elos)
            {
                g.drawString( " " + elo.toString() + "", 790, pos);
                pos += 200;
            }

            ImageIO.write(background, "png", new File("temp.png"));
            g.dispose();

            int total = 0;
            for (Player player : ladder.getLadder())
            {
                total += (player.getLosses() + player.getWins());
            }

            total /= 10;
            //EmbedBuilder imageEmbed = new EmbedBuilder();
            //imageEmbed.setColor(new Color(195, 23, 19));
            //imageEmbed.setAuthor("Defeat! Game #" + total + "", iconLink, iconLink);
            Inhouse.hostChannel.sendMessage("Defeat! Game #" + total + "").complete();
            Inhouse.hostChannel.sendFile(new File("temp.png")).queue();
            System.out.println("---------------------------------------------------");
            //return imageEmbed.build();
        }
        catch (IOException e)
        {
            e.printStackTrace();
            //return null;
        }

    }

    boolean pendingHostInput()
    {
        return pendingHostInput;
    }

    boolean pendingPickorderInput()
    {
        return pendingPickorderInput;
    }

    boolean pendingDraftInput()
    {
        return pendingDraftInput;
    }

    boolean pendingConfirm()
    {
        return pendingConfirm;
    }
}
