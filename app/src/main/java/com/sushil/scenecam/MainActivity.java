package com.sushil.scenecam;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** Home: your phone, Photo/Video switch, and the grid of scenes. */
public class MainActivity extends Activity {

    private static final int REQ_PHONE = 1;
    private boolean video;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        video = Ui.prefs(this).getBoolean("video", false);
        if (Ui.phone(this) == null) {
            startActivityForResult(new Intent(this, PhoneActivity.class).putExtra(PhoneActivity.EXTRA_FIRST, true), REQ_PHONE);
        }
        render();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_PHONE) {
            if (Ui.phone(this) == null) { finish(); return; }
            render();
        }
    }

    private void render() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Ui.BG);
        scroll.setFillViewport(true);
        LinearLayout root = Ui.vbox(this);
        int p = Ui.dp(this, 16);
        root.setPadding(p, Ui.dp(this, 22), p, Ui.dp(this, 28));
        scroll.addView(root);

        // Header
        LinearLayout brandRow = Ui.hbox(this);
        android.widget.ImageView logo = new android.widget.ImageView(this);
        logo.setImageResource(R.mipmap.ic_launcher);
        brandRow.addView(logo, new LinearLayout.LayoutParams(Ui.dp(this, 44), Ui.dp(this, 44)));
        TextView title = Ui.text(this, "SceneCam", 30, Ui.ACCENT, true);
        title.setPadding(Ui.dp(this, 10), 0, 0, 0);
        Ui.gradientText(title);
        brandRow.addView(title);
        root.addView(brandRow);
        root.addView(Ui.text(this, "Pro camera settings for every scene — made simple.", 14, Ui.MUTED, false));

        // Phone card
        Phone ph = Ui.phone(this);
        LinearLayout phoneCard = Ui.hbox(this);
        phoneCard.setPadding(p, Ui.dp(this, 14), p, Ui.dp(this, 14));
        TextView icon = Ui.text(this, "📱", 26, Ui.TEXT, false);
        phoneCard.addView(icon);
        LinearLayout info = Ui.vbox(this);
        info.setPadding(Ui.dp(this, 12), 0, 0, 0);
        info.addView(Ui.text(this, "YOUR PHONE", 10.5f, Ui.MUTED, true));
        info.addView(Ui.text(this, ph == null ? "Not set" : ph.fullName(), 16, Ui.TEXT, true));
        if (ph != null) info.addView(Ui.text(this, capabilityLine(ph), 12, Ui.MUTED, false));
        phoneCard.addView(info, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        phoneCard.addView(Ui.text(this, "Change ›", 13, Ui.ACCENT, true));
        phoneCard.setElevation(Ui.dp(this, 1.5f));
        Ui.clickable(phoneCard, Ui.rounded(this, Ui.CARD, 20),
                v -> startActivityForResult(new Intent(this, PhoneActivity.class), REQ_PHONE));
        root.addView(phoneCard, Ui.match(this, 18));

        // Photo / Video
        LinearLayout seg = Ui.segment(this, "📷  PHOTO", "🎥  VIDEO", video, true, true, right -> {
            video = right;
            Ui.prefs(this).edit().putBoolean("video", video).apply();
            render();
        });
        root.addView(seg, Ui.match(this, 18));

        TextView pick = Ui.text(this, video ? "What are you filming?" : "What are you shooting?", 18, Ui.TEXT, true);
        root.addView(pick, Ui.match(this, 22));

        // Scene grid (2 columns)
        List<Scene> scenes = new ArrayList<>();
        for (Scene s : Scene.all()) if (video ? s.video : s.photo) scenes.add(s);
        LinearLayout row = null;
        for (int i = 0; i < scenes.size(); i++) {
            if (i % 2 == 0) {
                row = Ui.hbox(this);
                root.addView(row, Ui.match(this, 10));
            }
            Scene s = scenes.get(i);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Ui.dp(this, 136), 1f);
            if (i % 2 == 0) lp.rightMargin = Ui.dp(this, 5); else lp.leftMargin = Ui.dp(this, 5);
            row.addView(tile(s), lp);
        }
        if (scenes.size() % 2 == 1 && row != null) {
            row.addView(new android.view.View(this), new LinearLayout.LayoutParams(0, 1, 1f));
        }

        TextView foot = Ui.text(this, "Tip: these settings work in your phone's own Camera app — open the scene, then tap “Open Camera”.", 12, Ui.MUTED, false);
        foot.setGravity(Gravity.CENTER);
        root.addView(foot, Ui.match(this, 20));

        scroll.setBackgroundColor(Ui.BG);
        Ui.fitSystemBars(scroll);
        setContentView(scroll);
    }

    private String capabilityLine(Phone ph) {
        StringBuilder sb = new StringBuilder();
        sb.append(ph.photoPro ? "Pro photo ✓" : "Auto photo");
        sb.append(" · ").append(ph.videoPro ? "Pro video ✓" : "Auto video");
        if (ph.tele > 0) sb.append(" · ").append(Guide.fmtZoom(ph.tele)).append(ph.tele2 > 0 ? "/" + Guide.fmtZoom(ph.tele2) : "").append(" zoom");
        return sb.toString();
    }

    private FrameLayout tile(Scene s) {
        FrameLayout f = new FrameLayout(this);
        int pad = Ui.dp(this, 14);
        f.setPadding(pad, pad, pad, pad);
        f.setElevation(Ui.dp(this, 2));
        Ui.clickable(f, Ui.gradient(this, s.color1, s.color2, 22), v -> {
            Intent i = new Intent(this, SceneActivity.class);
            i.putExtra(SceneActivity.EXTRA_SCENE, s.id);
            i.putExtra(SceneActivity.EXTRA_VIDEO, video);
            startActivity(i);
        });

        TextView emoji = Ui.text(this, s.emoji, 30, Ui.TEXT, false);
        f.addView(emoji, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.START));

        LinearLayout bottom = Ui.vbox(this);
        bottom.addView(Ui.text(this, s.name, 15, 0xFFFFFFFF, true));
        TextView quick = Ui.text(this, quickLine(s), 11, 0xEEFFFFFF, false);
        quick.setMaxLines(1);
        quick.setEllipsize(android.text.TextUtils.TruncateAt.END);
        bottom.addView(quick);
        f.addView(bottom, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM));
        return f;
    }

    private String quickLine(Scene s) {
        if (video) {
            String m = s.videoMode == Scene.V_PRO ? " · 1/" + (s.fps * 2) + "s" : s.videoMode == Scene.V_CINEMATIC ? " · blur" : s.videoMode == Scene.V_ACTION ? " · steady" : "";
            return s.res + " " + s.fps + "fps" + m;
        }
        StringBuilder sb = new StringBuilder();
        sb.append(s.shutter.equals("Auto") ? "Auto" : s.shutter.replace(" s", "s"));
        if (!s.ev.startsWith("0")) sb.append(" · EV ").append(s.ev);
        if (Math.abs(s.lens - 1f) > 0.01f) sb.append(" · ").append(Guide.fmtZoom(s.lens));
        return sb.toString();
    }
}
