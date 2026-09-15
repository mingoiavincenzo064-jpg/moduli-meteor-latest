package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import com.example.addon.AddonTemplate; // <-- stesso import usato negli altri moduli del template
import net.minecraft.network.protocol.game.ClientboundSetTimePacket; // nome Mojang (era WorldTimeUpdateS2CPacket in Yarn)
import net.minecraft.network.chat.Component; // nome Mojang per Text (verifica: in alcuni contesti puo' restare "Text")

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Stima i TPS del server contando la frequenza REALE di arrivo del pacchetto
 * WorldTimeUpdateS2CPacket. Il server vanilla invia questo pacchetto ogni 20
 * tick del SUO loop interno (non ogni 20 tick "di orologio reale"): quindi se
 * il server rallenta per una lag machine, gli servira' piu' tempo reale per
 * accumulare quei 20 tick, e il pacchetto arrivera' piu' di rado. Contando
 * quanti pacchetti arrivano in una finestra di tempo reale, e moltiplicando
 * per 20 (tick per pacchetto), si ottiene una stima onesta dei TPS reali.
 *
 * In aggiunta, un timeout indipendente rileva un freeze totale del server
 * (nessun pacchetto di alcun tipo per troppo tempo).
 */
public class TpsGuard extends Module {

    public TpsGuard() {
        super(AddonTemplate.CATEGORY, "tps-guard", "Disconnette se il TPS del server crolla (lag machine) o il server si blocca del tutto.");
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> minTps = sgGeneral.add(new DoubleSetting.Builder()
        .name("min-tps")
        .description("Sotto questo TPS stimato, disconnette.")
        .defaultValue(18.0)
        .range(1.0, 20.0)
        .sliderRange(1.0, 20.0)
        .build()
    );

    private final Setting<Integer> windowMs = sgGeneral.add(new IntSetting.Builder()
        .name("finestra-ms")
        .description("Finestra temporale reale su cui calcolare il TPS medio. Deve essere abbastanza larga da contenere piu' pacchetti (a TPS piene ne arriva circa 1 al secondo).")
        .defaultValue(6000) // 6 secondi -> ci si aspettano ~6 pacchetti a TPS piene
        .range(3000, 20000)
        .sliderRange(3000, 20000)
        .build()
    );

    private final Setting<Integer> minSamples = sgGeneral.add(new IntSetting.Builder()
        .name("campioni-minimi")
        .description("Numero minimo di pacchetti raccolti prima di fidarsi della stima.")
        .defaultValue(3)
        .range(2, 10)
        .build()
    );

    private final Setting<Double> freezeTimeoutSeconds = sgGeneral.add(new DoubleSetting.Builder()
        .name("timeout-freeze-secondi")
        .description("Se non arriva NESSUN pacchetto (di qualsiasi tipo) dal server per questi secondi, disconnette subito (sicurezza extra contro un freeze totale).")
        .defaultValue(5.0)
        .range(1.0, 30.0)
        .sliderRange(1.0, 30.0)
        .build()
    );

    private final Setting<Boolean> disableAfter = sgGeneral.add(new BoolSetting.Builder()
        .name("disattiva-dopo-trigger")
        .description("Disattiva il modulo dopo la disconnessione (evita loop se ti riconnetti manualmente).")
        .defaultValue(true)
        .build()
    );

    private static final int TICKS_PER_TIME_PACKET = 20;

    private final Deque<Long> timePacketTimestamps = new ArrayDeque<>();
    private long lastAnyPacketMs = 0L;

    @Override
    public void onActivate() {
        timePacketTimestamps.clear();
        lastAnyPacketMs = System.currentTimeMillis();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        // Controllo freeze totale: gira ad ogni tick del CLIENT, indipendentemente
        // da cosa arriva dal server. Se il server si blocca del tutto e non manda
        // piu' nessun pacchetto, e' questo il controllo che se ne accorge.
        double secondsSinceAnyPacket = (System.currentTimeMillis() - lastAnyPacketMs) / 1000.0;
        if (secondsSinceAnyPacket >= freezeTimeoutSeconds.get()) {
            trigger(String.format("nessun pacchetto ricevuto da %.1f secondi (server bloccato)", secondsSinceAnyPacket));
        }
    }

    @EventHandler
    private void onReceive(PacketEvent.Receive event) {
        long now = System.currentTimeMillis();
        lastAnyPacketMs = now; // qualsiasi pacchetto conta per il rilevamento freeze

        if (!(event.packet instanceof ClientboundSetTimePacket)) return;

        timePacketTimestamps.addLast(now);
        while (!timePacketTimestamps.isEmpty() && now - timePacketTimestamps.peekFirst() > windowMs.get()) {
            timePacketTimestamps.pollFirst();
        }

        if (timePacketTimestamps.size() < minSamples.get()) return; // troppo pochi campioni, aspetta

        double seconds = (now - timePacketTimestamps.peekFirst()) / 1000.0;
        if (seconds <= 0) return;

        int intervals = timePacketTimestamps.size() - 1;
        double estimatedTps = Math.min((intervals * TICKS_PER_TIME_PACKET) / seconds, 20.0);

        if (estimatedTps < minTps.get()) {
            trigger(String.format("TPS stimato: %.1f", estimatedTps));
        }
    }

    private void trigger(String detail) {
        info("Disconnessione di sicurezza — %s", detail);

        // ATTENZIONE MAPPING 26.1.2: "Text.literal(...)" e "getConnection()" sono nomi
        // Yarn (validi fino a 1.21.x). Con le mappature Mojang ufficiali usate dalla 26.1,
        // il testo si crea probabilmente con qualcosa come Component.literal(...), e il
        // metodo di disconnessione potrebbe chiamarsi diversamente (es. disconnect() sulla
        // connessione di rete). Verifica questi due punti con l'autocomplete del tuo IDE
        // o guardando come lo fanno altri moduli gia' presenti nel tuo template 26.1.2.
        if (mc.getConnection() != null) {
            mc.getConnection().getConnection().disconnect(
                Component.literal("Disconnesso da TpsGuard: " + detail)
            );
        }

        if (disableAfter.get()) toggle();
    }
}
