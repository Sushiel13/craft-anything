package com.sushil.scenecam;

import android.app.Activity;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** "Which phone do you have?" — brand groups with series cards. */
public class PhoneActivity extends Activity {

    public static final String EXTRA_FIRST = "first";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        boolean first = getIntent().getBooleanExtra(EXTRA_FIRST, false);
        Phone current = Ui.phone(this);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Ui.BG);
        LinearLayout root = Ui.vbox(this);
        int p = Ui.dp(this, 16);
        root.setPadding(p, Ui.dp(this, 22), p, Ui.dp(this, 28));
        scroll.addView(root);

        if (!first) {
            TextView back = Ui.text(this, "‹  Back", 15, Ui.ACCENT, true);
            back.setPadding(0, 0, 0, Ui.dp(this, 10));
            back.setOnClickListener(v -> finish());
            root.addView(back);
        }
        android.widget.ImageView logo = new android.widget.ImageView(this);
        logo.setImageResource(R.mipmap.ic_launcher);
        if (first) root.addView(logo, new LinearLayout.LayoutParams(Ui.dp(this, 64), Ui.dp(this, 64)));
        root.addView(Ui.text(this, first ? "Welcome to SceneCam 👋" : "Change phone", 26, Ui.TEXT, true), Ui.match(this, first ? 12 : 0));
        root.addView(Ui.text(this, "Which phone do you have? Every camera app hides its settings in different places — "
                + "we'll show the steps for yours.", 14, Ui.MUTED, false), Ui.match(this, 6));

        String lastBrand = "";
        for (Phone ph : Phone.all()) {
            if (!ph.brand.equals(lastBrand)) {
                lastBrand = ph.brand;
                TextView h = Ui.text(this, ph.brand.toUpperCase(), 12, Ui.ACCENT, true);
                h.setLetterSpacing(0.12f);
                root.addView(h, Ui.match(this, 22));
            }
            boolean on = current != null && current.id.equals(ph.id);
            LinearLayout card = Ui.hbox(this);
            card.setPadding(p, Ui.dp(this, 14), p, Ui.dp(this, 14));
            LinearLayout txt = Ui.vbox(this);
            txt.addView(Ui.text(this, ph.series, 16, Ui.TEXT, true));
            txt.addView(Ui.text(this, ph.models, 12.5f, Ui.MUTED, false));
            card.addView(txt, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            card.addView(Ui.text(this, on ? "✓" : "›", 20, on ? Ui.ACCENT : Ui.MUTED, true));

            card.setElevation(Ui.dp(this, 1.5f));
            android.graphics.drawable.GradientDrawable bg = Ui.rounded(this, Ui.CARD, 18);
            if (on) bg.setStroke(Ui.dp(this, 2), Ui.ACCENT);
            Ui.clickable(card, bg, v -> {
                Ui.prefs(this).edit().putString("phone", ph.id).apply();
                setResult(RESULT_OK);
                finish();
            });
            root.addView(card, Ui.match(this, 8));
        }

        TextView note = Ui.text(this, "Not sure? Open Settings → About phone to see your model name.", 12, Ui.MUTED, false);
        root.addView(note, Ui.match(this, 20));
        scroll.setBackgroundColor(Ui.BG);
        Ui.fitSystemBars(scroll);
        setContentView(scroll);
    }
}
