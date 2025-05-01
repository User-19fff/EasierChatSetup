package net.coma112.easierchatsetup;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;

public final class EasierChatSetup implements Listener {
    private final JavaPlugin plugin;
    private final Set<Player> players = Collections.synchronizedSet(new HashSet<>());
    private String message = "";
    private Duration timeLimit = Duration.ofSeconds(30);
    private String cancelCommand = "cancel";
    private Collection<?> listenerCollection = null;
    private Runnable onSuccess = () -> {};
    private Runnable onFail = () -> {};
    private Runnable onStart = () -> {};
    private Consumer<String> onInput = null;
    private Predicate<String> validator = null;
    private BukkitTask timeoutTask = null;

    /**
     * Creates a new EasierChatSetup instance with the specified plugin.
     *
     * @param plugin The JavaPlugin instance
     */
    public EasierChatSetup(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Creates an empty EasierChatSetup instance.
     *
     * @return A new builder instance
     */
    public static @NotNull EasierChatSetup empty() {
        JavaPlugin mainPlugin = JavaPlugin.getProvidingPlugin(EasierChatSetup.class);
        return new EasierChatSetup(mainPlugin);
    }

    /**
     * Creates an empty EasierChatSetup instance with explicit plugin reference.
     *
     * @param plugin The JavaPlugin instance
     * @return A new builder instance
     */
    @Contract("_ -> new")
    public static @NotNull EasierChatSetup empty(JavaPlugin plugin) {
        return new EasierChatSetup(plugin);
    }

    /**
     * Adds a player to listen for chat inputs from.
     *
     * @param player The player to add
     */
    public void addPlayer(Player player) {
        players.add(player);
    }

    /**
     * Sets the message to display to the player.
     * Supports MiniMessage format with placeholders:
     * {time} - time limit in seconds
     * {cancel} - cancel command
     *
     * @param message The message to display
     * @return This builder instance
     */
    public EasierChatSetup append(String message) {
        this.message = message;
        return this;
    }

    /**
     * Sets the time limit for the input in seconds.
     *
     * @param seconds The time limit in seconds
     * @return This builder instance
     */
    public EasierChatSetup setTime(int seconds) {
        this.timeLimit = Duration.ofSeconds(seconds);
        return this;
    }

    /**
     * Sets the time limit for the input.
     *
     * @param duration The time limit as a Duration
     * @return This builder instance
     */
    public EasierChatSetup setTime(Duration duration) {
        this.timeLimit = duration;
        return this;
    }

    /**
     * Sets the callback to execute on successful input.
     *
     * @param onSuccess The callback to execute
     * @return This builder instance
     */
    public EasierChatSetup onSuccess(Runnable onSuccess) {
        this.onSuccess = onSuccess;
        return this;
    }

    /**
     * Sets the callback to execute on input failure or timeout.
     *
     * @param onFail The callback to execute
     * @return This builder instance
     */
    public EasierChatSetup onFail(Runnable onFail) {
        this.onFail = onFail;
        return this;
    }

    /**
     * Sets the callback to process the received input.
     *
     * @param onInput The callback to process input
     * @return This builder instance
     */
    public EasierChatSetup onInput(Consumer<String> onInput) {
        this.onInput = onInput;
        return this;
    }

    /**
     * Sets the validator to check if input is valid.
     *
     * @param validator The predicate to validate input
     * @return This builder instance
     */
    public EasierChatSetup withValidator(Predicate<String> validator) {
        this.validator = validator;
        return this;
    }

    /**
     * Sets the command to cancel the input process.
     *
     * @param cancelCommand The cancel command
     * @return This builder instance
     */
    public EasierChatSetup setCancel(String cancelCommand) {
        this.cancelCommand = cancelCommand;
        return this;
    }

    /**
     * Sets the collection to check if the players are contained within.
     * Can be any collection type like Set, List, or values of a Map.
     *
     * @param collection The collection to check
     * @return This builder instance
     */
    public EasierChatSetup listenTo(Collection<?> collection) {
        this.listenerCollection = collection;
        return this;
    }

    /*
     * Builds and starts the chat input process.
     */
    /**
     * Starts a chat session with a specific player.
     * This is a convenient method for quick setups.
     *
     * @param player The player to start the session with
     * @return This builder instance
     */
    public EasierChatSetup startSession(Player player) {
        addPlayer(player);
        build();
        return this;
    }

    /**
     * Sets the callback to execute when the chat setup starts.
     *
     * @param onStart The callback to execute at start
     * @return This builder instance
     */
    public EasierChatSetup onStart(Runnable onStart) {
        this.onStart = onStart;
        return this;
    }

    public void build() {
        if (players.isEmpty()) {
            throw new IllegalStateException("No players added to EasierChatSetup");
        }

        // Filter players if collection is provided
        if (listenerCollection != null) {
            Set<Player> filteredPlayers = new HashSet<>();
            for (Player player : players) {
                if (listenerCollection.contains(player) ||
                        listenerCollection.contains(player.getUniqueId()) ||
                        listenerCollection.contains(player.getName())) {
                    filteredPlayers.add(player);
                }
            }

            players.clear();
            players.addAll(filteredPlayers);
        }

        if (players.isEmpty()) {
            onFail.run();
            return;
        }

        onStart.run();

        plugin.getServer().getPluginManager().registerEvents(this, plugin);

        // Send message to players
        String formattedMessage = message
                .replace("{time}", String.valueOf(timeLimit.toSeconds()))
                .replace("{cancel}", cancelCommand);

        MiniMessage miniMessage = MiniMessage.miniMessage();
        Component component = miniMessage.deserialize(formattedMessage);

        for (Player player : players) {
            player.sendMessage(component);
        }

        // Start timeout task
        timeoutTask = new BukkitRunnable() {
            @Override
            public void run() {
                cleanup();
                onFail.run();
            }
        }.runTaskLater(plugin, timeLimit.toSeconds() * 20L);
    }

    /**
     * Handles chat events from players.
     *
     * @param event The chat event
     */
    @EventHandler
    public void onPlayerChat(final @NotNull AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (!players.contains(player)) return;

        if (event.getMessage().equalsIgnoreCase(cancelCommand)) {
            event.setCancelled(true);
            cleanup();
            onFail.run();
            return;
        }

        if (validator != null && !validator.test(event.getMessage())) {
            return;
        }

        event.setCancelled(true);
        String input = event.getMessage();

        if (onInput != null) {
            plugin.getServer().getScheduler().runTask(plugin, () -> onInput.accept(input));
        }

        plugin.getServer().getScheduler().runTask(plugin, () -> {
            cleanup();
            onSuccess.run();
        });
    }

    /**
     * Handles player quit events to clean up.
     *
     * @param event The player quit event
     */
    @EventHandler
    public void onPlayerQuit(final @NotNull PlayerQuitEvent event) {
        if (players.contains(event.getPlayer())) {
            players.remove(event.getPlayer());
            if (players.isEmpty()) {
                cleanup();
                onFail.run();
            }
        }
    }

    /**
     * Cleans up resources used by this instance.
     */
    private void cleanup() {
        if (timeoutTask != null && !timeoutTask.isCancelled()) {
            timeoutTask.cancel();
        }
        HandlerList.unregisterAll(this);
    }
}
