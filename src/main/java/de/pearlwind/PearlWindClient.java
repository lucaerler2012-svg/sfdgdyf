package de.pearlwind;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Items;

/**
 * Pearl-Catcher: Schaust du (fast) senkrecht nach oben und wirfst eine Enderperle, wechselt die
 * Mod auf eine Wind Charge in der Hotbar und wirft sie hinterher. Danach geht es zurueck auf den
 * vorherigen Slot (die Perle).
 *
 * Die Kamera wird nicht bewegt. Hast du keine Wind Charge in der Hotbar (Slots 1-9), passiert nichts.
 */
public class PearlWindClient implements ClientModInitializer {

    /** Ab diesem Blickwinkel gilt es als "nach oben" (-90 = senkrecht nach oben). */
    private static final float MIN_UP_PITCH = -80.0f;
    /** Nach dem Wurf zurueck auf den vorherigen Slot wechseln? */
    private static final boolean RESTORE_SLOT = true;

    private enum Stage { IDLE, SWITCH, THROW, RESTORE }

    private Stage stage = Stage.IDLE;
    private int previousSlot = -1;

    @Override
    public void onInitializeClient() {
        // Wird beim Benutzen eines Items (Rechtsklick) ausgeloest, bevor es gesendet wird.
        UseItemCallback.EVENT.register((player, level, hand) -> {
            if (level.isClientSide()
                    && stage == Stage.IDLE
                    && hand == InteractionHand.MAIN_HAND
                    && player.getItemInHand(hand).is(Items.ENDER_PEARL)
                    && player.getXRot() <= MIN_UP_PITCH) {
                previousSlot = player.getInventory().getSelectedSlot();
                stage = Stage.SWITCH;
            }
            return InteractionResult.PASS;
        });

        ClientTickEvents.START_CLIENT_TICK.register(this::onTick);
    }

    private void onTick(Minecraft client) {
        if (stage == Stage.IDLE) {
            return;
        }
        if (client.player == null || client.gameMode == null) {
            reset();
            return;
        }

        Inventory inv = client.player.getInventory();

        switch (stage) {
            case SWITCH -> {
                // Die Perle wurde im letzten Tick geworfen. Jetzt auf die Wind Charge wechseln.
                int slot = findWindCharge(inv);
                if (slot < 0) {
                    reset();
                    return;
                }
                inv.setSelectedSlot(slot);
                stage = Stage.THROW;
            }
            case THROW -> {
                // Ein Tick Abstand, damit der Slot-Wechsel vor dem Wurf beim Server ankommt.
                if (inv.getItem(inv.getSelectedSlot()).is(Items.WIND_CHARGE)) {
                    client.gameMode.useItem(client.player, InteractionHand.MAIN_HAND);
                }
                stage = RESTORE_SLOT ? Stage.RESTORE : Stage.IDLE;
                if (stage == Stage.IDLE) {
                    previousSlot = -1;
                }
            }
            case RESTORE -> {
                if (previousSlot >= 0) {
                    inv.setSelectedSlot(previousSlot);
                }
                reset();
            }
            default -> reset();
        }
    }

    private static int findWindCharge(Inventory inv) {
        for (int slot = 0; slot < 9; slot++) {
            if (inv.getItem(slot).is(Items.WIND_CHARGE)) {
                return slot;
            }
        }
        return -1;
    }

    private void reset() {
        stage = Stage.IDLE;
        previousSlot = -1;
    }
}
