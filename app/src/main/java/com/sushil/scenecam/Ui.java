package com.sushil.scenecam;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.content.res.ColorStateList;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Small helpers so screens can be built in code with one consistent look. */
final class Ui {
    private Ui() {}

    static final int BG = 0xFF0E1116;
    static final int CARD = 0xFF181D25;
    static final int CARD2 = 0xFF222834;
    static final int TEXT = 0xFFF2F4F7;
    static final int MUTED = 0xFF9AA4B2;
    static final int ACCENT = 0xFFFFB547;
    static final int LINE = 0xFF2A313D;

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("scenecam", Context.MODE_PRIVATE);
    }

    static Phone phone(Context c) {
        return Phone.byId(prefs(c).getString("phone", null));
    }

    static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    static GradientDrawable rounded(Context c, int color, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(c, radiusDp));
        return g;
    }

    static GradientDrawable gradient(Context c, int c1, int c2, float radiusDp) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{c1, c2});
        g.setCornerRadius(dp(c, radiusDp));
        return g;
    }

    /** Makes a view tappable with a ripple over its background. */
    static void clickable(View v, android.graphics.drawable.Drawable bg, View.OnClickListener l) {
        v.setBackground(new RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), bg, bg));
        v.setClickable(true);
        v.setFocusable(true);
        v.setOnClickListener(l);
    }

    static TextView text(Context c, String s, float sp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        t.setLineSpacing(0, 1.15f);
        return t;
    }

    static LinearLayout vbox(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    static LinearLayout hbox(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    static LinearLayout.LayoutParams match(Context c, float topDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(c, topDp);
        return lp;
    }

    /** A rounded card with a small caps heading. Returns the inner box to add content to. */
    static LinearLayout card(Activity a, LinearLayout parent, String heading) {
        LinearLayout card = vbox(a);
        card.setBackground(rounded(a, CARD, 18));
        int p = dp(a, 16);
        card.setPadding(p, dp(a, 14), p, dp(a, 14));
        if (heading != null) {
            TextView h = text(a, heading.toUpperCase(), 11.5f, ACCENT, true);
            h.setLetterSpacing(0.12f);
            card.addView(h);
        }
        parent.addView(card, match(a, 12));
        return card;
    }

    /** Two-option pill switch (PHOTO / VIDEO). */
    static LinearLayout segment(Activity a, String left, String right, boolean rightOn,
                                boolean leftEnabled, boolean rightEnabled, java.util.function.Consumer<Boolean> onPick) {
        LinearLayout seg = hbox(a);
        seg.setBackground(rounded(a, CARD2, 22));
        int pad = dp(a, 3);
        seg.setPadding(pad, pad, pad, pad);
        String[] names = {left, right};
        boolean[] enabled = {leftEnabled, rightEnabled};
        for (int i = 0; i < 2; i++) {
            boolean on = (i == 1) == rightOn;
            TextView t = text(a, names[i], 13, on ? 0xFF111111 : (enabled[i] ? TEXT : 0x55FFFFFF), true);
            t.setLetterSpacing(0.08f);
            t.setGravity(Gravity.CENTER);
            if (on) t.setBackground(rounded(a, ACCENT, 19));
            final boolean pickRight = i == 1;
            if (enabled[i] && !on) t.setOnClickListener(v -> onPick.accept(pickRight));
            seg.addView(t, new LinearLayout.LayoutParams(0, dp(a, 38), 1f));
        }
        return seg;
    }

    /** Android 15+ draws apps edge-to-edge; keep content clear of the status and navigation bars. */
    static void fitSystemBars(View v) {
        if (android.os.Build.VERSION.SDK_INT < 30) return;   // older Android doesn't draw behind the bars
        v.setOnApplyWindowInsetsListener((view, ins) -> {
            android.graphics.Insets b = ins.getInsets(android.view.WindowInsets.Type.systemBars()
                    | android.view.WindowInsets.Type.displayCutout());
            view.setPadding(b.left, b.top, b.right, b.bottom);
            return android.view.WindowInsets.CONSUMED;
        });
        v.requestApplyInsets();
    }

    static View divider(Context c) {
        View v = new View(c);
        v.setBackgroundColor(LINE);
        return v;
    }
}
