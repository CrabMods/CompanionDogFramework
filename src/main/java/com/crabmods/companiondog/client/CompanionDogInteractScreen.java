package com.crabmods.companiondog.client;

import com.crabmods.companiondog.entity.CompanionDogCommand;
import com.crabmods.companiondog.entity.CompanionDogEntity;
import com.crabmods.companiondog.network.CompanionDogCommandPayload;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

public final class CompanionDogInteractScreen extends Screen {
    private final CompanionDogEntity dog;
    private final Map<CompanionDogCommand, Button> commands = new EnumMap<>(CompanionDogCommand.class);
    private int left, top, panelWidth, column;

    public CompanionDogInteractScreen(CompanionDogEntity dog) {
        super(dog.getDisplayName());
        this.dog = dog;
    }

    @Override
    protected void init() {
        commands.clear();
        panelWidth = Math.min(364, width - 16);
        column = Math.min(104, (panelWidth - 96) / 2);
        left = (width - panelWidth) / 2;
        top = Math.max(6, (height - 236) / 2);
        int rowHeight = Math.min(22, (height - top - 48) / 9);
        for (CompanionDogCommand command : CompanionDogCommand.values()) {
            boolean management = command == CompanionDogCommand.ARCHIVE_UNOWNED || command == CompanionDogCommand.ADOPT_UNOWNED;
            boolean unowned = dog.getOwnerUUID() == null;
            if (management != unowned) continue;
            int index = command.ordinal();
            if (unowned) index = command == CompanionDogCommand.ADOPT_UNOWNED ? 0 : 1;
            boolean leftColumn = index < 6 || index >= 15;
            int x = leftColumn ? left : left + panelWidth - column;
            int row = index < 6 ? index : index >= 15 ? index - 9 : index - 6;
            Button button = Button.builder(Component.translatable(command.translationKey()), b -> choose(command))
                    .bounds(x, top + 30 + row * rowHeight, column, rowHeight - 2).build();
            commands.put(command, addRenderableWidget(button));
        }
        addRenderableWidget(Button.builder(Component.literal("x"), b -> onClose())
                .bounds(left + panelWidth - 16, top, 16, 16).build());
    }

    private void choose(CompanionDogCommand command) {
        if (command == CompanionDogCommand.ARCHIVE_UNOWNED) {
            minecraft.setScreen(new ConfirmScreen(confirmed -> {
                if (confirmed) send(command);
                minecraft.setScreen(confirmed ? null : this);
            }, Component.translatable("screen.companiondog.archive_title"),
                    Component.translatable("screen.companiondog.archive_confirm")));
        } else send(command);
    }

    private void send(CompanionDogCommand command) {
        PacketDistributor.sendToServer(new CompanionDogCommandPayload(dog.getId(), command.ordinal()));
    }

    /** Uses the same Button.onPress path as a mouse click in deterministic client tests. */
    void pressCommand(CompanionDogCommand command) {
        commands.get(command).onPress();
    }

    @Override
    public void tick() {
        if (minecraft.player == null || dog.isRemoved() || dog.isReturningHome()
                || dog.distanceToSqr(minecraft.player) > 256) {
            onClose();
            return;
        }
        if (commands.containsKey(CompanionDogCommand.ARCHIVE_UNOWNED) && !dog.canManageUnowned(minecraft.player)) {
            onClose();
            return;
        }
        Button wander = commands.get(CompanionDogCommand.TOGGLE_WANDER);
        if (wander != null) wander.setMessage(Component.translatable(dog.isWandering()
                ? "command.companiondog.follow" : "command.companiondog.toggle_wander"));
        commands.forEach((command, button) -> button.active = command == CompanionDogCommand.END_REST
                ? dog.isRecovering() : !dog.isRecovering()
                || command == CompanionDogCommand.SET_HOME || command == CompanionDogCommand.GO_HOME);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partial) {
        // Screen.render blurs the world; draw the preview and title afterwards.
        super.render(graphics, mouseX, mouseY, partial);
        graphics.fill(left, top + 23, left + panelWidth, top + 24, 0xFF89978C);
        graphics.drawCenteredString(font, title, width / 2, top + 6, 0xFFF5F2E9);
        if (dog.isRecovering()) graphics.drawCenteredString(font,
                Component.translatable("screen.companiondog.resting", dog.restSeconds()),
                width / 2, Math.min(height - 10, top + 220), 0xFFD1E6CC);
        int x1 = left + column + 3;
        int x2 = left + panelWidth - column - 3;
        InventoryScreen.renderEntityInInventoryFollowsMouse(graphics, x1, top + 36, x2,
                Math.min(height - 12, top + 200), 80, 0.15F, mouseX, mouseY, dog);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
