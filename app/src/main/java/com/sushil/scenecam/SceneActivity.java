package com.sushil.scenecam;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

/** One scene: the settings, the steps for your phone, why, and tips. */
public class SceneActivity extends Activity {

    public static final String EXTRA_SCENE = "scene";
    public static final String EXTRA_VIDEO = "video";

    private Scene scene;
    private Phone phone;
    private boolean video;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        scene = Scene.byId(getIntent().getStringExtra(EXTRA_SCENE));
        phone = Ui.phone(this);
        if (phone == null) phone = Phone.all().get(0);
        video = getIntent().getBooleanExtra(EXTRA_VIDEO, false);
        if (video && !scene.video) video = false;
        if (!video && !scene.photo) video = true;
        render();
    }

    private void render() {
        FrameLayout frame = new FrameLayout(this);
        frame.setBackgroundColor(Ui.BG);

        ScrollView scroll = new ScrollView(this);
        LinearLayout root = Ui.vbox(this);
        int p = Ui.dp(this, 16);
        root.setPadding(p, Ui.dp(this, 14), p, Ui.dp(this, 110));
        scroll.addView(root);
        frame.addView(scroll);

        // Hero
        LinearLayout hero = Ui.vbox(this);
        hero.setBackground(Ui.gradient(this, scene.color1, scene.color2, 22));
        hero.setPadding(Ui.dp(this, 18), Ui.dp(this, 14), Ui.dp(this, 18), Ui.dp(this, 18));
        TextView back = Ui.text(this, "‹  Scenes", 14, 0xEEFFFFFF, true);
        back.setPadding(0, 0, 0, Ui.dp(this, 8));
        back.setOnClickListener(v -> finish());
        hero.addView(back);
        hero.addView(Ui.text(this, scene.emoji, 44, 0xFFFFFFFF, false));
        hero.addView(Ui.text(this, scene.name, 24, 0xFFFFFFFF, true));
        hero.addView(Ui.text(this, scene.shortDesc, 14, 0xE6FFFFFF, false));
        TextView chip = Ui.text(this, "📱 " + phone.fullName() + "   ·   " + (video ? "🎥 Video" : "📷 Photo"), 12, 0xFFFFFFFF, true);
        chip.setBackground(Ui.rounded(this, 0x40000000, 14));
        chip.setPadding(Ui.dp(this, 10), Ui.dp(this, 5), Ui.dp(this, 10), Ui.dp(this, 5));
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        clp.topMargin = Ui.dp(this, 12);
        hero.addView(chip, clp);
        root.addView(hero);

        // Photo / Video switch
        if (scene.photo && scene.video) {
            root.addView(Ui.segment(this, "📷  PHOTO", "🎥  VIDEO", video, true, true, right -> {
                video = right;
                render();
            }), Ui.match(this, 12));
        }

        // Settings table
        LinearLayout settings = Ui.card(this, root, "Settings to use");
        List<Guide.Row> rows = Guide.rows(scene, phone, video);
        for (int i = 0; i < rows.size(); i++) {
            Guide.Row r = rows.get(i);
            if (i > 0) settings.addView(Ui.divider(this), new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1));
            LinearLayout row = Ui.vbox(this);
            row.setPadding(0, Ui.dp(this, 9), 0, Ui.dp(this, 9));
            LinearLayout line = Ui.hbox(this);
            line.addView(Ui.text(this, r.label, 14, Ui.MUTED, false), new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            TextView val = Ui.text(this, r.value, 15, Ui.TEXT, true);
            val.setGravity(Gravity.END);
            line.addView(val, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.3f));
            row.addView(line);
            if (!r.note.isEmpty()) {
                TextView n = Ui.text(this, r.note, 12, Ui.NOTE, false);
                n.setGravity(Gravity.END);
                row.addView(n);
            }
            settings.addView(row);
        }
        if ((video && scene.videoMode == Scene.V_PRO && !phone.videoPro) || (!video && (scene.photoMode == Scene.P_PRO) && !phone.photoPro)) {
            TextView warn = Ui.text(this, "ℹ️  Your phone's camera picks ISO and shutter by itself here. The steps below get you the same look.", 12.5f, Ui.MUTED, false);
            warn.setBackground(Ui.rounded(this, Ui.CARD2, 12));
            warn.setPadding(Ui.dp(this, 12), Ui.dp(this, 10), Ui.dp(this, 12), Ui.dp(this, 10));
            settings.addView(warn, Ui.match(this, 8));
        }

        // Steps
        LinearLayout steps = Ui.card(this, root, "Do this on your " + phone.series);
        List<String> list = Guide.steps(scene, phone, video);
        for (int i = 0; i < list.size(); i++) {
            LinearLayout line = Ui.hbox(this);
            line.setGravity(Gravity.TOP);
            line.setPadding(0, Ui.dp(this, 9), 0, 0);
            TextView num = Ui.text(this, String.valueOf(i + 1), 13, 0xFFFFFFFF, true);
            num.setGravity(Gravity.CENTER);
            num.setBackground(Ui.brand(this, 12));
            line.addView(num, new LinearLayout.LayoutParams(Ui.dp(this, 24), Ui.dp(this, 24)));
            TextView t = Ui.text(this, list.get(i), 14.5f, Ui.TEXT, false);
            t.setPadding(Ui.dp(this, 12), Ui.dp(this, 1), 0, 0);
            line.addView(t, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            steps.addView(line);
        }

        // Why + tips
        LinearLayout why = Ui.card(this, root, "Why these settings");
        why.addView(Ui.text(this, video ? scene.videoWhy : scene.photoWhy, 14.5f, Ui.TEXT, false), Ui.match(this, 8));
        LinearLayout tips = Ui.card(this, root, "Pro tips");
        tips.addView(Ui.text(this, "💡 " + (video ? scene.videoTips : scene.photoTips), 14.5f, Ui.TEXT, false), Ui.match(this, 8));

        // Bottom button
        TextView open = Ui.text(this, phone.iphone ? "These steps are for an iPhone" : (video ? "🎥  Open Camera (video)" : "📷  Open Camera"), 16, 0xFFFFFFFF, true);
        open.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, Ui.dp(this, 56), Gravity.BOTTOM);
        blp.setMargins(p, 0, p, Ui.dp(this, 18));
        if (phone.iphone) {
            open.setBackground(Ui.rounded(this, Ui.CARD2, 28));
            open.setTextColor(Ui.MUTED);
        } else {
            Ui.clickable(open, Ui.brand(this, 28), v -> openCamera());
        }
        open.setElevation(Ui.dp(this, 6));
        frame.addView(open, blp);

        frame.setBackgroundColor(Ui.BG);
        Ui.fitSystemBars(frame);
        setContentView(frame);
    }

    private void openCamera() {
        String action = video ? MediaStore.INTENT_ACTION_VIDEO_CAMERA : MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA;
        try {
            startActivity(new Intent(action));
        } catch (Exception e) {
            try {
                startActivity(new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA));
            } catch (Exception e2) {
                Toast.makeText(this, "Couldn't find a camera app on this phone.", Toast.LENGTH_LONG).show();
            }
        }
    }
}
