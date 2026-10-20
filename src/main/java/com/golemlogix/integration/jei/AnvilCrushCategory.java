package com.golemlogix.integration.jei;

import com.golemlogix.GolemLogixMod;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.types.IRecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * The anvil ritual as a little animated scene (like Flux Networks' in-world crafting): the anvil hangs above the golem, falls along the
 * arrow, the battered golem flinches and flattens with a burst of sparks, and its plate lights up below. To the left of the golem an
 * arrow points at how battered it must be (hearts and HP); the plate stands under an "=" below the golem.
 */
public class AnvilCrushCategory implements IRecipeCategory<AnvilCrushRecipe> {
    public static final IRecipeType<AnvilCrushRecipe> TYPE =
            IRecipeType.create(GolemLogixMod.MOD_ID, "anvil_crushing", AnvilCrushRecipe.class);

    private static final int W = 150, H = 120;
    /** The golem's column: its centre, where the anvil hangs, where the golem's feet stand, its height on screen. */
    private static final int GX = 108, ANVIL_Y = 12, FEET_Y = 82, GOLEM_H = 34;
    private static final int OUT_X = GX - 9, OUT_Y = 100;
    /** One loop: hanging, falling, the blow, the plate shown. In ticks. */
    private static final float LOOP = 72f, FALL_START = 18f, IMPACT = 30f, FLAT = 36f;
    private static final int BROWN = 0xFF8A5A1E, ARROW = 0xFF6E5A44;
    private static final Identifier HEART_FULL = Identifier.withDefaultNamespace("hud/heart/full");
    private static final Identifier HEART_HALF = Identifier.withDefaultNamespace("hud/heart/half");
    private static final Identifier HEART_EMPTY = Identifier.withDefaultNamespace("hud/heart/container");

    private final IDrawable icon;
    private final IDrawable slot;
    private final Component title;
    /** Display copies of the golems, per level (one of each kind). */
    private static final Map<Level, Map<EntityType<?>, Entity>> MODELS = new WeakHashMap<>();

    public AnvilCrushCategory(IGuiHelper guiHelper) {
        this.icon = guiHelper.createDrawableItemLike(Blocks.ANVIL);
        this.slot = guiHelper.getSlotDrawable();
        this.title = Component.translatable("jei.golem_logix.category.anvil_crushing");
    }

    @Override
    public IRecipeType<AnvilCrushRecipe> getRecipeType() {
        return TYPE;
    }

    @Override
    public Component getTitle() {
        return title;
    }

    @Override
    public int getWidth() {
        return W;
    }

    @Override
    public int getHeight() {
        return H;
    }

    @Override
    public IDrawable getIcon() {
        return icon;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, AnvilCrushRecipe recipe, IFocusGroup focuses) {
        // the anvil and the golem are drawn as a scene; JEI still finds the ritual by them
        builder.addInvisibleIngredients(RecipeIngredientRole.CRAFTING_STATION).addItemStack(new ItemStack(Blocks.ANVIL));
        builder.addInvisibleIngredients(RecipeIngredientRole.INPUT).addItemStack(recipe.golemRepresentation());
        builder.addSlot(RecipeIngredientRole.OUTPUT, OUT_X + 1, OUT_Y + 1).addItemStack(recipe.output());
    }

    @Override
    public void draw(AnvilCrushRecipe recipe, IRecipeSlotsView slotsView, GuiGraphicsExtractor g, double mouseX, double mouseY) {
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        float t = (float) ((System.currentTimeMillis() / 50.0) % LOOP);

        g.text(font, recipe.golemName(), 4, 3, BROWN, false);

        // the golem: whole until the blow, then flattening, then gone while the plate is shown
        LivingEntity golem = model(mc.level, recipe.entity());
        float h = GOLEM_H;
        float squash = t < IMPACT ? 1f : t < FLAT ? 1f - 0.75f * (t - IMPACT) / (FLAT - IMPACT) : 0f;
        float top = FEET_Y - h * squash;
        if (golem != null && squash > 0f) {
            drawGolem(g, golem, squash, t >= IMPACT ? Math.max(0, 10 - (int) ((t - IMPACT) * 1.5f)) : 0);
        } else if (golem == null && squash > 0f) {
            g.item(recipe.golemRepresentation(), GX - 8, FEET_Y - 16);
        }

        // the anvil: hangs, falls faster and faster, then sinks with the golem and rests where it stood
        float fallTo = FEET_Y - h - 14;
        float anvilY;
        if (t < FALL_START) {
            anvilY = ANVIL_Y + Mth.sin(t * 0.35f) * 0.6f;
        } else if (t < IMPACT) {
            float p = (t - FALL_START) / (IMPACT - FALL_START);
            anvilY = ANVIL_Y + (fallTo - ANVIL_Y) * p * p;
        } else {
            anvilY = Math.min(top, FEET_Y) - 14;
        }
        // the arrow down to the golem, under the hanging anvil
        if (t < FALL_START) {
            arrowDown(g, GX, ANVIL_Y + 19, (int) fallTo + 10);
        }
        g.pose().pushMatrix();
        g.pose().translate(0, anvilY);
        g.item(new ItemStack(Blocks.ANVIL), GX - 8, 0);
        g.pose().popMatrix();

        // the blow: sparks and frost flying off
        if (t >= IMPACT && t < IMPACT + 10) {
            float k = (t - IMPACT) / 10f;
            int alpha = (int) (255 * (1 - k));
            for (int i = 0; i < 10; i++) {
                double a = i * Math.PI * 2 / 10 + 0.3;
                int sx = GX + (int) (Math.cos(a) * (6 + 16 * k));
                int sy = (int) top + (int) (Math.sin(a) * (3 + 9 * k)) - 2;
                g.fill(sx, sy, sx + 2, sy + 2, alpha << 24 | (i % 2 == 0 ? 0xFFFFFF : 0x7CE8F0));
            }
        }

        // how battered: an arrow from the golem to the hearts (the Wither: a time instead)
        int midY = FEET_Y - GOLEM_H / 2;
        arrowLeft(g, GX - 22, GX - 36, midY);
        if (Float.isNaN(recipe.minHealth())) {
            g.textWithWordWrap(font, recipe.healthCondition(), 4, midY - 8, GX - 42, BROWN);
        } else {
            int x = 4;
            x = hearts(g, x, midY - 5, Mth.ceil(recipe.minHealth()));
            g.text(font, "–", x + 1, midY - 4, BROWN, false);
            hearts(g, x + 8, midY - 5, Mth.floor(recipe.maxHealth()));
            g.text(font, recipe.healthCondition(), 4, midY + 7, 0xFFB0581A, false);
        }

        // "=" under the golem, then the plate, which lights up once the golem is flattened
        g.fill(GX - 5, OUT_Y - 12, GX + 5, OUT_Y - 11, BROWN);
        g.fill(GX - 5, OUT_Y - 9, GX + 5, OUT_Y - 8, BROWN);
        if (t >= FLAT) {
            float pulse = 0.5f + 0.5f * Mth.sin((t - FLAT) * 0.25f);
            int a = (int) (90 + 90 * pulse);
            g.fill(OUT_X - 2, OUT_Y - 2, OUT_X + 20, OUT_Y + 20, a << 24 | 0x5CE6F0);
        }
        slot.draw(g, OUT_X, OUT_Y);
    }

    /**
     * The golem's model standing on FEET_Y, a little turned; pressed down and red with hurt as the anvil lands. Since 1.21.6 an entity
     * in a GUI is drawn into its own picture at screen coordinates (the GUI pose does not apply), so the picture is placed by hand,
     * and the squash is a shrink towards the feet.
     */
    private static void drawGolem(GuiGraphicsExtractor g, LivingEntity golem, float squash, int hurt) {
        // by the hitbox, but no more than 22 px a block: some golems (the copper one's antenna) stand taller than their hitbox
        float scale = Math.min(22f, GOLEM_H / Math.max(0.5f, golem.getBbHeight())) * (0.55f + 0.45f * squash);
        golem.yBodyRot = 200f;
        golem.yBodyRotO = 200f;
        golem.setYRot(200f);
        golem.yHeadRot = 200f;
        golem.yHeadRotO = 200f;
        golem.setXRot(0f);
        golem.hurtTime = hurt;
        var renderer = net.minecraft.client.Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(golem);
        var state = renderer.createRenderState(golem, 1f);
        state.shadowPieces.clear();
        state.outlineColor = 0;
        float h = golem.getBbHeight() * scale;
        // the picture: centred on the golem, tall enough for antennas and heads above the hitbox
        org.joml.Vector2f c = g.pose().transformPosition(GX, FEET_Y - h / 2f, new org.joml.Vector2f());
        int half = (int) Math.ceil(h * 0.8f) + 4;
        g.entity(state, scale, new Vector3f(0f, golem.getBbHeight() / 2f, 0f), new Quaternionf().rotateZ((float) Math.PI), null,
                (int) c.x - half, (int) c.y - half, (int) c.x + half, (int) c.y + half);
        golem.hurtTime = 0;
    }

    /** Hearts for hp health points (a half heart per point), as in the HUD. Returns the x after them. */
    private static int hearts(GuiGraphicsExtractor g, int x, int y, int hp) {
        int n = Math.max(1, (hp + 1) / 2);
        for (int i = 0; i < n; i++) {
            g.blitSprite(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, HEART_EMPTY, x + i * 8, y, 9, 9);
            boolean half = i == n - 1 && hp % 2 == 1;
            g.blitSprite(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, half ? HEART_HALF : HEART_FULL, x + i * 8, y, 9, 9);
        }
        return x + n * 8 + 2;
    }

    private static void arrowDown(GuiGraphicsExtractor g, int x, int y0, int y1) {
        g.fill(x - 1, y0, x + 1, y1 - 3, ARROW);
        for (int i = 0; i < 4; i++) {
            g.fill(x - 4 + i, y1 - 4 + i, x + 4 - i, y1 - 3 + i, ARROW);
        }
    }

    private static void arrowLeft(GuiGraphicsExtractor g, int x0, int x1, int y) {
        g.fill(x1 + 3, y - 1, x0, y + 1, ARROW);
        for (int i = 0; i < 4; i++) {
            g.fill(x1 + i, y - 4 + i, x1 + i + 1, y + 4 - i, ARROW);
        }
    }

    /** A display copy of the golem (made once per kind and level); null if it is no living entity or there is no level. */
    private static LivingEntity model(Level level, EntityType<?> type) {
        if (level == null || type == null) {
            return null;
        }
        Entity e = MODELS.computeIfAbsent(level, l -> new HashMap<>()).computeIfAbsent(type, ty -> ty.create(level, net.minecraft.world.entity.EntitySpawnReason.LOAD));
        return e instanceof LivingEntity living ? living : null;
    }
}
