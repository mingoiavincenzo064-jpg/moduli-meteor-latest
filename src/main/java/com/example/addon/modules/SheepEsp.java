package com.example.addon.modules;
 
import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
 
// --- Import aggiornati per 26.1.2 (Mojang mappings, la 1.21.4 usava Yarn) ---
import net.minecraft.world.entity.animal.sheep.Sheep;   // prima: net.minecraft.entity.passive.SheepEntity
import net.minecraft.world.phys.AABB;             // prima: net.minecraft.util.math.Box
 
public class SheepEsp extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
 
    private final Setting<ShapeMode> shapeMode = sgGeneral.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .description("Come disegnare l'ESP.")
        .defaultValue(ShapeMode.Both)
        .build()
    );
 
    private final Setting<SettingColor> sideColor = sgGeneral.add(new ColorSetting.Builder()
        .name("colore-riempimento")
        .description("Colore del riempimento del box.")
        .defaultValue(new SettingColor(255, 255, 255, 50))
        .build()
    );
 
    private final Setting<SettingColor> lineColor = sgGeneral.add(new ColorSetting.Builder()
        .name("colore-bordo")
        .description("Colore del bordo del box.")
        .defaultValue(new SettingColor(255, 255, 255, 255))
        .build()
    );
 
    private final Setting<Boolean> onlyColoredSheep = sgGeneral.add(new BoolSetting.Builder()
        .name("solo-pecore-colorate")
        .description("Evidenzia solo pecore con lana colorata (non bianche).")
        .defaultValue(false)
        .build()
    );
 
    private final Setting<Double> countRadius = sgGeneral.add(new DoubleSetting.Builder()
        .name("raggio-conteggio")
        .description("Raggio entro cui contare le pecore rilevate (mostrato accanto al nome del modulo).")
        .defaultValue(16.0)
        .min(1.0).max(128.0)
        .build()
    );
 
    private int sheepCount = 0;
 
    public SheepEsp() {
        super(AddonTemplate.CATEGORY, "sheep-esp", "Evidenzia le pecore attraverso i muri.");
    }
 
    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (mc.level == null || mc.player == null) return; // prima: mc.world == null
 
        int count = 0;
        double radiusSq = countRadius.get() * countRadius.get();
 
        // entitiesForRendering() = tutte le entità caricate lato client (anche dietro i muri).
        // Prima usavi mc.world.getEntities(), che su 26.1.2 non è garantito allo stesso modo:
        // questo è il metodo corretto e più affidabile per un ESP.
        for (var entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof Sheep sheep)) continue;
            if (onlyColoredSheep.get() && sheep.getColor().getId() == 15) continue; // 15 = bianco/default
 
            // prima: mc.player.squaredDistanceTo(sheep)
            if (mc.player.distanceToSqr(sheep) <= radiusSq) {
                count++;
            }
 
            AABB box = sheep.getBoundingBox();
 
            // Interpolazione posizione tra i tick.
            // prima: sheep.prevX / prevY / prevZ -> ora: sheep.xo / yo / zo
            double x = sheep.xo + (sheep.getX() - sheep.xo) * event.tickDelta;
            double y = sheep.yo + (sheep.getY() - sheep.yo) * event.tickDelta;
            double z = sheep.zo + (sheep.getZ() - sheep.zo) * event.tickDelta;
 
            // prima: box.offset(...) -> ora: AABB.move(...)
            AABB renderBox = box.move(x - sheep.getX(), y - sheep.getY(), z - sheep.getZ());
 
            event.renderer.box(
                renderBox,
                sideColor.get(), lineColor.get(),
                shapeMode.get(), 0
            );
        }
 
        sheepCount = count;
    }
 
    @Override
    public String getInfoString() {
        return String.valueOf(sheepCount);
    }
}
 
