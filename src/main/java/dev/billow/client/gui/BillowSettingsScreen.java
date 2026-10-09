package dev.billow.client.gui;

import dev.billow.client.BillowClient;
import dev.billow.client.Texts;
import dev.billow.config.BillowConfig;
import dev.billow.core.Choice;
import dev.billow.core.CloudStyle;
import net.minecraft.client.Options;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;
import java.util.function.Supplier;

public final class BillowSettingsScreen extends BillowOptionsScreen {
    private static final int WIDTH = 150;
    private static final int HEIGHT = 20;

    public BillowSettingsScreen(Screen lastScreen, Options options) {
        super(lastScreen, options, Texts.translatable("billow.options.title"));
    }

    @Override
    protected void addOptions() {
        BillowConfig config = BillowClient.config();

        addRow(
                cycle("billow.options.style", CloudStyle.values(), config::style, config::setStyle),
                slider("billow.options.height", BillowConfig.MIN_HEIGHT - 4, BillowConfig.MAX_HEIGHT, 4,
                        config.height() == 0 ? BillowConfig.MIN_HEIGHT - 4 : config.height(),
                        value -> value < BillowConfig.MIN_HEIGHT
                                ? Texts.translatable("billow.options.height.default").getString()
                                : Integer.toString((int) value),
                        value -> config.setHeight((int) value)));

        addHeader(Texts.translatable("billow.options.section.look"));
        addRow(
                slider("billow.options.thickness", BillowConfig.MIN_THICKNESS, BillowConfig.MAX_THICKNESS, 1,
                        config.thickness(), value -> Texts.translatable("billow.options.blocks", (int) value).getString(),
                        value -> config.setThickness((int) value)),
                slider("billow.options.roundness", 0, 100, 5, config.roundness(), BillowSettingsScreen::percent,
                        value -> config.setRoundness((int) value)));
        addRow(
                slider("billow.options.coverage", 0, 100, 5, config.coverage(), BillowSettingsScreen::percent,
                        value -> config.setCoverage((int) value)),
                toggle("billow.options.shapes_with_clouds", config::shapesWithClouds, config::setShapesWithClouds));
        addRow(
                slider("billow.options.speed", 0, BillowConfig.MAX_SPEED, 10, config.speed(),
                        BillowSettingsScreen::percent, value -> config.setSpeed((int) value)),
                slider("billow.options.opacity", 10, 100, 5, config.opacity(), BillowSettingsScreen::percent,
                        value -> config.setOpacity((int) value)));
        addRow(resetButton(config), null);
    }

    private static String percent(double value) {
        return (int) value + "%";
    }

    @Override
    public void removed() {
        super.removed();
        BillowClient.saveConfig();
    }

    private AbstractWidget resetButton(BillowConfig config) {
        return tooltip(button(Texts.translatable("billow.options.reset"), WIDTH, button -> {
            config.resetToDefaults();
            ScreenOpener.open(minecraft, new BillowSettingsScreen(lastScreen, options));
        }), Texts.translatable("billow.options.reset.tooltip"));
    }

    private AbstractWidget toggle(String key, BooleanSupplier getter, Consumer<Boolean> setter) {
        return tooltip(button(toggleLabel(key, getter.getAsBoolean()), WIDTH, button -> {
            setter.accept(!getter.getAsBoolean());
            button.setMessage(toggleLabel(key, getter.getAsBoolean()));
        }), Texts.translatable(key + ".tooltip"));
    }

    private static Component toggleLabel(String key, boolean on) {
        Component state = Texts.translatable(on ? "options.on" : "options.off");
        return Texts.translatable("options.generic_value", Texts.translatable(key), state);
    }

    private <T extends Enum<T> & Choice> AbstractWidget cycle(
            String key, T[] values, Supplier<T> getter, Consumer<T> setter) {
        return tooltip(button(cycleLabel(key, getter.get()), WIDTH, button -> {
            T next = values[(getter.get().ordinal() + 1) % values.length];
            setter.accept(next);
            button.setMessage(cycleLabel(key, getter.get()));
        }), Texts.translatable(key + ".tooltip"));
    }

    private static Component cycleLabel(String key, Choice value) {
        return Texts.translatable("options.generic_value", Texts.translatable(key),
                Texts.translatable(key + "." + value.id()));
    }

    private AbstractWidget slider(String key, double min, double max, double step, double initial,
                                  DoubleFunction<String> format, DoubleConsumer onChange) {
        return tooltip(new StepSlider(key, min, max, step, initial, format, onChange),
                Texts.translatable(key + ".tooltip"));
    }

    private static final class StepSlider extends AbstractSliderButton {
        private final String captionKey;
        private final double min;
        private final double max;
        private final double step;
        private final DoubleFunction<String> format;
        private final DoubleConsumer onChange;

        StepSlider(String captionKey, double min, double max, double step, double initial,
                   DoubleFunction<String> format, DoubleConsumer onChange) {
            super(0, 0, WIDTH, HEIGHT, Texts.empty(), 0.0);
            this.captionKey = captionKey;
            this.min = min;
            this.max = max;
            this.step = step;
            this.format = format;
            this.onChange = onChange;
            this.value = (snap(initial) - min) / (max - min);
            updateMessage();
        }

        private double snap(double raw) {
            double clamped = Math.max(min, Math.min(max, raw));
            double snapped = min + Math.round((clamped - min) / step) * step;
            return Math.max(min, Math.min(max, snapped));
        }

        private double current() {
            return snap(min + value * (max - min));
        }

        @Override
        protected void updateMessage() {
            Component shown = Texts.literal(format.apply(current()));
            setMessage(Texts.translatable("options.generic_value", Texts.translatable(captionKey), shown));
        }

        @Override
        protected void applyValue() {
            double snapped = current();
            value = (snapped - min) / (max - min);
            onChange.accept(snapped);
        }
    }
}
