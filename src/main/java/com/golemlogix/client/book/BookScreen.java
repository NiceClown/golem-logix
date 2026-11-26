package com.golemlogix.client.book;

import com.golemlogix.client.GolemGui;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.golemlogix.client.book.BookLayout.H;
import static com.golemlogix.client.book.BookLayout.W;

/**
 * Guide book in two modes: the constellation map (a dark starry blueprint with nodes and dependency links) and an
 * open article (parchment spread: text, recipes, rituals, illustrations, schematic). Section tabs sit on the left;
 * on the map there is search across all entries, dragging and zoom.
 *
 * Page pagination and layout audit live in {@link BookLayout}; spread rendering in {@link BookPageRenderer};
 * constellation map state and rendering in {@link BookMapView}.
 */
public class BookScreen extends Screen {
    private static final int TAB_STEP = 25;
    private static final int TAB_TOP = 15;
    private static final int[][] CORNERS = {{-6, -6}, {W - 12, -6}, {-6, H - 12}, {W - 12, H - 12}};

    /** Last place opened in the book (section, entry, spread): persists across reopenings and restarts. */
    private static String[] lastState = null;

    private final BookModel book = BookModel.get();
    private final BookMapView mapView = new BookMapView(book);
    private final BookPageRenderer pageRenderer = new BookPageRenderer();
    private final Set<String> visited = new HashSet<>();
    private final List<Integer> visibleTabs = new ArrayList<>();

    private int categoryIndex = 0;
    private BookModel.Entry openEntry = null;
    private List<BookLayout.View> views = new ArrayList<>();
    private int spread = 0;

    private EditBox search;
    // transformation of "book" coordinates into screen coordinates
    private float k = 1f;
    private int ox, oy;
    private Component tabHoverText = null;

    public BookScreen() {
        super(Component.translatable("item.golem_logix.guide_book"));
        loadVisited();
        restoreState();
    }

    static MutableComponent styled(String s) {
        return BookLayout.styled(s);
    }

    /** For the developer rig: checks pagination and table fit for every entry in the current language. */
    public static List<String> auditLayout() {
        return BookLayout.auditLayout(Minecraft.getInstance().font);
    }

    /** The book opened right at an entry (a click on the secret knowledge message); an unknown entry opens the map. */
    public static BookScreen openAt(String entryId) {
        BookModel model = BookModel.get();
        BookModel.Entry e = model.byId.get(entryId);
        if (e != null && e.visible()) {
            lastState = new String[]{Integer.toString(model.categories.indexOf(e.category)), entryId, "0"};
        } else {
            lastState = new String[]{"0", "", "0"};
        }
        return new BookScreen();
    }

    // ---------------------------------------------- last opened page & reading progress

    private Path stateFile() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("golem_logix_book_state.txt");
    }

    /** Recalls the section, entry and spread where the player closed the book (from memory or from a file). */
    private void restoreState() {
        String[] saved = lastState;
        if (saved == null) {
            try {
                Path p = stateFile();
                if (Files.exists(p)) {
                    List<String> lines = Files.readAllLines(p);
                    if (lines.size() >= 3) {
                        saved = new String[]{lines.get(0), lines.get(1), lines.get(2)};
                    }
                }
            } catch (IOException ignored) {
            }
        }
        if (saved == null || book.categories.isEmpty()) {
            return;
        }
        try {
            categoryIndex = Mth.clamp(Integer.parseInt(saved[0].trim()), 0, book.categories.size() - 1);
            BookModel.Entry e = saved[1].isBlank() ? null : book.byId.get(saved[1].trim());
            if (e != null && e.visible()) {
                openEntry = e;
                categoryIndex = book.categories.indexOf(e.category);
                spread = Math.max(0, Integer.parseInt(saved[2].trim()));
            }
        } catch (NumberFormatException ignored) {
        }
    }

    private void saveState() {
        lastState = new String[]{Integer.toString(categoryIndex), openEntry == null ? "" : openEntry.id, Integer.toString(spread)};
        try {
            Files.writeString(stateFile(), String.join("\n", lastState));
        } catch (IOException ignored) {
        }
    }

    @Override
    public void removed() {
        saveState();
        super.removed();
    }

    private Path progressFile() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("golem_logix_book.txt");
    }

    private void loadVisited() {
        try {
            Path p = progressFile();
            if (Files.exists(p)) {
                visited.addAll(Files.readAllLines(p));
            }
        } catch (IOException ignored) {
        }
    }

    private void markVisited(String id) {
        if (visited.add(id)) {
            try {
                Files.writeString(progressFile(), String.join("\n", visited));
            } catch (IOException ignored) {
            }
        }
    }

    private BookModel.Category category() {
        if (book.categories.isEmpty()) {
            return null;
        }
        categoryIndex = Mth.clamp(categoryIndex, 0, book.categories.size() - 1);
        BookModel.Category c = book.categories.get(categoryIndex);
        if (!c.visible()) {
            for (int i = 0; i < book.categories.size(); i++) {
                if (book.categories.get(i).visible()) {
                    categoryIndex = i;
                    return book.categories.get(i);
                }
            }
            categoryIndex = 0;
            c = book.categories.get(0);
        }
        return c;
    }

    // ----------------------------------------------------------------- lifecycle

    @Override
    protected void init() {
        k = Math.max(0.1f, Math.min(1f, Math.min((width - 16f) / (W + 56f), (height - 8f) / H)));
        ox = Math.round((width - W * k) / 2f + 14f * k);
        oy = Math.round((height - H * k) / 2f);
        String oldSearch = search != null ? search.getValue() : "";
        search = new EditBox(font, Math.round(ox + (W - 126) * k), Math.round(oy + 9 * k), Math.round(106 * k), 12,
                Component.translatable("gui.golem_logix.search"));
        search.setBordered(false);
        search.setMaxLength(40);
        search.setTextColor(0xFFE8D8B0);
        search.setHint(Component.translatable("gui.golem_logix.search").withStyle(ChatFormatting.DARK_GRAY));
        search.setValue(oldSearch);
        search.setResponder(s -> mapView.invalidateSearch());
        mapView.invalidateSearch();
        addWidget(search);
        syncSearchVisibility();
        if (openEntry != null) {
            views = BookLayout.buildViews(font, openEntry);
            spread = Mth.clamp(spread, 0, spreads() - 1);
        }
    }

    private void syncSearchVisibility() {
        if (search != null) {
            boolean mapMode = openEntry == null;
            search.visible = mapMode;
            search.active = mapMode;
            if (!mapMode) {
                search.setFocused(false);
            }
        }
    }

    private void open(BookModel.Entry entry) {
        categoryIndex = book.categories.indexOf(entry.category);
        openEntry = entry;
        views = BookLayout.buildViews(font, entry);
        spread = 0;
        markVisited(entry.id);
        if (search != null) {
            search.setValue("");
        }
        mapView.invalidateSearch();
        syncSearchVisibility();
    }

    private void closeEntry() {
        openEntry = null;
        views = new ArrayList<>();
        mapView.invalidateSearch();
        syncSearchVisibility();
    }

    private int spreads() {
        return Math.max(1, views.size() / 2);
    }

    private String searchQuery() {
        return search == null ? "" : search.getValue();
    }

    // ------------------------------------------------------------------ rendering

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        pageRenderer.resetHover();
        tabHoverText = null;
        double bx = (mouseX - ox) / k, by = (mouseY - oy) / k;

        g.pose().pushMatrix();
        g.pose().translate(ox, oy);
        g.pose().scale(k, k);

        // the bookmark tabs before the binding: the cover lies over their spines, not the other way round
        drawTabs(g, (int) bx, (int) by);
        drawCover(g);
        if (openEntry == null) {
            mapView.drawMap(g, font, book, category(), categoryIndex, visited, searchQuery(), (int) bx, (int) by, partial);
        } else {
            pageRenderer.drawArticle(g, font, views, spread, spreads(), (int) bx, (int) by, this::onClose);
        }
        drawCorners(g);
        g.pose().popMatrix();

        if (openEntry == null && search != null) {
            search.extractRenderState(g, mouseX, mouseY, partial);
        }
        ItemStack hoverStack = pageRenderer.hoverStack();
        Component hoverText = tabHoverText != null ? tabHoverText : pageRenderer.hoverText();
        if (!hoverStack.isEmpty()) {
            g.setTooltipForNextFrame(font, hoverStack, mouseX, mouseY);
        } else if (hoverText != null) {
            g.setTooltipForNextFrame(font, hoverText, mouseX, mouseY);
        } else if (openEntry == null && mapView.hoverTip() != null) {
            g.setComponentTooltipForNextFrame(font, mapView.hoverTip(), mouseX, mouseY);
        }
    }

    private static void drawCover(GuiGraphicsExtractor g) {
        // leather binding
        g.fill(-6, -6, W + 6, H + 6, 0xFF1A0F08);
        g.fill(-4, -4, W + 4, H + 4, 0xFF4A2E1A);
        g.fill(-3, -3, W + 3, H + 3, 0xFF5E3B22);
        g.fill(0, 0, W, H, 0xFF3A2314);
    }

    /** Brass corners of the binding, on top of all content so the interface does not overlap the cover. */
    private static void drawCorners(GuiGraphicsExtractor g) {
        for (int[] c : CORNERS) {
            g.fill(c[0], c[1], c[0] + 18, c[1] + 18, GolemGui.BRASS_DARK);
            g.fill(c[0] + 1, c[1] + 1, c[0] + 17, c[1] + 17, GolemGui.BRASS);
            g.fill(c[0] + 5, c[1] + 5, c[0] + 13, c[1] + 13, GolemGui.BRASS_DARK);
            g.fill(c[0] + 7, c[1] + 7, c[0] + 11, c[1] + 11, GolemGui.CYAN);
        }
    }

    /** Refreshes the reusable list of visible section indices without allocating each frame. */
    private List<Integer> tabs() {
        visibleTabs.clear();
        for (int i = 0; i < book.categories.size(); i++) {
            if (book.categories.get(i).visible()) {
                visibleTabs.add(i);
            }
        }
        return visibleTabs;
    }

    /** Whether the section has visible but not yet read entries. */
    private boolean hasUnread(BookModel.Category c) {
        for (BookModel.Entry e : c.entries) {
            if (e.visible() && !visited.contains(e.id)) {
                return true;
            }
        }
        return false;
    }

    /** Tab spacing: all bookmarks fit between the brass corners of the binding. */
    private static int tabStep(int count) {
        return Math.max(18, Math.min(TAB_STEP, (H - 2 * TAB_TOP) / Math.max(1, count)));
    }

    /** Left edge of a tab: the bookmark sticks out from under the binding, the selected one sticks out further. */
    private int tabX(int i) {
        return i == categoryIndex ? -34 : -30;
    }

    private void drawTabs(GuiGraphicsExtractor g, int mx, int my) {
        List<Integer> tabs = tabs();
        long time = Minecraft.getInstance().level != null ? Minecraft.getInstance().level.getGameTime() : 0;
        int step = tabStep(tabs.size()), th = step - 1;
        for (int j = 0; j < tabs.size(); j++) {
            int i = tabs.get(j);
            BookModel.Category c = book.categories.get(i);
            int x = tabX(i);
            int y = TAB_TOP + j * step;
            boolean hover = mx >= x && mx < -6 && my >= y && my < y + th;
            // the bookmark goes under the binding (the cover is drawn over its right edge)
            g.fill(x, y, 2, y + th, 0xFF1A0F08);
            g.fill(x + 1, y + 1, 2, y + th - 1, hover ? GolemGui.BRASS_L : (i == categoryIndex ? GolemGui.BRASS : GolemGui.BRASS_DARK));
            g.fill(x + 3, y + 3, 2, y + th - 3, 0xFF16222B);
            g.fill(x + 3, y + 3, x + 5, y + th - 3, c.color);
            g.item(c.icon, x + 7, y + (th - 16) / 2);
            if (hasUnread(c)) {
                // unread entries in the section: a pulsing light in the tab corner
                int a = (int) (140 + 115 * Math.sin(time * 0.2 + j));
                g.fill(x + 21, y + 3, x + 25, y + 7, (Math.max(0, Math.min(255, a)) << 24) | 0x38D9D6);
            }
            if (hover) {
                tabHoverText = Component.literal(c.title);
            }
        }
    }

    // ----------------------------------------------------------------- input

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x(), mouseY = event.y();
        int button = event.button();
        double bx = (mouseX - ox) / k, by = (mouseY - oy) / k;

        // section tabs
        if (button == 0) {
            List<Integer> tabs = tabs();
            int step = tabStep(tabs.size());
            for (int j = 0; j < tabs.size(); j++) {
                int i = tabs.get(j);
                int x = tabX(i), y = TAB_TOP + j * step;
                if (bx >= x && bx < -6 && by >= y && by < y + step - 1) {
                    categoryIndex = i;
                    closeEntry();
                    return true;
                }
            }
        }
        if (openEntry != null) {
            return (button == 0 && pageRenderer.clickArticle(bx, by, spread, spreads(), s -> spread = s, this::closeEntry))
                    || super.mouseClicked(event, doubleClick);
        }

        if (button == 0 && mapView.clickSearchResults(book, searchQuery(), bx, by, this::open)) {
            return true;
        }
        if (button == 0 && mapView.clickNoMagicBanner(book, bx, by, this::open)) {
            return true;
        }

        // a right-click on the search field clears it
        if (search != null) {
            if (button == 1 && search.isMouseOver(mouseX, mouseY)) {
                search.setValue("");
                setFocused(search);
                return true;
            }
            if (search.mouseClicked(event, doubleClick)) {
                setFocused(search);
                return true;
            }
            search.setFocused(false);
        }

        if (button == 0 && mapView.startDrag(book.categories.size(), bx, by)) {
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent event, double dx, double dy) {
        if (openEntry == null && mapView.mouseDragged(categoryIndex, book.categories.size(), k, dx, dy)) {
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent event) {
        double bx = (event.x() - ox) / k, by = (event.y() - oy) / k;
        BookModel.Category cat = openEntry == null ? category() : null;
        if (mapView.mouseReleased(cat, categoryIndex, bx, by, this::open)) {
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (openEntry == null) {
            double bx = (mouseX - ox) / k, by = (mouseY - oy) / k;
            if (mapView.mouseScrolled(categoryIndex, book.categories.size(), bx, by, scrollY)) {
                return true;
            }
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        if (scrollY < 0 && spread < spreads() - 1) {
            spread++;
        } else if (scrollY > 0 && spread > 0) {
            spread--;
        }
        return true;
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        int key = event.key();
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            if (openEntry != null) {
                closeEntry();
                return true;
            }
            if (search != null && search.isFocused() && !search.getValue().isEmpty()) {
                search.setValue("");
                return true;
            }
            return super.keyPressed(event);
        }
        if (openEntry != null) {
            if ((key == GLFW.GLFW_KEY_RIGHT || key == GLFW.GLFW_KEY_D) && spread < spreads() - 1) {
                spread++;
                return true;
            }
            if ((key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_A) && spread > 0) {
                spread--;
                return true;
            }
            if (key == GLFW.GLFW_KEY_BACKSPACE) {
                closeEntry();
                return true;
            }
            return super.keyPressed(event);
        }
        if (search != null && search.isFocused()) {
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                List<BookModel.Entry> r = mapView.searchResults(book, searchQuery());
                if (!r.isEmpty()) {
                    open(r.get(0));
                }
                return true;
            }
            search.keyPressed(event);
            return true;
        }
        if (search != null && (key == GLFW.GLFW_KEY_TAB || key == GLFW.GLFW_KEY_SLASH)) {
            search.setFocused(true);
            setFocused(search);
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(net.minecraft.client.input.CharacterEvent event) {
        if (openEntry == null && search != null && search.isFocused()) {
            return search.charTyped(event);
        }
        return super.charTyped(event);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        g.fill(0, 0, width, height, 0xB0000000);
    }
}
