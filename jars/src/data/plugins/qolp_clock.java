package data.plugins;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignUIAPI;
import com.fs.starfarer.api.campaign.CoreUITabId;
import com.fs.starfarer.api.campaign.listeners.CampaignUIRenderingListener;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.util.Misc;
import org.lazywizard.lazylib.ui.FontException;
import org.lazywizard.lazylib.ui.LazyFont;
import org.lwjgl.opengl.GL11;
import java.awt.Color;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Real-world local time, rendered once per UI frame, including while paused. */
public class qolp_clock implements CampaignUIRenderingListener {
    private final boolean twelveHour;
    private LazyFont.DrawableString text;
    private boolean fontFailed;

    qolp_clock(boolean twelveHour) { this.twelveHour = twelveHour; }

    static String formatTime(LocalTime time, boolean twelveHour) {
        return time.format(DateTimeFormatter.ofPattern(twelveHour ? "h:mm a" : "H:mm", Locale.ENGLISH));
    }

    static boolean shouldRender(CampaignUIAPI ui) {
        return ui != null && !ui.isHideUI() && !ui.isShowingDialog() && !ui.isShowingMenu()
                && (ui.getCurrentCoreTab() == null || ui.getCurrentCoreTab() == CoreUITabId.FLEET);
    }

    @Override public void renderInUICoordsBelowUI(ViewportAPI viewport) {}
    @Override public void renderInUICoordsAboveUIAndTooltips(ViewportAPI viewport) {}

    @Override
    public void renderInUICoordsAboveUIBelowTooltips(ViewportAPI viewport) {
        if (Global.getSector() == null || !shouldRender(Global.getSector().getCampaignUI())) return;
        // Already scaled UI coordinates; re-read height after resolution changes.
        drawClock(formatTime(LocalTime.now(), twelveHour), Global.getSettings().getScreenHeight() - 115f);
    }

    protected void drawClock(String value, float y) {
        if (fontFailed) return;
        if (text == null) {
            try {
                text = LazyFont.loadFont("graphics/fonts/orbitron20aa.fnt").createText();
                text.setFontSize(20f);
            } catch (FontException e) {
                fontFailed = true;
                Global.getLogger(qolp_clock.class).error("Unable to load the campaign clock font", e);
                return;
            }
        }
        // The campaign renderer owns projection/viewport state. Restore all text drawing state.
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT
                | GL11.GL_CURRENT_BIT | GL11.GL_TEXTURE_BIT);
        try {
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            text.setText(value);
            text.setBaseColor(Color.BLACK);
            text.draw(9f, y - 1f);
            text.setBaseColor(Misc.getTooltipTitleAndLightHighlightColor());
            text.draw(8f, y);
        } finally {
            GL11.glPopAttrib();
        }
    }
}
