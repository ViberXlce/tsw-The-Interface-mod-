package com.tsw.mod.client.gui;

import com.tsw.mod.network.TSWNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.List;

/**
 * The screen shown to every player right when they join the server
 * (before they are considered "in the world" for TSW).
 *
 * Layout: one large "focused" character shown centered with a big
 * rotating preview and a big Play/Create button, and the other two
 * slots shown as small side thumbnails you click to switch focus.
 * This screen intentionally cannot be closed with Escape: a character
 * must be chosen before play continues.
 */
public class CharacterSelectScreen extends Screen {

    private static final int SLOT_COUNT = 3;

    private static final int CENTER_WIDTH = 260;
    private static final int CENTER_HEIGHT = 300;
    private static final int THUMB_WIDTH = 90;
    private static final int THUMB_HEIGHT = 140;
    private static final int GAP = 24;

    private final List<TSWNetworking.SlotInfo> slots;
    private final boolean[] occupied = new boolean[SLOT_COUNT];
    private TextFieldWidget skinField;

    /** Which slot is currently shown big in the middle. */
    private int focusedSlot;

    public CharacterSelectScreen(List<TSWNetworking.SlotInfo> initialSlots) {
        super(Text.translatable("tsw.screen.title"));
        this.slots = initialSlots;
        for (int i = 0; i < SLOT_COUNT; i++) {
            TSWNetworking.SlotInfo info = i < initialSlots.size() ? initialSlots.get(i) : null;
            occupied[i] = info != null && info.occupied();
        }
        // Start focused on the first occupied slot, or slot 0 if all empty.
        this.focusedSlot = 0;
        for (int i = 0; i < SLOT_COUNT; i++) {
            if (occupied[i]) {
                this.focusedSlot = i;
                break;
            }
        }
    }

    private String skinUrlOf(int slot) {
        TSWNetworking.SlotInfo info = slot < slots.size() ? slots.get(slot) : null;
        return info != null ? info.skinUrl() : "";
    }

    @Override
    protected void init() {
        rebuildWidgets();
    }

    private void rebuildWidgets() {
        this.clearChildren();

        int centerX = (this.width - CENTER_WIDTH) / 2;
        int centerY = (this.height - CENTER_HEIGHT) / 2;

        // ---- Center (focused) slot ----
        if (occupied[focusedSlot]) {
            skinField = new TextFieldWidget(this.textRenderer,
                    centerX + 20, centerY + CENTER_HEIGHT - 78, CENTER_WIDTH - 40, 20,
                    Text.translatable("tsw.slot.skinUrl"));
            skinField.setMaxLength(512);
            skinField.setText(skinUrlOf(focusedSlot));
            this.addDrawableChild(skinField);

            this.addDrawableChild(ButtonWidget.builder(Text.translatable("tsw.slot.save"), b -> saveSkin(focusedSlot))
                    .dimensions(centerX + 20, centerY + CENTER_HEIGHT - 52, CENTER_WIDTH - 40, 16)
                    .build());

            this.addDrawableChild(ButtonWidget.builder(Text.translatable("tsw.slot.select"), b -> selectSlot(focusedSlot))
                    .dimensions(centerX + 20, centerY + CENTER_HEIGHT - 30, CENTER_WIDTH - 40, 24)
                    .build());
        } else {
            skinField = null;
            this.addDrawableChild(ButtonWidget.builder(Text.literal("+"), b -> createSlot(focusedSlot))
                    .dimensions(centerX + CENTER_WIDTH / 2 - 30, centerY + CENTER_HEIGHT - 40, 60, 30)
                    .build());
        }

        // ---- Side thumbnails (the other two slots) ----
        int leftSlot = Math.floorMod(focusedSlot - 1, SLOT_COUNT);
        int rightSlot = Math.floorMod(focusedSlot + 1, SLOT_COUNT);

        int leftX = centerX - GAP - THUMB_WIDTH;
        int rightX = centerX + CENTER_WIDTH + GAP;
        int thumbY = centerY + (CENTER_HEIGHT - THUMB_HEIGHT) / 2;

        Text leftLabel = occupied[leftSlot]
                ? Text.translatable("tsw.slot.slotNumber", leftSlot + 1)
                : Text.literal("+");
        this.addDrawableChild(ButtonWidget.builder(leftLabel, b -> focusSlot(leftSlot))
                .dimensions(leftX, thumbY + THUMB_HEIGHT - 24, THUMB_WIDTH, 20)
                .build());

        Text rightLabel = occupied[rightSlot]
                ? Text.translatable("tsw.slot.slotNumber", rightSlot + 1)
                : Text.literal("+");
        this.addDrawableChild(ButtonWidget.builder(rightLabel, b -> focusSlot(rightSlot))
                .dimensions(rightX, thumbY + THUMB_HEIGHT - 24, THUMB_WIDTH, 20)
                .build());
    }

    private void focusSlot(int slot) {
        this.focusedSlot = slot;
        rebuildWidgets();
    }

    private void saveSkin(int slot) {
        if (skinField == null) {
            return;
        }
        ClientPlayNetworking.send(new TSWNetworking.UpdateSkinUrlC2S(slot, skinField.getText()));
    }

    private void selectSlot(int slot) {
        saveSkin(slot);
        ClientPlayNetworking.send(new TSWNetworking.SelectCharacterC2S(slot));
        this.close();
    }

    private void createSlot(int slot) {
        ClientPlayNetworking.send(new TSWNetworking.SelectCharacterC2S(slot));
        this.close();
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderAnimatedBackground(context);

        // "TSW" logo, top-left corner (matches a typical top-left wordmark).
        context.drawTextWithShadow(this.textRenderer, Text.literal("TSW"), 16, 14, 0xD9B8FF);
        context.drawTextWithShadow(this.textRenderer, this.title, 16, 26, 0x9090A0);

        int centerX = (this.width - CENTER_WIDTH) / 2;
        int centerY = (this.height - CENTER_HEIGHT) / 2;

        drawPanel(context, centerX, centerY, CENTER_WIDTH, CENTER_HEIGHT, 0xA02A2A40);

        if (occupied[focusedSlot]) {
            renderPreview(context, centerX + 20, centerY + 16, centerX + CENTER_WIDTH - 20, centerY + CENTER_HEIGHT - 110, 45);
            context.drawCenteredTextWithShadow(this.textRenderer,
                    Text.translatable("tsw.slot.slotNumber", focusedSlot + 1),
                    centerX + CENTER_WIDTH / 2, centerY + CENTER_HEIGHT - 100, 0xFFFFFF);
        } else {
            context.drawCenteredTextWithShadow(this.textRenderer,
                    Text.translatable("tsw.slot.empty"),
                    centerX + CENTER_WIDTH / 2, centerY + CENTER_HEIGHT / 2 - 40, 0xB0B0B0);
        }

        int leftSlot = Math.floorMod(focusedSlot - 1, SLOT_COUNT);
        int rightSlot = Math.floorMod(focusedSlot + 1, SLOT_COUNT);
        int leftX = centerX - GAP - THUMB_WIDTH;
        int rightX = centerX + CENTER_WIDTH + GAP;
        int thumbY = centerY + (CENTER_HEIGHT - THUMB_HEIGHT) / 2;

        drawPanel(context, leftX, thumbY, THUMB_WIDTH, THUMB_HEIGHT, 0x702A2A40);
        drawPanel(context, rightX, thumbY, THUMB_WIDTH, THUMB_HEIGHT, 0x702A2A40);

        if (occupied[leftSlot]) {
            renderPreview(context, leftX + 10, thumbY + 8, leftX + THUMB_WIDTH - 10, thumbY + THUMB_HEIGHT - 30, 20);
        }
        if (occupied[rightSlot]) {
            renderPreview(context, rightX + 10, thumbY + 8, rightX + THUMB_WIDTH - 10, thumbY + THUMB_HEIGHT - 30, 20);
        }

        int occupiedCount = 0;
        for (boolean b : occupied) {
            if (b) occupiedCount++;
        }
        context.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal(occupiedCount + " / " + SLOT_COUNT),
                this.width / 2, centerY + CENTER_HEIGHT + 14, 0x9090A0);

        super.render(context, mouseX, mouseY, delta);
    }

    private void drawPanel(DrawContext context, int x, int y, int w, int h, int fillColor) {
        context.fill(x, y, x + w, y + h, fillColor);
        int border = 0x50FFFFFF;
        context.fill(x, y, x + w, y + 1, border);
        context.fill(x, y + h - 1, x + w, y + h, border);
        context.fill(x, y, x + 1, y + h, border);
        context.fill(x + w - 1, y, x + w, y + h, border);
    }

    /**
     * A dark purple-to-black gradient with a handful of small dots
     * slowly drifting upward, to give the screen some life without
     * needing any external image assets. Uses only DrawContext#fill /
     * #fillGradient, which have been stable across Minecraft versions
     * for a very long time.
     */
    private void renderAnimatedBackground(DrawContext context) {
        context.fillGradient(0, 0, this.width, this.height, 0xFF1A0B2E, 0xFF05030A);

        long now = System.currentTimeMillis();
        int dotCount = 40;
        for (int i = 0; i < dotCount; i++) {
            float seed = i * 12.9898f;
            float baseX = (float) ((Math.sin(seed) * 0.5 + 0.5) * this.width);
            float speed = 10f + (i % 5) * 4f;
            float loopHeight = this.height + 60f;
            float y = loopHeight - ((now / 1000f * speed + i * 53f) % loopHeight);
            float drift = (float) Math.sin(now / 1000.0 + i) * 8f;
            int x = (int) (baseX + drift);
            int size = 1 + (i % 3);
            int alpha = 0x30 + (i % 4) * 0x18;
            int color = (alpha << 24) | 0xC9A6FF;
            context.fill(x, (int) y, x + size, (int) y + size, color);
        }
    }

    /**
     * Draws the local player's model rotating slowly inside the given box.
     *
     * MAPPING NOTE: `InventoryScreen.drawEntity(...)` is the same static
     * helper vanilla itself uses to render the player inside the
     * survival-inventory background. If its parameter list does not
     * match your exact 1.21.11 mappings, open InventoryScreen in your
     * IDE, copy the up-to-date signature, and adjust this single call.
     */
    private void renderPreview(DrawContext context, int x0, int y0, int x1, int y1, int scale) {
        if (this.client == null || this.client.player == null) {
            return;
        }
        InventoryScreen.drawEntity(
                context,
                x0, y0, x1, y1,
                scale,
                0.0f,
                x0 + (x1 - x0) / 2f,
                y0 + (y1 - y0) / 2f,
                this.client.player
        );
    }
}
