package dangoland.mcd;

import net.dv8tion.jda.core.AccountType;
import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.JDABuilder;
import net.dv8tion.jda.core.entities.Webhook;
import net.dv8tion.jda.core.events.Event;
import net.dv8tion.jda.core.events.ReadyEvent;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;
import net.dv8tion.jda.core.exceptions.RateLimitedException;
import net.dv8tion.jda.core.hooks.EventListener;
import net.dv8tion.jda.core.requests.restaction.MessageAction;
import net.dv8tion.jda.webhook.WebhookClient;
import net.dv8tion.jda.webhook.WebhookClientBuilder;
import net.dv8tion.jda.webhook.WebhookMessage;
import net.dv8tion.jda.webhook.WebhookMessageBuilder;
import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.plugin.java.JavaPlugin;

import javax.security.auth.login.LoginException;
import java.io.File;
import java.io.IOException;
import java.util.logging.Logger;

public class MCD extends JavaPlugin implements EventListener, Listener {
    private static FileConfiguration CONFIG;
    private static JDA DISCORD;
    private static Logger LOGGER;

    private int messagesRelayed;

    @Override
    public void onEnable() {
        // register events
        getServer().getPluginManager().registerEvents(this, this);
        LOGGER = getLogger();
        CONFIG = getConfig();

        // add a config header
        CONFIG.options().header("MCD Configuration\n\n" +
                "You may read up on configuration options at https://github.com/LewisTehMinerz/MCD/wiki, however\n" +
                "most options should be self-explanitory.");

        CONFIG.options().copyHeader(true);

        // set up default options
        CONFIG.addDefault("submit-metrics", true);
        CONFIG.addDefault("bot-token", "BOT-TOKEN-HERE");
        CONFIG.addDefault("relay-channel", 999999999999999999L);
        CONFIG.addDefault("webhooks.enabled", false);
        CONFIG.addDefault("webhooks.url", "");
        CONFIG.addDefault("format.minecraft", "\\u00A7l%s\\u00A7r: %s");
        CONFIG.addDefault("format.discord", "**%s**: %s");

        CONFIG.options().copyDefaults(true);

        try {
            CONFIG.save(getDataFolder() + File.separator + "config.yml");
        } catch (IOException e) {
            // something went horribly wrong, log and disable.
            LOGGER.severe("Something went very wrong while saving the configuration!");
            LOGGER.severe(e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
        }

        // only create the metrics class if it's actually enabled.
        if (CONFIG.getBoolean("submit-metrics")) {
            Metrics metrics = new Metrics(this);
            metrics.addCustomChart(new Metrics.SingleLineChart("messagesRelayed", () -> messagesRelayed));
        }

        try {
            DISCORD = new JDABuilder(AccountType.BOT)
                    .setToken(CONFIG.getString("bot-token"))
                    .addEventListener(this)
                    .buildAsync();
        } catch (LoginException e) {
            // something went horribly wrong, log and disable.
            LOGGER.severe("Something went very wrong while starting the Discord bot!");
            LOGGER.severe(e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
        }

    }

    @Override
    public void onDisable() {
        DISCORD.shutdown();
    }

    public void onEvent(Event e) {
        if (e instanceof MessageReceivedEvent) {
            MessageReceivedEvent ev = (MessageReceivedEvent)e;
            WebhookClient hook = new WebhookClientBuilder(CONFIG.getString("webhooks.url"))
                    .setDaemon(true)
                    .build();
            long id = hook.getIdLong();
            hook.close();
            if (ev.getAuthor().getIdLong() != DISCORD.getSelfUser().getIdLong() // don't relay messages sent by us
                    && !ev.isWebhookMessage()) { // don't relay messages sent via webhook
                if (ev.getChannelType().isGuild()) {
                    if (ev.getChannel().getIdLong() == CONFIG.getLong("relay-channel")) { // only send messages to the game that are in the relay channel
                        Bukkit.broadcastMessage(String.format(
                                CONFIG.getString("format.minecraft"),
                                ev.getAuthor().getName(),
                                ev.getMessage().getContentStripped()));
                        messagesRelayed++;
                    }
                }
            }
        } else if (e instanceof ReadyEvent) {
            LOGGER.info("Discord bot ready.");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onMessage(AsyncPlayerChatEvent e) {
        if (!e.isCancelled()) { // prevent relaying messages that aren't actually meant to be sent
            if (!CONFIG.getBoolean("webhooks.enabled")) {
                DISCORD.getTextChannelById(CONFIG.getLong("relay-channel")).sendMessage(String.format(
                        CONFIG.getString("format.discord"),
                        e.getPlayer().getName(),
                        e.getMessage())).queue();
            } else {
                WebhookClient hook = new WebhookClientBuilder(CONFIG.getString("webhooks.url"))
                        .setDaemon(true)
                        .build();
                WebhookMessage msg = new WebhookMessageBuilder()
                        .setContent(e.getMessage())
                        .setAvatarUrl("https://crafatar.com/renders/head/" + e.getPlayer().getUniqueId()
                                + "?overlay&default=MHF_Steve")
                        .setUsername(e.getPlayer().getName())
                        .build();
                hook.send(msg);
                hook.close();
                messagesRelayed++;
            }
        }
    }

}
