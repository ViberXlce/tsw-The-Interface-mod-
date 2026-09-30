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
            renderPreview(context, centerX + 20, centerY + 16, centerX + CENTER_WIDTH - 20, centerY + CENTER_HEIGHT - 128, 45);

            context.drawCenteredTextWithShadow(this.textRenderer,
                    Text.translatable("tsw.slot.slotNumber", focusedSlot + 1).formatted(net.minecraft.util.Formatting.GOLD),
                    centerX + CENTER_WIDTH / 2, centerY + CENTER_HEIGHT - 122, 0xFFAA55);

            String playerName = this.client != null && this.client.player != null
                    ? this.client.player.getName().getString()
                    : "";
            context.drawCenteredTextWithShadow(this.textRenderer,
                    Text.literal(playerName.toUpperCase()),
                    centerX + CENTER_WIDTH / 2, centerY + CENTER_HEIGHT - 110, 0xFFFFFF);
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

        if (occupied[focusedSlot]) {
            // Orange glow behind the Select/Play button (drawn before the
            // button itself so it peeks out as a border).
            int bx = centerX + 20;
            int by = centerY + CENTER_HEIGHT - 30;
            int bw = CENTER_WIDTH - 40;
            int bh = 24;
            context.fill(bx - 3, by - 3, bx + bw + 3, by + bh + 3, 0xFFFF8C1A);
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
        // Base gradient.
        context.fillGradient(0, 0, this.width, this.height, 0xFF160A28, 0xFF04020A);

        long now = System.currentTimeMillis();

        // Slowly drifting "aurora" bands: a few wide, very transparent
        // horizontal strips whose horizontal offset and tint shift over
        // time, layered on top of the base gradient.
        int bandCount = 4;
        for (int b = 0; b < bandCount; b++) {
            double t = now / 1000.0;
            float bandY = this.height * (0.15f + b * 0.2f);
            float bandHeight = this.height * 0.22f;
            float xOffset = (float) Math.sin(t * 0.3 + b * 1.7) * this.width * 0.15f;
            int[] hues = {0xFF6A3CB5, 0xFF3C6AB5, 0xFFB53C8E, 0xFF3CB5A6};
            int bandColor = (0x1C << 24) | (hues[b % hues.length] & 0xFFFFFF);
            int bandX0 = (int) (-this.width * 0.3f + xOffset);
            int bandX1 = (int) (this.width * 1.3f + xOffset);
            context.fillGradient(bandX0, (int) bandY, bandX1, (int) (bandY + bandHeight), bandColor, 0x00000000);
        }

        // Soft glow centered behind the focused character: several
        // overlapping low-alpha rectangles, each a bit smaller than the
        // last, to fake a radial spotlight without needing a real
        // circular/gradient texture.
        int centerX = this.width / 2;
        int glowCenterY = (int) (this.height * 0.42);
        int rings = 6;
        for (int r = rings; r >= 1; r--) {
            int w = r * 36;
            int h = r * 44;
            int alpha = 0x08 + (rings - r) * 0x03;
            int color = (alpha << 24) | 0xC9A6FF;
            context.fill(centerX - w, glowCenterY - h, centerX + w, glowCenterY + h, color);
        }

        // Twinkling starfield: small dots that both drift upward and
        // pulse in brightness.
        int dotCount = 50;
        for (int i = 0; i < dotCount; i++) {
            float seed = i * 12.9898f;
            float baseX = (float) ((Math.sin(seed) * 0.5 + 0.5) * this.width);
            float speed = 6f + (i % 5) * 3f;
            float loopHeight = this.height + 60f;
            float y = loopHeight - ((now / 1000f * speed + i * 53f) % loopHeight);
            float drift = (float) Math.sin(now / 1200.0 + i) * 6f;
            int x = (int) (baseX + drift);
            int size = 1 + (i % 3);
            float twinkle = (float) (0.5 + 0.5 * Math.sin(now / 400.0 + i * 2.1));
            int alpha = (int) (0x20 + twinkle * 0x60);
            int color = (alpha << 24) | 0xE6D9FF;
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
