package com.tim.mvlastlocation;

import com.onarandombox.MultiversePortals.event.MVPortalEvent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

public final class MVLastLocation extends JavaPlugin implements Listener {

    private File storageFile;
    private FileConfiguration storageConfig;

    @Override
    public void onEnable() {
        if (!Bukkit.getPluginManager().isPluginEnabled("Multiverse-Portals")) {
            getLogger().severe("Multiverse-Portals is niet gevonden! Plugin schakelt zichzelf uit.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        saveDefaultConfig();
        initStorage();
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("MVLastLocation succesvol geactiveerd!");
    }

    @Override
    public void onDisable() {
        saveStorage();
    }

    private void initStorage() {
        storageFile = new File(getDataFolder(), "locations.yml");
        if (!storageFile.exists()) {
            try {
                if (storageFile.getParentFile().mkdirs() || storageFile.createNewFile()) {
                    getLogger().info("locations.yml aangemaakt.");
                }
            } catch (IOException e) {
                getLogger().severe("Kon locations.yml niet aanmaken: " + e.getMessage());
            }
        }
        storageConfig = YamlConfiguration.loadConfiguration(storageFile);
    }

    private void saveStorage() {
        try {
            if (storageConfig != null && storageFile != null) {
                storageConfig.save(storageFile);
            }
        } catch (IOException e) {
            getLogger().severe("Kon locaties niet opslaan: " + e.getMessage());
        }
    }

    private void savePlayerPosition(Player player, World world) {
        if (player == null || world == null) return;
        
        // Optioneel: negeer de lobbywereld zodat de speler daar niet willekeurig ergens respawnt
        // if (world.getName().equalsIgnoreCase("lobby")) return;

        Location loc = player.getLocation();
        String path = player.getUniqueId() + "." + world.getName();

        storageConfig.set(path + ".x", loc.getX());
        storageConfig.set(path + ".y", loc.getY());
        storageConfig.set(path + ".z", loc.getZ());
        storageConfig.set(path + ".yaw", loc.getYaw());
        storageConfig.set(path + ".pitch", loc.getPitch());
        
        saveStorage();
    }

    private Location getSavedLocation(UUID uuid, World world) {
        String path = uuid.toString() + "." + world.getName();
        if (!storageConfig.contains(path + ".x")) {
            return null; // Geen eerdere locatie bekend
        }

        double x = storageConfig.getDouble(path + ".x");
        double y = storageConfig.getDouble(path + ".y");
        double z = storageConfig.getDouble(path + ".z");
        float yaw = (float) storageConfig.getDouble(path + ".yaw");
        float pitch = (float) storageConfig.getDouble(path + ".pitch");

        return new Location(world, x, y, z, yaw, pitch);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        // Sla de locatie op van de wereld die de speler zojuist heeft verlaten
        savePlayerPosition(event.getPlayer(), event.getFrom());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        // Als een speler disconnect, sla de huidige positie in die wereld op
        Player player = event.getPlayer();
        savePlayerPosition(player, player.getWorld());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPortalUse(MVPortalEvent event) {
        if (!(event.getTeleportee() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getTeleportee();
        Location targetLoc = event.getDestination().getLocation(player);

        if (targetLoc == null || targetLoc.getWorld() == null) {
            return;
        }

        World targetWorld = targetLoc.getWorld();

        // Controleer of er al een opgeslagen plek is voor deze speler in de doelwereld
        Location lastKnownLoc = getSavedLocation(player.getUniqueId(), targetWorld);

        if (lastKnownLoc != null) {
            // Overschrijf de teleportbestemming van het portaal
            event.getDestination().setLocation(lastKnownLoc);
        }
        // Is lastKnownLoc == null? Dan doet de plugin niks en pakt hij het standaard portaaldoel.
    }
}
