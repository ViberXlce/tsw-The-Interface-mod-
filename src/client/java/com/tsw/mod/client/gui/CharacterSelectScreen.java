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
 * character slots side by side; each has a live rotating preview of the
 * player model wearing that slot's skin, a text field to type/edit a skin
 * URL, and a Select/Create button.
 *
 * This screen intentionally cannot be closed with Escape: a character
 * must be chosen before play continues, matching the requested behaviour.
 */
public class CharacterSelectScreen extends Screen {

    private static final int SLOT_COUNT = 3;
    private static final int PANEL_WIDTH = 150;
    private static final int PANEL_HEIGHT = 200;
    private static final int PANEL_GAP = 20;

    private final List<TSWNetworking.SlotInfo> initialSlots;
    private final TextFieldWidget[] urlFields = new TextFieldWidget[SLOT_COUNT];
    private final String[] previewUrls = new String[SLOT_COUNT];

    /** Slot the local player was previewing before this screen opened, so we can restore it if they back out. */
    private String previousOwnSkinUrl;

    public CharacterSelectScreen(List<TSWNetworking.SlotInfo> initialSlots) {
        super(Text.translatable("tsw.screen.title"));
        this.initialSlots = initialSlots;
    }

    @Override
    protected void init() {
        if (this.client != null && this.client.player != null) {
            previousOwnSkinUrl = SkinCache.PLAYER_SKIN_URLS.get(this.client.player.getUuid());
        }

        int totalWidth = SLOT_COUNT * PANEL_WIDTH + (SLOT_COUNT - 1) * PANEL_GAP;
        int startX = (this.width - totalWidth) / 2;
        int panelY = (this.height - PANEL_HEIGHT) / 2;

        for (int i = 0; i < SLOT_COUNT; i++) {
            int slot = i;
            int panelX = startX + i * (PANEL_WIDTH + PANEL_GAP);

            TSWNetworking.SlotInfo info = slot < initialSlots.size() ? initialSlots.get(slot) : null;
            String existingUrl = info != null ? info.skinUrl() : "";
            boolean occupied = info != null && info.occupied();
            previewUrls[slot] = existingUrl;

            TextFieldWidget urlField = new TextFieldWidget(this.textRenderer,
                    panelX + 10, panelY + PANEL_HEIGHT - 60, PANEL_WIDTH - 20, 20,
                    Text.translatable("tsw.slot.skinUrl"));
            urlField.setMaxLength(512);
            urlField.setText(existingUrl == null ? "" : existingUrl);
            urlField.setChangedListener(text -> onUrlTyped(slot, text));
            urlFields[slot] = urlField;
            this.addDrawableChild(urlField);

            this.addDrawableChild(ButtonWidget.builder(Text.translatable("tsw.slot.save"), b -> saveSkin(slot))
                    .dimensions(panelX + 10, panelY + PANEL_HEIGHT - 34, PANEL_WIDTH - 20, 16)
                    .build());

            Text mainLabel = occupied ? Text.translatable("tsw.slot.select") : Text.translatable("tsw.slot.create");
            this.addDrawableChild(ButtonWidget.builder(mainLabel, b -> selectSlot(slot))
                    .dimensions(panelX + 10, panelY + PANEL_HEIGHT - 12, PANEL_WIDTH - 20, 20)
                    .build());
        }
    }

    private void onUrlTyped(int slot, String text) {
        previewUrls[slot] = text;
        // Live-preview on the local player entity only while this screen is open.
        if (this.client != null && this.client.player != null) {
            SkinCache.PLAYER_SKIN_URLS.put(this.client.player.getUuid(), text);
            SkinCache.getOrRequest(text);
        }
    }

    private void saveSkin(int slot) {
        String url = urlFields[slot].getText();
        ClientPlayNetworking.send(new TSWNetworking.UpdateSkinUrlC2S(slot, url));
    }

    private void selectSlot(int slot) {
        // Make sure whatever URL is currently typed is saved before switching in.
        saveSkin(slot);
        ClientPlayNetworking.send(new TSWNetworking.SelectCharacterC2S(slot));
        this.close();
    }

    @Override
    public void close() {
        // Real skin state will arrive shortly via the server's SkinBroadcastS2C
        // once selection is processed; nothing else to restore here.
        super.close();
    }

    @Override
    public boolean shouldCloseOnEsc() {
        // A character must be chosen before returning to normal play.
        return false;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);

        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 20, 0xFFFFFF);

        int totalWidth = SLOT_COUNT * PANEL_WIDTH + (SLOT_COUNT - 1) * PANEL_GAP;
        int startX = (this.width - totalWidth) / 2;
        int panelY = (this.height - PANEL_HEIGHT) / 2;

        for (int i = 0; i < SLOT_COUNT; i++) {
            int panelX = startX + i * (PANEL_WIDTH + PANEL_GAP);
            context.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + PANEL_HEIGHT, 0x88000000);
            context.drawCenteredTextWithShadow(this.textRenderer,
                    Text.translatable("tsw.slot.slotNumber", i + 1),
                    panelX + PANEL_WIDTH / 2, panelY + 6, 0xFFFFFF);

            renderRotatingPreview(context, panelX, panelY, mouseX, mouseY);
        }

        super.render(context, mouseX, mouseY, delta);
    }

    /**
     * Draws the local player's model (which, thanks to the live preview
     * hook in onUrlTyped, is currently wearing whatever skin URL is typed
     * for this slot) rotating slowly inside the panel.
     *
     * MAPPING NOTE: `InventoryScreen.drawEntity(...)` is the same static
     * helper vanilla itself uses to render the player inside the
     * survival-inventory background. Its exact parameter list has shifted
     * slightly between minor MC versions (extra float for scale, or a
     * consumer for rotation) - if this line does not compile against your
     * 1.21.11 mappings, open InventoryScreen in your IDE, copy the
     * up-to-date signature, and adjust this single call; nothing else in
     * the screen needs to change.
     */
    private void renderRotatingPreview(DrawContext context, int panelX, int panelY, int mouseX, int mouseY) {
        if (this.client == null || this.client.player == null) {
            return;
        }
        int previewX0 = panelX + 20;
        int previewY0 = panelY + 30;
        int previewX1 = panelX + PANEL_WIDTH - 20;
        int previewY1 = panelY + PANEL_HEIGHT - 70;

        float rotationYaw = (System.currentTimeMillis() % 4000L) / 4000f * 360f;

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
