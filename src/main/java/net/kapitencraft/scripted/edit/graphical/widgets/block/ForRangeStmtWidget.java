package net.kapitencraft.scripted.edit.graphical.widgets.block;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.kapitencraft.scripted.edit.TextRenderHelper;
import net.kapitencraft.scripted.edit.graphical.CodeWidgetSprites;
import net.kapitencraft.scripted.edit.graphical.MethodContext;
import net.kapitencraft.scripted.edit.graphical.connector.ArgumentExprConnector;
import net.kapitencraft.scripted.edit.graphical.connector.CommonBranchBlockConnector;
import net.kapitencraft.scripted.edit.graphical.connector.Connector;
import net.kapitencraft.scripted.edit.graphical.fetch.BlockWidgetFetchResult;
import net.kapitencraft.scripted.edit.graphical.fetch.WidgetFetchResult;
import net.kapitencraft.scripted.edit.graphical.widgets.ArgumentStorage;
import net.kapitencraft.scripted.edit.graphical.widgets.CodeWidget;
import net.kapitencraft.scripted.edit.graphical.widgets.expr.ExprCodeWidget;
import net.kapitencraft.scripted.edit.graphical.widgets.expr.ParamWidget;
import net.kapitencraft.scripted.edit.graphical.widgets.expr.VarNameSelectorWidget;
import net.kapitencraft.scripted.edit.graphical.widgets.interaction.CodeInteraction;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

public class ForRangeStmtWidget extends BlockCodeWidget {
    public static final MapCodec<ForRangeStmtWidget> CODEC = RecordCodecBuilder.mapCodec(i -> commonFields(i)
            .and(ExprCodeWidget.CODEC.optionalFieldOf("min", ParamWidget.NUM).forGetter(ForRangeStmtWidget::getMin))
            .and(ExprCodeWidget.CODEC.optionalFieldOf("max", ParamWidget.NUM).forGetter(ForRangeStmtWidget::getMax))
            .and(ExprCodeWidget.CODEC.optionalFieldOf("step", ParamWidget.NUM).forGetter(ForRangeStmtWidget::getStep))
            .and(BlockCodeWidget.CODEC.optionalFieldOf("body").forGetter(w -> Optional.ofNullable(w.body)))
            .apply(i, ForRangeStmtWidget::new)
    );

    private final VarNameSelectorWidget varName = new VarNameSelectorWidget();
    private final Map<String, ExprCodeWidget> args;
    private BlockCodeWidget body;

    private ForRangeStmtWidget(Optional<BlockCodeWidget> child, ExprCodeWidget min, ExprCodeWidget max, ExprCodeWidget step, Optional<BlockCodeWidget> body) {
        this(min, max, step, body.orElse(null));
        child.ifPresent(this::setChild);
    }

    private ForRangeStmtWidget(ExprCodeWidget lowerBound, ExprCodeWidget upperBound, ExprCodeWidget step, BlockCodeWidget body) {
        this.args = new HashMap<>();
        args.put("var", varName);
        args.put("min", lowerBound);
        args.put("max", upperBound);
        args.put("interval", step);
        this.varName.setCreate(true);
        this.body = body;
    }

    @Override
    protected @NotNull Type getType() {
        return Type.FOR_RANGE_STMT;
    }

    @Override
    public int getWidth(Font font) {
        int headWidth = getHeadWidth(font);
        if (body != null && body.getWidth(font) > headWidth)
            return body.getWidth(font);
        return headWidth;
    }

    @Override
    public int getHeight() {
        return getHeadHeight() + getBodyHeight() + 13;
    }

    private int getHeadHeight() {
        return Math.max(18, ExprCodeWidget.getHeightFromArgs(this.args) + 4) + 2;
    }

    private int getHeadWidth(Font font) {
        return 4 + TextRenderHelper.getVisualTextWidth(font, "§for", this.args);
    }

    private int getBodyHeight() {
        return body != null ? body.getHeightWithChildren() : 10;
    }

    @Override
    public BlockCodeWidget copy() {
        return new ForRangeStmtWidget(
                this.args.get("min").copy(),
                this.args.get("max").copy(),
                this.args.get("interval").copy(),
                this.body == null ? null : this.body.copy()
        );
    }

    @Override
    public void insertByName(@NotNull String arg, @NotNull ExprCodeWidget obj) {
        if ("var".equals(arg) || !args.containsKey(arg))
            throw new IllegalStateException("unknown argument \"" + arg + "\" on ForRange widget");
        this.args.put(arg, obj);
    }

    @Override
    public CodeWidget getByName(String arg) {
        if ("var".equals(arg) || !args.containsKey(arg))
            throw new IllegalStateException("unknown argument \"" + arg + "\" on ForRange widget");
        return args.get(arg);
    }

    public ExprCodeWidget getMin() {
        return args.get("min");
    }

    public ExprCodeWidget getMax() {
        return args.get("max");
    }

    public ExprCodeWidget getStep() {
        return args.get("interval");
    }

    @Override
    public void collectConnectors(int aX, int aY, Font font, Consumer<Connector> collector) {
        ArgumentExprConnector.parse(font, aX + 4, aY, "§for", this.args, this, collector);

        int headHeight = this.getHeadHeight();
        collector.accept(new CommonBranchBlockConnector(
                aX + 6,
                aY + headHeight,
                this::setBody,
                () -> this.body,
                font,
                collector
        ));

        super.collectConnectors(aX, aY, font, collector);
    }

    @Override
    public WidgetFetchResult fetchAndRemoveHovered(int x, int y, Font font) {
        if (y < this.getHeadHeight()) {
            if (x < this.getHeadWidth(font))
                return BlockWidgetFetchResult.fromExprList(4, x, y, font, this, "§for", ArgumentStorage.create(this.args));
            return null;
        }

        if (y - this.getHeadHeight() < getBodyHeight()) {
            if (x < 6)
                return BlockWidgetFetchResult.notRemoved(this, x, y);
            if (this.body != null) {
                WidgetFetchResult result = this.body.fetchAndRemoveHovered(x - 6, y - this.getHeadHeight(), font);
                if (result == null) return null;
                if (!result.removed())
                    this.body = null;
                return result.setRemoved();
            }
            return null;
        }

        return super.fetchAndRemoveHovered(x, y - getHeight(), font);
    }

    private void setBody(BlockCodeWidget widget) {
        this.body = widget;
    }

    @Override
    public void render(GuiGraphics graphics, Font font, int renderX, int renderY) {
        int headWidth = getHeadWidth(font);
        int headHeight = getHeadHeight();

        graphics.blitSprite(CodeWidgetSprites.SCOPE_HEAD, renderX, renderY, headWidth, headHeight + 3);
        TextRenderHelper.renderVisualText(graphics, font,
                renderX,
                renderY + 7 + (headHeight - 20) / 2,
                "§for", args
        );

        int bodyHeight = getBodyHeight();
        if (this.body != null)
            this.body.render(graphics, font, renderX + 6, renderY + headHeight);
        graphics.blitSprite(CodeWidgetSprites.SCOPE_ENCLOSURE, renderX, renderY + headHeight + 3, 6, bodyHeight - 3);
        graphics.blitSprite(CodeWidgetSprites.SCOPE_END, renderX, renderY + headHeight + bodyHeight, headWidth, 16);

        super.render(graphics, font, renderX, renderY);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public void registerInteractions(int xOrigin, int yOrigin, Font font, Consumer<CodeInteraction> sink) {
        TextRenderHelper.registerAllInteractions(xOrigin + 4, yOrigin + 7 + (getHeadHeight() - 20) / 2, font, sink, "§for", args);
        if (this.body != null)
            this.body.registerInteractions(xOrigin + 6, yOrigin + this.getHeadHeight(), font, sink);

        super.registerInteractions(xOrigin, yOrigin, font, sink);
    }

    @Override
    public void update(@Nullable MethodContext context, Font font) {
        this.args.values().forEach(w -> w.update(context, font));
        if (this.body != null)
            this.body.update(context, font);
        super.update(context, font);
    }

    public static class Builder implements BlockCodeWidget.Builder<ForRangeStmtWidget> {
        private ExprCodeWidget min = ParamWidget.NUM;
        private ExprCodeWidget max = ParamWidget.NUM;
        private ExprCodeWidget step = ParamWidget.NUM;
        private BlockCodeWidget body;

        public Builder setMin(ExprCodeWidget widget) {
            this.min = widget;
            return this;
        }

        public Builder setMax(ExprCodeWidget max) {
            this.max = max;
            return this;
        }

        public Builder setStep(ExprCodeWidget step) {
            this.step = step;
            return this;
        }

        @Override
        public ForRangeStmtWidget build() {
            return new ForRangeStmtWidget(min, max, step, body);
        }
    }
}
