package bot;

import net.dv8tion.jda.api.EmbedBuilder;

import java.awt.*;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.List;

class Decay extends Thread
{
    private final Hub hub;

    String iconLink = "https://i.imgur.com/uJCE6CI.png";

    Decay(Hub f_hub)
    {
        hub = f_hub;
    }

    @Override
    public void run()
    {
        try
        {
            while (true)
            {
                //checkDecay();
                Date tempDate = new Date();
                Calendar cal = Calendar.getInstance();
                cal.add(Calendar.DAY_OF_WEEK, 1);
                Inhouse.nextDecayCheck = cal.getTime();
                hub.ldate = LocalDate.now();
                Thread.sleep(86400000);
            }
        }
        catch (InterruptedException e)
        {
            e.printStackTrace();
        }
    }

    private void checkDecay()
    {
        List<Player> listCopy = new ArrayList<>(hub.getHubLadder().getLadder());
        listCopy.sort(Comparator.comparing(Player::getElo));
        Collections.reverse(listCopy);

        boolean decay = false;
        SimpleDateFormat formatter = new SimpleDateFormat("HH:mm aa");
        formatter.setTimeZone(TimeZone.getTimeZone("PST"));
        Date date = new Date();
        StringBuilder decayList = new StringBuilder("\n");
        EmbedBuilder decayEmbed = new EmbedBuilder();
        decayEmbed.setColor(new Color(0, 112, 52));
        decayEmbed.setFooter("Today at " + formatter.format(date), iconLink);
        decayEmbed.setAuthor("Daily Decay", iconLink, iconLink);

        for (int i = 0; i < 20; ++i)
        {
            Player p = listCopy.get(i);
            if (ChronoUnit.DAYS.between(LocalDate.now(), p.getDecayDate()) < 0)
            {
                if (p.getElo() > 2400) {
                    decay = true;
                    decayList.append(String.format("%-16s", p.getIgn()))
                            .append(p.getElo())
                            .append(" > ")
                            .append(p.getElo() - 35)
                            .append("   \n");
                    p.decay(35);
                }
            }
        }
        decayList.append(" ");
        hub.getHubLadder().write();
        decayEmbed.setDescription((decay) ? decayList : "**No players up are up for decay.**");
        Inhouse.chatChannel.sendMessage(decayEmbed.build()).complete();
    }
}
