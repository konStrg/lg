package com.example.loupgarou.voice;

import com.example.loupgarou.LoupGarouPlugin;
import com.example.loupgarou.game.Game;
import com.example.loupgarou.game.PlayerData;
import de.maxhenkel.voicechat.api.BukkitVoicechatService;
import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;
import de.maxhenkel.voicechat.api.events.VoicechatServerStartedEvent;
import de.maxhenkel.voicechat.api.packets.MicrophonePacket;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.List;

/** Optional Simple Voice Chat integration: routes voice according to game state (zones, night, dead, cannibals). */
public class VoiceHook implements VoicechatPlugin {

    private final LoupGarouPlugin plugin;
    private VoicechatServerApi serverApi;

    private VoiceHook(LoupGarouPlugin plugin) {
        this.plugin = plugin;
    }

    public static void register(LoupGarouPlugin plugin) {
        BukkitVoicechatService service = plugin.getServer().getServicesManager().load(BukkitVoicechatService.class);
        if (service == null) throw new IllegalStateException("BukkitVoicechatService indisponible");
        service.registerPlugin(new VoiceHook(plugin));
    }

    @Override
    public String getPluginId() {
        return "transperceneige";
    }

    @Override
    public void initialize(VoicechatApi api) {
        // server api is received through the started event
    }

    @Override
    public void registerEvents(EventRegistration registration) {
        registration.registerEvent(VoicechatServerStartedEvent.class, e -> serverApi = e.getVoicechat());
        registration.registerEvent(MicrophonePacketEvent.class, this::onMicrophone);
    }

    private void onMicrophone(MicrophonePacketEvent event) {
        if (serverApi == null) return;
        VoicechatConnection connection = event.getSenderConnection();
        if (connection == null || !(connection.getPlayer().getPlayer() instanceof Player sender)) return;
        Game game = plugin.game();
        if (game == null) return;

        try {
            List<Player> audience = game.audience(sender);
            if (audience == null) return;
            PlayerData pd = game.data(sender.getUniqueId());
            boolean positional = pd != null && pd.alive && !game.isCannibalDebate(sender);
            event.cancel();

            MicrophonePacket packet = event.getPacket();
            Location l = sender.getLocation();
            float distance = (float) plugin.getConfig().getDouble("voice.proximity", 24.0);
            for (Player receiver : audience) {
                VoicechatConnection rc = serverApi.getConnectionOf(receiver.getUniqueId());
                if (rc == null || receiver == sender) continue;
                if (positional) {
                    serverApi.sendLocationalSoundPacketTo(rc, packet.locationalSoundPacketBuilder()
                            .position(serverApi.createPosition(l.getX(), l.getY(), l.getZ()))
                            .distance(distance)
                            .build());
                } else {
                    serverApi.sendStaticSoundPacketTo(rc, packet.staticSoundPacketBuilder().build());
                }
            }
        } catch (RuntimeException ex) {
            // routing failed: let the packet follow the default voice chat behavior
        }
    }
}
