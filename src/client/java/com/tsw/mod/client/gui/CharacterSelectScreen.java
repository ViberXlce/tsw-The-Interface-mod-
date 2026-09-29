package com.tsw.mod.client.gui;

import com.tsw.mod.client.skin.SkinCache;
import com.tsw.mod.network.TSWNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.List;

/**
 * The screen shown to every player right when they join the server
 * (before they are considered "in the world" for TSW). Shows the 3
 * character slots side by side.
 *
 * - An EMPTY slot shows just a big "+" button. Pressing it creates a
 *   brand new character in that slot and immediately plays as it (no
 *   skin field is shown until the character actually exists, matching
 *   the requested "no skin until you choose" flow).
 * - An OCCUPIED slot shows a live rotating preview of the player model,
 *   a skin URL field (to change it), a Save button, and a Select button.
 *
 * This screen intentionally cannot be closed with Escape: a character
 * must be chosen before play continues.
 */
public class CharacterSelectScreen extends Screen {

    private static final int SLOT_COUNT = 3;
    private static final int PANEL_WIDTH = 150;
    private static final int PANEL_HEIGHT = 200;
    private static final int PANEL_GAP = 20;

    private final List<TSWNetworking.SlotInfo> initialSlots;
    private final TextFieldWidget[] urlFields = new TextFieldWidget[SLOT_COUNT];
    private final boolean[] occupiedSlots = new boolean[SLOT_COUNT];

    public CharacterSelectScreen(List<TSWNetworking.SlotInfo> initialSlots) {
        super(Text.translatable("tsw.screen.title"));
        this.initialSlots = initialSlots;
    }

    @Override
    protected void init() {
        int totalWidth = SLOT_COUNT * PANEL_WIDTH + (SLOT_COUNT - 1) * PANEL_GAP;
        int startX = (this.width - totalWidth) / 2;
        int panelY = (this.height - PANEL_HEIGHT) / 2;

        for (int i = 0; i < SLOT_COUNT; i++) {
            int slot = i;
            int panelX = startX + i * (PANEL_WIDTH + PANEL_GAP);

            TSWNetworking.SlotInfo info = slot < initialSlots.size() ? initialSlots.get(slot) : null;
            boolean occupied = info != null && info.occupied();
            occupiedSlots[slot] = occupied;

            if (!occupied) {
                // Empty slot: only a big "+" button, nothing else.
                this.addDrawableChild(ButtonWidget.builder(Text.literal("+"), b -> createSlot(slot))
                        .dimensions(panelX + PANEL_WIDTH / 2 - 25, panelY + PANEL_HEIGHT / 2 - 15, 50, 30)
                        .build());
                continue;
            }

            String existingUrl = info.skinUrl();

            TextFieldWidget urlField = new TextFieldWidget(this.textRenderer,
                    panelX + 10, panelY + PANEL_HEIGHT - 60, PANEL_WIDTH - 20, 20,
                    Text.translatable("tsw.slot.skinUrl"));
            urlField.setMaxLength(512);
            urlField.setText(existingUrl == null ? "" : existingUrl);
            urlFields[slot] = urlField;
            this.addDrawableChild(urlField);

            this.addDrawableChild(ButtonWidget.builder(Text.translatable("tsw.slot.save"), b -> saveSkin(slot))
                    .dimensions(panelX + 10, panelY + PANEL_HEIGHT - 34, PANEL_WIDTH - 20, 16)
                    .build());

            this.addDrawableChild(ButtonWidget.builder(Text.translatable("tsw.slot.select"), b -> selectSlot(slot))
                    .dimensions(panelX + 10, panelY + PANEL_HEIGHT - 12, PANEL_WIDTH - 20, 20)
                    .build());
        }
    }

    private void saveSkin(int slot) {
        String url = urlFields[slot].getText();
        ClientPlayNetworking.send(new TSWNetworking.UpdateSkinUrlC2S(slot, url));
    }

    private void selectSlot(int slot) {
        saveSkin(slot);
        ClientPlayNetworking.send(new TSWNetworking.SelectCharacterC2S(slot));
        this.close();
    }

    private void createSlot(int slot) {
        // A fresh character: nothing to save yet, just claim the slot and play.
        ClientPlayNetworking.send(new TSWNetworking.SelectCharacterC2S(slot));
        this.close();
    }

    @Override
    public boolean shouldCloseOnEsc() {
        // A character must be chosen before returning to normal play.
        return false;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Plain dark overlay instead of Screen#renderBackground: recent
        // versions only allow one background-blur call per frame, and
        // this screen does not need the blur effect anyway.
        context.fill(0, 0, this.width, this.height, 0xF0101018);

        // "TSW" wordmark, drawn twice with a 1px offset for a cheap bold look.
        int titleY = 34;
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("TSW").formatted(net.minecraft.util.Formatting.LIGHT_PURPLE), this.width / 2 + 1, titleY, 0xFFFFFF);
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("TSW").formatted(net.minecraft.util.Formatting.LIGHT_PURPLE), this.width / 2, titleY, 0xFFFFFF);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, titleY + 14, 0xA0A0A0);

        int totalWidth = SLOT_COUNT * PANEL_WIDTH + (SLOT_COUNT - 1) * PANEL_GAP;
        int startX = (this.width - totalWidth) / 2;
        int panelY = (this.height - PANEL_HEIGHT) / 2;

        for (int i = 0; i < SLOT_COUNT; i++) {
            int panelX = startX + i * (PANEL_WIDTH + PANEL_GAP);
            context.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + PANEL_HEIGHT, 0x902A2A38);
            // Manual 1px border (avoids DrawContext#drawBorder, whose
            // signature has shifted across versions): top, bottom, left, right.
            int borderColor = 0x60FFFFFF;
            context.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + 1, borderColor);
            context.fill(panelX, panelY + PANEL_HEIGHT - 1, panelX + PANEL_WIDTH, panelY + PANEL_HEIGHT, borderColor);
            context.fill(panelX, panelY, panelX + 1, panelY + PANEL_HEIGHT, borderColor);
            context.fill(panelX + PANEL_WIDTH - 1, panelY, panelX + PANEL_WIDTH, panelY + PANEL_HEIGHT, borderColor);

            context.drawCenteredTextWithShadow(this.textRenderer,
                    Text.translatable("tsw.slot.slotNumber", i + 1),
                    panelX + PANEL_WIDTH / 2, panelY + 8, 0xFFFFFF);

            if (occupiedSlots[i]) {
                renderRotatingPreview(context, panelX, panelY);
            }
            // Empty slots draw nothing extra here - the "+" button (added
            // in init()) already sits centered in the panel.
        }

        super.render(context, mouseX, mouseY, delta);
    }

    /**
     * Draws the local player's model rotating slowly inside an occupied
     * slot's panel.
     *
     * MAPPING NOTE: `InventoryScreen.drawEntity(...)` is the same static
     * helper vanilla itself uses to render the player inside the
     * survival-inventory background. If its parameter list does not
     * match your exact 1.21.11 mappings, open InventoryScreen in your
     * IDE, copy the up-to-date signature, and adjust this single call.
     */
    private void renderRotatingPreview(DrawContext context, int panelX, int panelY) {
        if (this.client == null || this.client.player == null) {
            return;
        }
        int previewX0 = panelX + 20;
        int previewY0 = panelY + 26;
        int previewX1 = panelX + PANEL_WIDTH - 20;
        int previewY1 = panelY + PANEL_HEIGHT - 70;

        net.minecraft.client.gui.screen.ingame.InventoryScreen.drawEntity(
                context,
                previewX0, previewY0, previewX1, previewY1,
                30,
                0.0f,
                previewX0 + (previewX1 - previewX0) / 2f,
                previewY0 + (previewY1 - previewY0) / 2f,
                this.client.player
        );
    }
}
