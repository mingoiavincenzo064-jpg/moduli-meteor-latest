package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.game.GameJoinedEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.AbstractContainerMenu;
// ⚠️ CLASSE NON TROVATA IN COMPILAZIONE: nella tua build 26.1.2 "net.minecraft.world.inventory.ClickType"
// non risulta più qui. Cancella questa riga, poi nel punto dove usi "ClickType.PICKUP" più sotto
// usa l'auto-import del tuo IDE (Alt+Invio in IntelliJ) per farti trovare il pacchetto corretto:
// l'IDE ha già i sorgenti mappati reali della tua versione, io da remoto non riesco a confermarlo.
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class AutoSheepFarm extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> interactRadius = sgGeneral.add(new DoubleSetting.Builder()
        .name("raggio-interazione")
        .description("Distanza massima per shearare in sicurezza.")
        .defaultValue(3.0)
        .min(1.0).max(4.5)
        .build()
    );

    private final Setting<Double> searchRadius = sgGeneral.add(new DoubleSetting.Builder()
        .name("raggio-ricerca")
        .description("Raggio entro cui cercare pecore lontane da raggiungere camminando.")
        .defaultValue(40.0)
        .min(5.0).max(64.0)
        .build()
    );

    private final Setting<Double> maxYDifference = sgGeneral.add(new DoubleSetting.Builder()
        .name("differenza-y-massima")
        .description("Ignora pecore e lana caduta a un'altezza (Y) troppo diversa dalla tua, utile per farm a più piani.")
        .defaultValue(4.0)
        .min(1.0).max(50.0)
        .build()
    );

    private final Setting<Double> wanderSpeed = sgGeneral.add(new DoubleSetting.Builder()
        .name("velocita-ricerca")
        .description("Velocità con cui ti sposti verso una pecora lontana.")
        .defaultValue(0.28)
        .min(0.05).max(0.5)
        .build()
    );

    private final Setting<Integer> wanderTimeout = sgGeneral.add(new IntSetting.Builder()
        .name("timeout-ricerca")
        .description("Tick massimi spesi a camminare verso una pecora lontana prima di riprovare da capo.")
        .defaultValue(200)
        .build()
    );

    private final Setting<Integer> stackAmount = sgGeneral.add(new IntSetting.Builder()
        .name("stack-richiesto")
        .description("Numero minimo nello stack per shearare.")
        .defaultValue(500)
        .build()
    );

    private final Setting<Integer> actionDelay = sgGeneral.add(new IntSetting.Builder()
        .name("ritardo-tick")
        .description("Ritardo (in tick) tra un'azione e l'altra. 20 tick = 1 secondo.")
        .defaultValue(6)
        .min(1).max(40)
        .build()
    );

    private final Setting<Integer> scanInterval = sgGeneral.add(new IntSetting.Builder()
        .name("intervallo-scansione")
        .description("Ogni quanti tick rifare le ricerche pesanti. Più alto = meno carico, meno reattivo.")
        .defaultValue(4)
        .min(1).max(20)
        .build()
    );

    private final Setting<String> shopCommand = sgGeneral.add(new StringSetting.Builder()
        .name("comando-shop")
        .description("Comando per aprire lo shop, senza la barra iniziale.")
        .defaultValue("shop Blocks")
        .build()
    );

    private final Setting<Integer> containerSize = sgGeneral.add(new IntSetting.Builder()
        .name("slot-totali-pagina")
        .description("Quanti slot ha la parte shop di ogni pagina.")
        .defaultValue(18)
        .build()
    );

    private final Setting<Integer> nextPageSlot = sgGeneral.add(new IntSetting.Builder()
        .name("slot-pagina-successiva")
        .description("Slot del vetro blu 'Pagina Successiva' (parte da 0).")
        .defaultValue(14)
        .build()
    );

    private final Setting<Integer> maxPageAttempts = sgGeneral.add(new IntSetting.Builder()
        .name("max-pagine")
        .description("Numero massimo di pagine da provare prima di arrendersi.")
        .defaultValue(10)
        .build()
    );

    private final Setting<String> woolName = sgGeneral.add(new StringSetting.Builder()
        .name("nome-lana")
        .description("Testo da cercare nel nome dell'oggetto lana.")
        .defaultValue("wool")
        .build()
    );

    private final Setting<Integer> emeraldSlot = sgGeneral.add(new IntSetting.Builder()
        .name("slot-smeraldo")
        .description("Slot dello smeraldo 'conferma vendita' (parte da 0).")
        .defaultValue(31)
        .build()
    );

    private final Setting<Double> lootSearchRadius = sgGeneral.add(new DoubleSetting.Builder()
        .name("raggio-ricerca-lana")
        .description("Raggio in cui cercare la lana caduta a terra.")
        .defaultValue(8.0)
        .min(1.0).max(20.0)
        .build()
    );

    private final Setting<Double> moveSpeed = sgGeneral.add(new DoubleSetting.Builder()
        .name("velocita-recupero")
        .description("Velocità con cui ti sposti verso la lana caduta.")
        .defaultValue(0.28)
        .min(0.05).max(0.5)
        .build()
    );

    private final Setting<Integer> moveTimeout = sgGeneral.add(new IntSetting.Builder()
        .name("timeout-recupero")
        .description("Tick massimi spesi a inseguire la lana prima di rinunciare.")
        .defaultValue(60)
        .build()
    );

    private final Setting<Double> arrivalDistance = sgGeneral.add(new DoubleSetting.Builder()
        .name("distanza-arrivo")
        .description("Quando sei abbastanza vicino da fermarti e considerare la lana raccolta.")
        .defaultValue(1.3)
        .min(0.5).max(4.0)
        .build()
    );

    private final Setting<Integer> maxWaitRetries = sgGeneral.add(new IntSetting.Builder()
        .name("max-attese")
        .description("Tentativi massimi di attesa GUI prima di arrendersi e resettare.")
        .defaultValue(15)
        .build()
    );

    private enum State {
        IDLE, WANDER, TARGETING, SHEAR, WAIT_AFTER_SHEAR, MOVE_TO_LOOT,
        OPEN_SHOP, WAIT_SHOP_OPEN, SEARCH_WOOL,
        OPEN_SELL_MENU, WAIT_SELL_MENU, CONFIRM_SELL,
        CLOSE
    }

    private State state = State.IDLE;
    private Entity target;
    private Entity cachedLootItem;
    private int delayTicks = 0;
    private int moveTicks = 0;
    private int wanderTicks = 0;
    private int pageAttempts = 0;
    private int waitRetries = 0;
    private int scanCooldown = 0;
    private int foundWoolSlot = -1;

    public AutoSheepFarm() {
        super(AddonTemplate.CATEGORY, "auto-sheep-farm", "Cerca pecore a stack pieno, le sheara, riempie l'inventario e vende tutto.");
    }

    @Override
    public void onActivate() {
        resetState();
    }

    private void resetState() {
        state = State.IDLE;
        target = null;
        cachedLootItem = null;
        delayTicks = 0;
        moveTicks = 0;
        wanderTicks = 0;
        pageAttempts = 0;
        waitRetries = 0;
        scanCooldown = 0;
        foundWoolSlot = -1;
        if (mc.player != null) mc.player.setSprinting(false);
    }

    @EventHandler
    private void onGameJoined(GameJoinedEvent event) {
        resetState();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        // ⚠️ VERIFICARE: in 1.21.4 (Yarn) il mondo client è "mc.world". Con le mappature Mojang
        // il nome del campo è "level" (mc.level). Se il compilatore si lamenta, prova mc.level().
        if (mc.player == null || mc.level == null) return;

        boolean movementState = state == State.MOVE_TO_LOOT || state == State.WANDER;
        if (delayTicks > 0 && !movementState) {
            delayTicks--;
            return;
        }

        switch (state) {
            case IDLE -> handleIdle();
            case WANDER -> wander();
            case TARGETING -> rotateToTarget();
            case SHEAR -> shear();
            case WAIT_AFTER_SHEAR -> {
                moveTicks = 0;
                state = State.MOVE_TO_LOOT;
            }
            case MOVE_TO_LOOT -> moveToLoot();
            case OPEN_SHOP -> openShop();
            case WAIT_SHOP_OPEN -> checkShopOpen();
            case SEARCH_WOOL -> searchWool();
            case OPEN_SELL_MENU -> openSellMenu();
            case WAIT_SELL_MENU -> checkSellMenuOpen();
            case CONFIRM_SELL -> confirmSell();
            case CLOSE -> closeShop();
        }
    }

    private void handleIdle() {
        if (scanCooldown > 0) {
            scanCooldown--;
            return;
        }
        scanCooldown = scanInterval.get();

        Entity loot = findNearestWoolItem();
        if (loot != null) {
            cachedLootItem = loot;
            moveTicks = 0;
            state = State.MOVE_TO_LOOT;
            return;
        }

        if (isInventoryFull()) {
            state = State.OPEN_SHOP;
            return;
        }

        Entity nearby = findValidSheep(interactRadius.get());
        if (nearby != null) {
            target = nearby;
            state = State.TARGETING;
            return;
        }

        Entity far = findValidSheep(searchRadius.get());
        if (far != null) {
            target = far;
            wanderTicks = 0;
            state = State.WANDER;
        }
    }

    // MODIFICATA: ora ignora le pecore troppo distanti in altezza (Y), utile per farm a più piani.
    private Entity findValidSheep(double radius) {
        String needle = stackAmount.get() + "x";
        double best = radius * radius;
        Entity found = null;

        // ⚠️ VERIFICARE: "mc.level.entitiesForRendering()" è il metodo più vicino a
        // "mc.world.getEntities()" (Yarn) con le mappature Mojang su ClientLevel.
        // Se non compila/non trova entità, controlla il template aggiornato
        // (github.com/MeteorDevelopment/meteor-addon-template) per il metodo corretto
        // di iterazione entità nella tua build di Meteor 26.1.2.
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof Sheep sheep)) continue;
            if (sheep.isSheared()) continue;
            if (sheep.getCustomName() == null) continue;
            if (!sheep.getCustomName().getString().contains(needle)) continue;
            if (Math.abs(sheep.getY() - mc.player.getY()) > maxYDifference.get()) continue;

            double dist = mc.player.distanceToSqr(sheep);
            if (dist <= best) {
                best = dist;
                found = sheep;
            }
        }

        return found;
    }

    private void wander() {
        if (target == null || !target.isAlive() || (target instanceof Sheep s && s.isSheared())) {
            mc.player.setSprinting(false);
            state = State.IDLE;
            return;
        }

        double dist = mc.player.distanceTo(target);

        if (dist <= interactRadius.get()) {
            mc.player.setSprinting(false);
            state = State.TARGETING;
            return;
        }

        if (wanderTicks >= wanderTimeout.get()) {
            mc.player.setSprinting(false);
            target = null;
            state = State.IDLE;
            delayTicks = actionDelay.get() * 2;
            return;
        }

        double dx = target.getX() - mc.player.getX();
        double dz = target.getZ() - mc.player.getZ();
        double horizDist = Math.sqrt(dx * dx + dz * dz);

        if (horizDist > 0.001) {
            mc.player.setSprinting(true);
            double speed = wanderSpeed.get();
            mc.player.setDeltaMovement(dx / horizDist * speed, mc.player.getDeltaMovement().y, dz / horizDist * speed);
        }

        wanderTicks++;
    }

    private void rotateToTarget() {
        if (target == null || !target.isAlive()) {
            state = State.IDLE;
            return;
        }

        double yaw = Rotations.getYaw(target);
        double pitch = Rotations.getPitch(target);

        Rotations.rotate(yaw, pitch, 100, () -> state = State.SHEAR);
    }

    private void shear() {
        if (target == null || !target.isAlive() || mc.player.distanceTo(target) > interactRadius.get()) {
            state = State.IDLE;
            return;
        }

        equipShears();

        mc.gameMode.interact(mc.player, target, InteractionHand.MAIN_HAND);
        mc.player.swing(InteractionHand.MAIN_HAND);

        delayTicks = actionDelay.get();
        state = State.WAIT_AFTER_SHEAR;
    }

    private void equipShears() {
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getItem(i).getItem() == Items.SHEARS) {
                // "selected" è privato dalla 1.21.5: si usa il setter pubblico.
                mc.player.getInventory().setSelectedSlot(i);
                return;
            }
        }
    }

    // MODIFICATA: ora ignora anche la lana caduta a un'altezza (Y) troppo diversa dalla tua.
    private Entity findNearestWoolItem() {
        String needle = woolName.get().toLowerCase();
        double best = lootSearchRadius.get() * lootSearchRadius.get();
        Entity found = null;

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof ItemEntity itemEntity)) continue;
            if (Math.abs(itemEntity.getY() - mc.player.getY()) > maxYDifference.get()) continue;

            ItemStack stack = itemEntity.getItem();
            if (stack.isEmpty()) continue;
            if (!stack.getHoverName().getString().toLowerCase().contains(needle)) continue;

            double dist = mc.player.distanceToSqr(entity);
            if (dist <= best) {
                best = dist;
                found = entity;
            }
        }

        return found;
    }

    private void moveToLoot() {
        if (scanCooldown <= 0) {
            cachedLootItem = findNearestWoolItem();
            scanCooldown = scanInterval.get();
        } else {
            scanCooldown--;
        }

        Entity woolItem = cachedLootItem;

        if (woolItem == null || !woolItem.isAlive() || moveTicks >= moveTimeout.get()) {
            mc.player.setSprinting(false);
            delayTicks = actionDelay.get();
            proceedAfterLoot();
            return;
        }

        double dx = woolItem.getX() - mc.player.getX();
        double dz = woolItem.getZ() - mc.player.getZ();
        double distSq = dx * dx + dz * dz;

        if (distSq <= arrivalDistance.get() * arrivalDistance.get()) {
            mc.player.setSprinting(false);
            delayTicks = actionDelay.get();
            proceedAfterLoot();
            return;
        }

        double dist = Math.sqrt(distSq);
        double speed = moveSpeed.get();

        mc.player.setSprinting(true);
        mc.player.setDeltaMovement(dx / dist * speed, mc.player.getDeltaMovement().y, dz / dist * speed);
        moveTicks++;
    }

    private void proceedAfterLoot() {
        state = isInventoryFull() ? State.OPEN_SHOP : State.IDLE;
    }

    private boolean isInventoryFull() {
        for (int i = 0; i < 36; i++) {
            if (mc.player.getInventory().getItem(i).isEmpty()) return false;
        }
        return true;
    }

    private void openShop() {
        // ⚠️ VERIFICARE: nome del metodo per inviare un comando chat con le mappature Mojang.
        // "connection" sostituisce "networkHandler"; il metodo dovrebbe chiamarsi "sendCommand".
        mc.player.connection.sendCommand(shopCommand.get());
        delayTicks = actionDelay.get() * 3;
        pageAttempts = 0;
        waitRetries = 0;
        state = State.WAIT_SHOP_OPEN;
    }

    private void checkShopOpen() {
        if (mc.screen instanceof AbstractContainerScreen<?>) {
            state = State.SEARCH_WOOL;
            return;
        }

        waitRetries++;
        if (waitRetries >= maxWaitRetries.get()) {
            resetState();
            return;
        }
        delayTicks = actionDelay.get();
    }

    private void searchWool() {
        if (!(mc.screen instanceof AbstractContainerScreen<?> screen)) {
            state = State.IDLE;
            return;
        }

        AbstractContainerMenu menu = screen.getMenu();
        String needle = woolName.get().toLowerCase();

        for (int i = 0; i < containerSize.get(); i++) {
            ItemStack stack = menu.getSlot(i).getItem();
            if (stack.isEmpty()) continue;

            String name = stack.getHoverName().getString().toLowerCase();
            if (name.contains(needle)) {
                foundWoolSlot = i;
                state = State.OPEN_SELL_MENU;
                return;
            }
        }

        pageAttempts++;
        if (pageAttempts >= maxPageAttempts.get()) {
            mc.player.closeContainer();
            state = State.IDLE;
            return;
        }

        mc.gameMode.handleInventoryMouseClick(menu.containerId, nextPageSlot.get(), 0, ClickType.PICKUP, mc.player);
        delayTicks = actionDelay.get();
    }

    private void openSellMenu() {
        if (!(mc.screen instanceof AbstractContainerScreen<?> screen) || foundWoolSlot < 0) {
            state = State.IDLE;
            return;
        }

        AbstractContainerMenu menu = screen.getMenu();
        mc.gameMode.handleInventoryMouseClick(menu.containerId, foundWoolSlot, 1, ClickType.PICKUP, mc.player);

        delayTicks = actionDelay.get() * 2;
        waitRetries = 0;
        state = State.WAIT_SELL_MENU;
    }

    private void checkSellMenuOpen() {
        if (mc.screen instanceof AbstractContainerScreen<?>) {
            state = State.CONFIRM_SELL;
            return;
        }

        waitRetries++;
        if (waitRetries >= maxWaitRetries.get()) {
            resetState();
            return;
        }
        delayTicks = actionDelay.get();
    }

    private void confirmSell() {
        if (!(mc.screen instanceof AbstractContainerScreen<?> screen)) {
            state = State.IDLE;
            return;
        }

        AbstractContainerMenu menu = screen.getMenu();
        mc.gameMode.handleInventoryMouseClick(menu.containerId, emeraldSlot.get(), 0, ClickType.PICKUP, mc.player);

        delayTicks = actionDelay.get();
        state = State.CLOSE;
    }

    private void closeShop() {
        mc.player.closeContainer();
        target = null;
        foundWoolSlot = -1;
        state = State.IDLE;
    }
}
