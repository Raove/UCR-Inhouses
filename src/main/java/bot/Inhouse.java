package bot;

import net.dv8tion.jda.api.AccountType;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import javax.security.auth.login.LoginException;
import java.awt.*;
import java.util.Date;

public class Inhouse extends ListenerAdapter{
    public static TextChannel hostChannel;
    public static TextChannel chatChannel;
    public static TextChannel staffChannel;
    public static TextChannel deletedChannel;

    public static JDA jda;
    public static Date nextDecayCheck;
    private static Hub hub;

    private static final String staffchatId = "784242930776145930";
    private static final String hostingchatId = "784242827047600168";
    private static final String chatId = "784203074058715198";
    private static final String deletechatId = "784242866800164904";

    public static void main(String args[]) throws LoginException
    {
        hub = new Hub();
        jda = JDABuilder.createDefault("NzExMDY3NjczNjg0ODY5MTgw.Xr9neA.eVMng9D87Y-WWf0JoYGOxP0oDU4").build();
        jda.addEventListener(new Inhouse());

        hostChannel = jda.getTextChannelById(hostingchatId);
        chatChannel = jda.getTextChannelById(chatId);
        staffChannel = jda.getTextChannelById(staffchatId);
        deletedChannel = jda.getTextChannelById(deletechatId);

        Decay decayChecker = new Decay(hub);
        decayChecker.run();
        hub.initDates();
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (!event.getAuthor().isBot()) {
            switch (event.getMessage().getChannel().getId()) {
                case hostingchatId:
                    if (hub.pendingHostInput()) {
                        if (permissable(event.getMember())) {
                            hub.hostInput(event.getMessage());
                        }
                    } else if (hub.pendingPickorderInput()) {
                        if (event.getMessage().getContentRaw().equals("!redraft")) {
                            if (permissable(event.getMember())) {
                                hub.redraft();
                            }
                        } else hub.pickorderInput(event.getMessage());
                    } else if (hub.pendingDraftInput()) {
                        switch (event.getMessage().getContentRaw()) {
                            case "!redraft":
                                if (permissable(event.getMember())) {
                                    hub.redraft();
                                }
                                break;
                            case "!start":
                                if (permissable(event.getMember())) {
                                    hub.startgame();
                                }
                                break;
                            case "!cancel":
                                if (permissable(event.getMember())) {
                                    hub.cancel(event.getMember());
                                }
                                break;
                            case "!repick":
                                if (permissable(event.getMember())) {
                                    hub.repick();
                                    --hub.picknum;
                                }
                                break;
                            default:
                                hub.draftInput(event.getMessage());
                                break;
                        }
                    } else {
                        if (event.getMessage().getContentRaw().startsWith("!host")) {
                            if (permissable(event.getMember())) {
                                if (hub.activeRooms < 5) {
                                    startHost(event.getMessage());
                                } else
                                    chatChannel.sendMessage(String.format("%s Cannot host more than 3 games!", event.getMember().getAsMention())).complete();
                            }
                        } else if (event.getMessage().getContentRaw().startsWith("!join")) {
                            hub.join(event.getMember());
                        } else if (event.getMessage().getContentRaw().equals("!cancel")) {
                            if (permissable(event.getMember())) {
                                hub.cancel(event.getMember());
                            }
                        } else if (event.getMessage().getContentRaw().equals("!undo")) {
                            if (permissable(event.getMember())) {
                                hub.undo(event.getMessage());
                            }
                        } else if (event.getMessage().getContentRaw().startsWith("!game ")) {
                            if (permissable(event.getMember())) {
                                hub.declareWinner(event.getMessage());
                            }
                        } else if (event.getMessage().getContentRaw().startsWith("!remove ")) {
                            if (permissable(event.getMember())) {
                                hub.kick(event.getMessage());
                            }
                        } else if (event.getMessage().getContentRaw().equals("!drop")) {
                            hub.drop(event.getMessage());
                        }
                    }
                    try {
                        Thread.sleep(20);
                    } catch (InterruptedException ex) {

                    }
                    EmbedBuilder deleted = new EmbedBuilder();
                    deleted.setColor(new Color(150, 0, 0));
                    deleted.setTitle(event.getAuthor().getName());
                    deleted.setDescription(event.getMessage().getContentRaw());
                    Inhouse.deletedChannel.sendMessage(deleted.build()).complete();
                    event.getMessage().delete().complete();
                    break;
                case chatId:
                    if (event.getMessage().getContentRaw().equals("!games")) {
                        hub.games(event.getMessage());
                    } else if (event.getMessage().getContentRaw().startsWith("!name ")) {
                        if (permissable(event.getMember())) {
                            hub.changeName(event.getMessage().getContentRaw());
                        }
                    } else if (event.getMessage().getContentRaw().equals("!lobbies")) {
                        hub.lobbies(event.getMessage());
                    } else if (event.getMessage().getContentRaw().startsWith("!ladder")) {
                        hub.ladder(event.getMessage());
                    } else if (event.getMessage().getContentRaw().startsWith("!most")) {
                        hub.most(event.getMessage());
                    } else if (event.getMessage().getContentRaw().startsWith("!mostwins")) {
                        hub.mostwins(event.getMessage());
                    } else if (event.getMessage().getContentRaw().startsWith("!stats")) {
                        hub.stats(event.getMessage());
                    } else if (event.getMessage().getContentRaw().startsWith("!commands")) {
                        hub.usercommands(event.getMessage());
                    } else if (event.getMessage().getContentRaw().startsWith("!register ")) {
                        register(event.getMessage());
                    } /*else if (event.getMessage().getContentRaw().equals("!decay")) {
                    hub.decayCountdown();
                }*/ else if (event.getMessage().getContentRaw().startsWith("!whitelist ")) {
                        if (permissable(event.getMember())) {
                            hub.remove(event.getMember(), event.getMessage().getContentRaw(), event.getMessage());
                        }
                    } else if (event.getMessage().getContentRaw().startsWith("!blacklist ")) {
                        if (permissable(event.getMember())) {
                            hub.blacklist(event.getMember(), event.getMessage().getContentRaw(), event.getMessage());
                        }
                    } else if (event.getMessage().getContentRaw().startsWith("!setelo ")) {
                        if (permissable(event.getMember())) {
                            hub.setElo(event.getMessage().getContentRaw(), event.getMessage());
                        }
                    } else if (event.getMessage().getContentRaw().startsWith("!setwins ")) {
                        if (permissable(event.getMember())) {
                            hub.setWin(event.getMessage().getContentRaw(), event.getMessage());
                        }
                    } else if (event.getMessage().getContentRaw().startsWith("!setlosses ")) {
                        if (permissable(event.getMember())) {
                            hub.setLoss(event.getMessage().getContentRaw(), event.getMessage());
                        }
                    } else if (event.getMessage().getContentRaw().startsWith("!restart")) {
                        if (permissable(event.getMember())) {
                            chatChannel.sendMessage("Restarting bot, please wait.").complete();
                            hub = null;
                            jda = null;

                            hub = new Hub();
                            jda.addEventListener(new Inhouse());

                            hostChannel = jda.getTextChannelById(hostingchatId);
                            chatChannel = jda.getTextChannelById(chatId);
                            staffChannel = jda.getTextChannelById(staffchatId);
                            deletedChannel = jda.getTextChannelById(deletechatId);
                            hub.initDates();

                            Decay decayChecker = new Decay(hub);
                            decayChecker.run();
                        }
                    } else if (event.getMessage().getContentRaw().startsWith("!giveElo ")) {
                        if (permissable(event.getMember())) {
                            hub.giveElo(event.getMessage().getContentRaw(), event.getMessage());
                        }
                    } else if (event.getMessage().getContentRaw().startsWith("!takeElo ")) {
                        if (permissable(event.getMember())) {
                            hub.takeElo(event.getMessage().getContentRaw(), event.getMessage());
                        }
                    }
                    break;
                case staffchatId:
                    if (event.getMessage().getContentRaw().equals("!list")) {
                        hub.list();
                    } else if (event.getMessage().getContentRaw().startsWith("!whitelist ")) {
                        if (permissable(event.getMember())) {
                            hub.remove(event.getMember(), event.getMessage().getContentRaw(), event.getMessage());
                        }
                    } else if (event.getMessage().getContentRaw().startsWith("!commands")) {
                        if (permissable(event.getMember())) {
                            hub.commands();
                        }
                    } else if (event.getMessage().getContentRaw().startsWith("!blacklist ")) {
                        if (permissable(event.getMember())) {
                            hub.blacklist(event.getMember(), event.getMessage().getContentRaw(), event.getMessage());
                        }
                    }
                    break;
            }
        }
    }

    //Used by the join command to make sure the user
    //entered an ign and pass it to the delegate.
    private void joinRoom(Message msg)
    {
        String msgArray[] = msg.getContentRaw().split(" ");
        StringBuilder ign = new StringBuilder();

        for (int i = 0; i < msgArray.length; ++i)
        {
            if (i != 0)
            {
                ign.append(msgArray[i]);
            }
        }

        if (!ign.toString().equals(""))
        {
            //hub.join(msg.getMember(), ign.toString(), msg);
        }
    }

    private void register(Message msg)
    {
        String msgArray[] = msg.getContentRaw().split(" ");
        StringBuilder ign = new StringBuilder();

        for (int i = 0; i < msgArray.length; ++i)
        {
            if (i != 0)
            {
                ign.append(msgArray[i]);
            }
        }

        if (!ign.toString().equals(""))
        {
            hub.register(msg.getMember(), ign.toString(), msg);
        }
    }

    //Used by the host command to determine if the host
    //entered an ign, and if so pass it to the appropriate
    //member function.
    private void startHost(Message message)
    {
        String msgArray[] = message.getContentRaw().split(" ");
        StringBuilder ign = new StringBuilder();

        for (int i = 0; i < msgArray.length; ++i)
        {
            if (i != 0)
            {
                ign.append(msgArray[i]);
            }
        }

        if (msgArray.length < 2)
        {
            System.out.println("IGN BLANK");
            hub.host(message.getMember(), message, true);
        }
        else
        {
            hub.host(message.getMember(), message, ign.toString());
        }
    }


    //Some commands call this function before delegating to the hub class
    //to confirmOrCancel that the user has the mods role.
    private boolean permissable(Member member)
    {
        for (Role role : member.getRoles())
        {
            if (role.getName().equals("OPG Moderator"))
            {
                return true;
            }
        }

        return false;
    }
}
