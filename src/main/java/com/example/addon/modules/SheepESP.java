package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.passive.SheepEntity;
import net.minecraft.util.math.Box;
 
/**
 * Modulo ESP per le pecore.
 * Evidenzia tutte le SheepEntity nel mondo, anche attraverso i muri.
 *
 * NOTA: i nomi esatti delle classi/metodi di Meteor Client (Categories,
 * ShapeMode, firma di event.renderer.box(...), ecc.) possono cambiare
 * leggermente tra le varie build/versioni. Se compilando trovi errori,
 * controlla la versione dei sorgenti di Meteor Client che stai usando
 * come dipendenza e adegua gli import/firme di conseguenza.
 */
public class SheepESP extends Module {
 
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
 
    // Modalità di disegno: solo contorno, solo riempimento, entrambi
    private final Setting<ShapeMode> shapeMode = sgGeneral.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .description("Come vengono disegnate le forme.")
        .defaultValue(ShapeMode.Both)
        .build()
    );
 
    // Colore di riempimento del box
    private final Setting<SettingColor> fillColor = sgGeneral.add(new ColorSetting.Builder()
        .name("colore-riempimento")
        .description("Colore di riempimento del box attorno alla pecora.")
        .defaultValue(new SettingColor(255, 255, 255, 60))
        .build()
    );
 
    // Colore del contorno
    private final Setting<SettingColor> lineColor = sgGeneral.add(new ColorSetting.Builder()
        .name("colore-contorno")
        .description("Colore del contorno del box.")
        .defaultValue(new SettingColor(255, 255, 255, 255))
        .build()
    );
 
    // Quanto espandere il box attorno all'entità (utile per renderlo più visibile)
    private final Setting<Double> boxExpand = sgGeneral.add(new DoubleSetting.Builder()
        .name("espansione-box")
        .description("Espande leggermente il box di rendering.")
        .defaultValue(0.0)
        .min(0)
        .sliderMax(0.5)
        .build()
    );
 
    public SheepESP() {
        // Sostituisci "Categories.Render" con la categoria del tuo addon
        // (es. TuoAddon.CATEGORY) se ne hai definita una personalizzata.
        super(meteordevelopment.meteorclient.systems.modules.Categories.Render,
            "sheep-esp",
            "Evidenzia le pecore attraverso i muri.");
    }
 
    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (mc.world == null) return;
 
        for (SheepEntity sheep : mc.world.getEntitiesByClass(
                SheepEntity.class,
                mc.world.getWorldBorder().asBox(),
                entity -> true
        )) {
            Box box = sheep.getBoundingBox().expand(boxExpand.get());
 
            event.renderer.box(
                box,
                fillColor.get(),
                lineColor.get(),
                shapeMode.get(),
                0
            );
        }
    }
}
 
