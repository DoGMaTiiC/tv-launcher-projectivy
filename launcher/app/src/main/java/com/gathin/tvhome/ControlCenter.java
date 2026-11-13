package com.gathin.tvhome;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Apple tvOS 27.2 TVSystemUIService Bento Control Center overlay with Solarium Glass focus. */
final class ControlCenter {

    static final int PANEL_WIDTH_DP = 304;
    static final float DIM_AMOUNT = 0f;
    static final int SLIDE_DURATION_MS = 200;
    static final int GLASS_FILL = 0xDC1B1B20;
    static final int GLASS_STROKE = 0x33FFFFFF;
    static final int FOCUS_FILL = 0xFFFFFFFF;
    static final int FOCUS_INK = 0xFF111114;
    static final int FOCUS_SUB_INK = 0x99111114;
    static final int TEXT_PRIMARY = 0xFFFFFFFF;
    static final int TEXT_SECONDARY = 0xB3FFFFFF;
    static final int ACCENT_BLUE = 0xFF0A84FF;
    static final int ACCENT_INDIGO = 0xFF5E5CE6;
    static final int ACCENT_TEAL = 0xFF63E6E2;

    private static final int ICON_POWER = 0, ICON_WIFI = 1, ICON_DND = 2, ICON_AUDIO = 3,
            ICON_TIMER = 4, ICON_INPUTS = 5, ICON_A11Y = 6, ICON_LOCK = 7, ICON_SEARCH = 8;

    enum Action {
        INPUTS, WIFI, SCREENSAVER, LIBRARY, REORDER, SETTINGS,
        MEDIA_PLAY_PAUSE, MEDIA_NEXT, TOGGLE_MUSIC_PILL
    }

    static final class Item {
        final Action action;
        final String label;
        final int labelRes;

        Item(Action action, String label, int labelRes) {
            this.action = action;
            this.label = label;
            this.labelRes = labelRes;
        }
    }

    interface Host {
        void onInputs();
        void onWifi();
        void onScreensaver();
        void onLibrary();
        void onReorder();
        void onSettings();
        void onMediaPlayPause();
        void onMediaNext();
        void onToggleMusicPill();
        boolean isMusicPillVisible();
        String nowPlayingTitle();
        String nowPlayingArtist();
        boolean isMediaPlaying();
        default String onCycleMusicCorner() { return ""; }
        default void onSearch() { onLibrary(); }
        default boolean onToggleDoNotDisturb() { return false; }
        default void onAudioRouteSelected(String route) {}
    }

    private ControlCenter() {}

    static List<Item> buildItems(boolean showInputs, boolean showLibrary, boolean reorderAllowed) {
        List<Item> items = new ArrayList<>(6);
        if (showInputs) items.add(new Item(Action.INPUTS, "Inputs", R.string.inputs));
        items.add(new Item(Action.WIFI, "Wi-Fi", R.string.wifi));
        items.add(new Item(Action.SCREENSAVER, "Screensaver", 0));
        if (showLibrary) items.add(new Item(Action.LIBRARY, "App library", R.string.app_library));
        if (reorderAllowed) items.add(new Item(Action.REORDER, "Reorder apps", R.string.reorder));
        items.add(new Item(Action.SETTINGS, "Settings", R.string.settings));
        return Collections.unmodifiableList(items);
    }

    static String formatNowPlaying(String title, String artist, boolean playing) {
        String t = title == null ? "" : title.trim();
        String a = artist == null ? "" : artist.trim();
        if (t.isEmpty() && a.isEmpty()) return "Nothing playing";
        String track = (!t.isEmpty() && !a.isEmpty()) ? (t + " · " + a) : (!t.isEmpty() ? t : a);
        return (playing ? "Playing · " : "Paused · ") + track;
    }

    static String formatCornerLabel(String corner) {
        if ("top_left".equals(corner)) return "Top Left";
        if ("bottom_left".equals(corner)) return "Bottom Left";
        if ("bottom_right".equals(corner)) return "Bottom Right";
        return "Top Right";
    }

    static Dialog show(Activity activity, Host host) {
        Config cfg = Config.load(activity);
        return show(activity, host, cfg == null || cfg.showInputsPill,
                cfg != null && (cfg.appLibrary || cfg.libraryButton || cfg.showLibraryPill),
                cfg != null && cfg.reorderButton, cfg != null && cfg.reducedMotion);
    }

    static Dialog show(Activity activity, Host host, boolean showInputs, boolean showLibrary,
                       boolean reorderAllowed, boolean reducedMotion) {
        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCancelable(true);
        dialog.setCanceledOnTouchOutside(true);

        float d = activity.getResources().getDisplayMetrics().density;
        Typeface med = UiFont.get(activity, R.string.font_medium);
        Typeface reg = UiFont.get(activity, R.string.font_regular);

        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setClipChildren(false);
        root.setClipToPadding(false);
        int pad = dp(8, d);
        root.setPadding(pad, pad, pad, pad);

        // 1. Top Bento Section: Power Off (left 136x128) + Wi-Fi & Do Not Disturb (right 148x60 x2)
        LinearLayout topRow = hRow(activity);
        Tile powerTile = makeBigTile(activity, d, med, "Power Off", ICON_POWER);
        powerTile.view.setId(View.generateViewId());

        LinearLayout rightCol = new LinearLayout(activity);
        rightCol.setOrientation(LinearLayout.VERTICAL);
        rightCol.setClipChildren(false);
        rightCol.setClipToPadding(false);
        Tile wifiTile = makePillTile(activity, d, med, reg, 148, "Wi-Fi", "Connected", ACCENT_BLUE, ICON_WIFI);
        wifiTile.view.setId(View.generateViewId());
        Tile dndTile = makePillTile(activity, d, med, reg, 148, "Do Not Disturb", "Off", ACCENT_INDIGO, ICON_DND);
        dndTile.view.setId(View.generateViewId());

        LinearLayout.LayoutParams wLp = new LinearLayout.LayoutParams(dp(148, d), dp(60, d));
        wLp.bottomMargin = dp(8, d);
        rightCol.addView(wifiTile.view, wLp);
        rightCol.addView(dndTile.view, new LinearLayout.LayoutParams(dp(148, d), dp(60, d)));

        LinearLayout.LayoutParams pLp = new LinearLayout.LayoutParams(dp(136, d), dp(128, d));
        pLp.setMarginEnd(dp(12, d));
        topRow.addView(powerTile.view, pLp);
        topRow.addView(rightCol, new LinearLayout.LayoutParams(dp(148, d), dp(128, d)));
        root.addView(topRow, new LinearLayout.LayoutParams(dp(296, d), dp(128, d)));

        // 2. Middle Bento Row: Multiple / Surround (142x60) + Sleep / Timer (142x60)
        LinearLayout midRow = hRow(activity);
        Tile audioTile = makePillTile(activity, d, med, reg, 142, "Multiple", "Surround", ACCENT_BLUE, ICON_AUDIO);
        audioTile.view.setId(View.generateViewId());
        Tile timerTile = makePillTile(activity, d, med, reg, 142, "Sleep", "Timer", 0xFF1F4E50, ICON_TIMER);
        timerTile.view.setId(View.generateViewId());

        LinearLayout.LayoutParams aLp = new LinearLayout.LayoutParams(dp(142, d), dp(60, d));
        aLp.setMarginEnd(dp(12, d));
        midRow.addView(audioTile.view, aLp);
        midRow.addView(timerTile.view, new LinearLayout.LayoutParams(dp(142, d), dp(60, d)));
        LinearLayout.LayoutParams midLp = new LinearLayout.LayoutParams(dp(296, d), dp(60, d));
        midLp.topMargin = dp(12, d);
        root.addView(midRow, midLp);

        // 3. Bottom Bento Row: 4 compact pills (66dp x 46dp, corner 23dp)
        LinearLayout botRow = hRow(activity);
        Tile btnInputs = makeCompactTile(activity, d, ICON_INPUTS);
        Tile btnSettings = makeCompactTile(activity, d, ICON_A11Y);
        Tile btnLock = makeCompactTile(activity, d, ICON_LOCK);
        Tile btnSearch = makeCompactTile(activity, d, ICON_SEARCH);
        Tile[] compacts = {btnInputs, btnSettings, btnLock, btnSearch};
        for (int i = 0; i < compacts.length; i++) {
            compacts[i].view.setId(View.generateViewId());
            LinearLayout.LayoutParams bLp = new LinearLayout.LayoutParams(dp(66, d), dp(46, d));
            if (i < 3) bLp.setMarginEnd(dp(i == 1 ? 10 : 11, d));
            botRow.addView(compacts[i].view, bLp);
        }
        LinearLayout.LayoutParams botLp = new LinearLayout.LayoutParams(dp(296, d), dp(46, d));
        botLp.topMargin = dp(12, d);
        root.addView(botRow, botLp);

        // 4. Optional inline Audio Output sub-popover (toggled by Multiple / Surround tile)
        LinearLayout audioSheet = new LinearLayout(activity);
        audioSheet.setOrientation(LinearLayout.VERTICAL);
        audioSheet.setVisibility(View.GONE);
        audioSheet.setBackground(glassRect(GLASS_FILL, GLASS_STROKE, dp(22, d), d));
        int sPad = dp(12, d);
        audioSheet.setPadding(sPad, sPad, sPad, sPad);
        LinearLayout.LayoutParams sheetLp = new LinearLayout.LayoutParams(dp(296, d), ViewGroup.LayoutParams.WRAP_CONTENT);
        sheetLp.topMargin = dp(12, d);
        root.addView(audioSheet, sheetLp);

        String[] routes = {"HomePod Speakers", "Surround Sound", "Whole Room Audio"};
        final View[] firstRouteBtn = {null};
        for (int i = 0; i < routes.length; i++) {
            final String rName = routes[i];
            TextView rBtn = new TextView(activity);
            rBtn.setText(rName);
            rBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            rBtn.setTypeface(med);
            rBtn.setTextColor(TEXT_PRIMARY);
            rBtn.setPadding(dp(12, d), dp(8, d), dp(12, d), dp(8, d));
            rBtn.setFocusable(true);
            rBtn.setFocusableInTouchMode(true);
            rBtn.setBackground(glassRect(0x18FFFFFF, 0x24FFFFFF, dp(14, d), d));
            rBtn.setOnFocusChangeListener((v, f) -> {
                rBtn.setBackground(glassRect(f ? FOCUS_FILL : 0x18FFFFFF, f ? FOCUS_FILL : 0x24FFFFFF, dp(14, d), d));
                rBtn.setTextColor(f ? FOCUS_INK : TEXT_PRIMARY);
                float sc = f ? 1.04f : 1f;
                v.animate().scaleX(sc).scaleY(sc).setDuration(100).start();
            });
            rBtn.setOnClickListener(v -> {
                audioTile.sub.setText(rName.split(" ")[0]);
                if (host != null) host.onAudioRouteSelected(rName);
                audioSheet.setVisibility(View.GONE);
                audioTile.view.requestFocus();
            });
            LinearLayout.LayoutParams rLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (i < routes.length - 1) rLp.bottomMargin = dp(6, d);
            audioSheet.addView(rBtn, rLp);
            if (i == 0) firstRouteBtn[0] = rBtn;
        }

        // Explicit 2D DPAD wiring
        wireFocus(powerTile.view, -1, wifiTile.view.getId(), -1, audioTile.view.getId());
        wireFocus(wifiTile.view, powerTile.view.getId(), -1, -1, dndTile.view.getId());
        wireFocus(dndTile.view, powerTile.view.getId(), -1, wifiTile.view.getId(), timerTile.view.getId());
        wireFocus(audioTile.view, -1, timerTile.view.getId(), powerTile.view.getId(), btnInputs.view.getId());
        wireFocus(timerTile.view, audioTile.view.getId(), -1, dndTile.view.getId(), btnLock.view.getId());
        wireFocus(btnInputs.view, -1, btnSettings.view.getId(), audioTile.view.getId(), -1);
        wireFocus(btnSettings.view, btnInputs.view.getId(), btnLock.view.getId(), audioTile.view.getId(), -1);
        wireFocus(btnLock.view, btnSettings.view.getId(), btnSearch.view.getId(), timerTile.view.getId(), -1);
        wireFocus(btnSearch.view, btnLock.view.getId(), -1, timerTile.view.getId(), -1);

        final boolean[] dndOn = {false};
        Tile[] all = {powerTile, wifiTile, dndTile, audioTile, timerTile, btnInputs, btnSettings, btnLock, btnSearch};
        View.OnClickListener clicker = v -> {
            int idx = (Integer) v.getTag();
            if (idx == 2) {
                dndOn[0] = host != null ? host.onToggleDoNotDisturb() : !dndOn[0];
                dndTile.sub.setText(dndOn[0] ? "On" : "Off");
                return;
            }
            if (idx == 3) {
                boolean show = audioSheet.getVisibility() != View.VISIBLE;
                audioSheet.setVisibility(show ? View.VISIBLE : View.GONE);
                if (show && firstRouteBtn[0] != null) firstRouteBtn[0].requestFocus();
                return;
            }
            if (idx == 4) {
                String corner = host != null ? host.onCycleMusicCorner() : "";
                if (corner != null && !corner.isEmpty()) timerTile.sub.setText(corner);
                return;
            }
            dialog.dismiss();
            if (host == null) return;
            if (idx == 0 || idx == 7) host.onScreensaver();
            else if (idx == 1) host.onWifi();
            else if (idx == 5) host.onInputs();
            else if (idx == 6) host.onSettings();
            else if (idx == 8) host.onSearch();
        };
        for (int i = 0; i < all.length; i++) {
            all[i].view.setTag(i);
            all[i].view.setOnClickListener(clicker);
        }

        dialog.setContentView(root);
        Window w = dialog.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams lp = w.getAttributes();
            lp.gravity = Gravity.TOP | Gravity.END;
            lp.x = dp(40, d); // 40dp + 8dp root pad = 48dp rightMargin
            lp.y = dp(50, d); // 50dp + 8dp root pad = 58dp topMargin
            lp.width = dp(PANEL_WIDTH_DP + 8, d);
            lp.height = WindowManager.LayoutParams.WRAP_CONTENT;
            lp.dimAmount = DIM_AMOUNT;
            w.setAttributes(lp);
        }

        root.setPivotX(dp(PANEL_WIDTH_DP, d));
        root.setPivotY(0f);
        root.setScaleX(0.94f);
        root.setScaleY(0.94f);
        root.setAlpha(0f);
        root.animate().scaleX(1f).scaleY(1f).alpha(1f)
                .setDuration(reducedMotion ? Motion.REDUCED_MS : SLIDE_DURATION_MS)
                .setInterpolator(new DecelerateInterpolator(1.6f)).start();

        dialog.show();
        powerTile.view.requestFocus();
        return dialog;
    }

    static void dispatchAction(Host host, Action a) {
        if (host == null || a == null) return;
        if (a == Action.INPUTS) host.onInputs();
        else if (a == Action.WIFI) host.onWifi();
        else if (a == Action.SCREENSAVER) host.onScreensaver();
        else if (a == Action.LIBRARY) host.onLibrary();
        else if (a == Action.REORDER) host.onReorder();
        else if (a == Action.SETTINGS) host.onSettings();
        else if (a == Action.MEDIA_PLAY_PAUSE) host.onMediaPlayPause();
        else if (a == Action.MEDIA_NEXT) host.onMediaNext();
        else if (a == Action.TOGGLE_MUSIC_PILL) host.onToggleMusicPill();
    }

    private static final class Tile {
        final ViewGroup view;
        final TextView title, sub;
        final BentoIcon icon;
        Tile(ViewGroup view, TextView title, TextView sub, BentoIcon icon) {
            this.view = view;
            this.title = title;
            this.sub = sub;
            this.icon = icon;
        }
    }

    private static LinearLayout hRow(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setClipChildren(false);
        l.setClipToPadding(false);
        return l;
    }

    private static void wireFocus(View v, int left, int right, int up, int down) {
        if (left != -1) v.setNextFocusLeftId(left);
        if (right != -1) v.setNextFocusRightId(right);
        if (up != -1) v.setNextFocusUpId(up);
        if (down != -1) v.setNextFocusDownId(down);
    }

    private static Tile makeBigTile(Context c, float d, Typeface med, String label, int iconKind) {
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(dp(12, d), dp(14, d), dp(12, d), dp(12, d));
        BentoIcon drw = new BentoIcon(iconKind, 0, d);
        ImageView iv = new ImageView(c);
        iv.setImageDrawable(drw);
        LinearLayout.LayoutParams ivLp = new LinearLayout.LayoutParams(dp(56, d), dp(56, d));
        ivLp.bottomMargin = dp(10, d);
        box.addView(iv, ivLp);

        TextView tv = makeLabel(c, label, 13, med, TEXT_PRIMARY);
        tv.setGravity(Gravity.CENTER);
        box.addView(tv);
        applySolariumFocus(box, d, 24, tv, null, drw);
        return new Tile(box, tv, null, drw);
    }

    private static Tile makePillTile(Context c, float d, Typeface med, Typeface reg, int wDp,
                                     String label, String subtitle, int badgeColor, int iconKind) {
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setGravity(Gravity.CENTER_VERTICAL);
        box.setPadding(dp(11, d), dp(8, d), dp(10, d), dp(8, d));

        BentoIcon drw = new BentoIcon(iconKind, badgeColor, d);
        ImageView iv = new ImageView(c);
        iv.setImageDrawable(drw);
        LinearLayout.LayoutParams ivLp = new LinearLayout.LayoutParams(dp(36, d), dp(36, d));
        ivLp.setMarginEnd(dp(9, d));
        box.addView(iv, ivLp);

        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        TextView t1 = makeLabel(c, label, wDp < 145 ? 13 : 12, med, TEXT_PRIMARY);
        TextView t2 = makeLabel(c, subtitle, 11, reg, TEXT_SECONDARY);
        col.addView(t1);
        col.addView(t2);
        box.addView(col, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        applySolariumFocus(box, d, 30, t1, t2, drw);
        return new Tile(box, t1, t2, drw);
    }

    private static Tile makeCompactTile(Context c, float d, int iconKind) {
        LinearLayout box = new LinearLayout(c);
        box.setGravity(Gravity.CENTER);
        BentoIcon drw = new BentoIcon(iconKind, 0, d);
        ImageView iv = new ImageView(c);
        iv.setImageDrawable(drw);
        box.addView(iv, new LinearLayout.LayoutParams(dp(24, d), dp(24, d)));
        applySolariumFocus(box, d, 23, null, null, drw);
        return new Tile(box, null, null, drw);
    }

    private static void applySolariumFocus(ViewGroup box, float d, int radiusDp,
                                           TextView primary, TextView secondary, BentoIcon icon) {
        box.setFocusable(true);
        box.setFocusableInTouchMode(true);
        box.setClickable(true);
        box.setBackground(glassRect(GLASS_FILL, GLASS_STROKE, dp(radiusDp, d), d));
        box.setOnFocusChangeListener((v, f) -> {
            v.setBackground(glassRect(f ? FOCUS_FILL : GLASS_FILL, f ? FOCUS_FILL : GLASS_STROKE, dp(radiusDp, d), d));
            if (primary != null) primary.setTextColor(f ? FOCUS_INK : TEXT_PRIMARY);
            if (secondary != null) secondary.setTextColor(f ? FOCUS_SUB_INK : TEXT_SECONDARY);
            if (icon != null) icon.setFocused(f);
            v.setElevation(f ? dp(10, d) : dp(2, d));
            float s = f ? 1.06f : 1f;
            v.animate().scaleX(s).scaleY(s).setDuration(120).start();
        });
    }

    private static TextView makeLabel(Context c, String text, int sp, Typeface tf, int color) {
        TextView tv = new TextView(c);
        tv.setText(text);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        tv.setTypeface(tf);
        tv.setTextColor(color);
        tv.setSingleLine(true);
        tv.setEllipsize(TextUtils.TruncateAt.END);
        return tv;
    }

    private static GradientDrawable glassRect(int fill, int stroke, int radiusPx, float d) {
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(fill);
        gd.setCornerRadius(radiusPx);
        gd.setStroke(Math.max(1, Math.round(d)), stroke);
        return gd;
    }

    private static int dp(int v, float d) { return Math.round(v * d); }

    /** Lightweight vector glyph drawable for all 7 Bento Control Center tiles. */
    private static final class BentoIcon extends Drawable {
        private final int kind, badgeColor;
        private final float d;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint f = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rf = new RectF();
        private final Path path = new Path();
        private boolean focused;

        BentoIcon(int kind, int badgeColor, float d) {
            this.kind = kind;
            this.badgeColor = badgeColor;
            this.d = d;
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeJoin(Paint.Join.ROUND);
        }

        void setFocused(boolean focused) {
            if (this.focused != focused) {
                this.focused = focused;
                invalidateSelf();
            }
        }

        @Override public void draw(Canvas c) {
            Rect b = getBounds();
            float cx = b.exactCenterX(), cy = b.exactCenterY(), s = Math.min(b.width(), b.height());
            int ink = (badgeColor != 0 || !focused) ? Color.WHITE : FOCUS_INK;
            if (badgeColor != 0) {
                f.setColor(badgeColor);
                c.drawCircle(cx, cy, s * 0.48f, f);
            }
            p.setColor(ink);
            f.setColor(ink);
            if (kind == ICON_POWER) {
                p.setStrokeWidth(3.2f * d);
                rf.set(cx - s * 0.34f, cy - s * 0.34f, cx + s * 0.34f, cy + s * 0.34f);
                c.drawArc(rf, -58f, 296f, false, p);
                c.drawLine(cx, cy - s * 0.42f, cx, cy - s * 0.04f, p);
            } else if (kind == ICON_WIFI) {
                p.setStrokeWidth(1.9f * d);
                float wy = cy + s * 0.14f;
                c.drawCircle(cx, wy, 1.4f * d, f);
                for (int i = 1; i <= 3; i++) {
                    float r = i * s * 0.095f;
                    rf.set(cx - r, wy - r, cx + r, wy + r);
                    c.drawArc(rf, -135f, 90f, false, p);
                }
            } else if (kind == ICON_DND) {
                path.reset();
                float mr = s * 0.22f;
                path.addCircle(cx, cy, mr, Path.Direction.CW);
                Path cut = new Path();
                cut.addCircle(cx + mr * 0.48f, cy - mr * 0.35f, mr * 0.86f, Path.Direction.CW);
                path.op(cut, Path.Op.DIFFERENCE);
                c.drawPath(path, f);
            } else if (kind == ICON_AUDIO) {
                p.setStrokeWidth(1.8f * d);
                c.drawRoundRect(cx - s * 0.2f, cy - s * 0.16f, cx + s * 0.04f, cy + s * 0.16f, 2f * d, 2f * d, f);
                rf.set(cx - s * 0.02f, cy - s * 0.16f, cx + s * 0.16f, cy + s * 0.16f);
                c.drawArc(rf, -60f, 120f, false, p);
                rf.set(cx + s * 0.04f, cy - s * 0.24f, cx + s * 0.24f, cy + s * 0.24f);
                c.drawArc(rf, -60f, 120f, false, p);
            } else if (kind == ICON_TIMER) {
                p.setColor(ACCENT_TEAL);
                p.setStrokeWidth(2.0f * d);
                c.drawCircle(cx, cy + s * 0.03f, s * 0.23f, p);
                c.drawLine(cx, cy + s * 0.03f, cx, cy - s * 0.12f, p);
                c.drawLine(cx, cy + s * 0.03f, cx + s * 0.11f, cy + s * 0.08f, p);
                c.drawLine(cx - s * 0.08f, cy - s * 0.26f, cx + s * 0.08f, cy - s * 0.26f, p);
            } else if (kind == ICON_INPUTS) {
                p.setStrokeWidth(2.0f * d);
                rf.set(cx - s * 0.42f, cy - s * 0.26f, cx + s * 0.42f, cy + s * 0.26f);
                c.drawRoundRect(rf, s * 0.24f, s * 0.24f, p);
                c.drawLine(cx - s * 0.25f, cy, cx - s * 0.09f, cy, p);
                c.drawLine(cx - s * 0.17f, cy - s * 0.08f, cx - s * 0.17f, cy + s * 0.08f, p);
                c.drawCircle(cx + s * 0.14f, cy - s * 0.05f, 1.4f * d, f);
                c.drawCircle(cx + s * 0.25f, cy + s * 0.05f, 1.4f * d, f);
            } else if (kind == ICON_A11Y) {
                p.setStrokeWidth(1.9f * d);
                c.drawCircle(cx, cy, s * 0.4f, p);
                c.drawCircle(cx, cy - s * 0.16f, s * 0.07f, f);
                c.drawLine(cx - s * 0.2f, cy - s * 0.03f, cx + s * 0.2f, cy - s * 0.03f, p);
                c.drawLine(cx, cy - s * 0.03f, cx - s * 0.12f, cy + s * 0.24f, p);
                c.drawLine(cx, cy - s * 0.03f, cx + s * 0.12f, cy + s * 0.24f, p);
            } else if (kind == ICON_LOCK) {
                p.setStrokeWidth(2.0f * d);
                rf.set(cx - s * 0.2f, cy - s * 0.38f, cx + s * 0.2f, cy + s * 0.04f);
                c.drawArc(rf, 180f, 180f, false, p);
                rf.set(cx - s * 0.28f, cy - s * 0.08f, cx + s * 0.28f, cy + s * 0.36f);
                c.drawRoundRect(rf, 4f * d, 4f * d, f);
            } else if (kind == ICON_SEARCH) {
                p.setStrokeWidth(2.2f * d);
                c.drawCircle(cx - s * 0.08f, cy - s * 0.08f, s * 0.26f, p);
                c.drawLine(cx + s * 0.11f, cy + s * 0.11f, cx + s * 0.36f, cy + s * 0.36f, p);
            }
        }

        @Override public void setAlpha(int alpha) {}
        @Override public void setColorFilter(ColorFilter cf) {}
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
}
