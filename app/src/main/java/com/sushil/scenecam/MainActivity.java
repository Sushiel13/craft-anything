package com.sushil.scenecam;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.os.Bundle;
import android.util.Range;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

/** Scene gallery: a grid of scene tiles. Picking one returns it to the camera. */
public class MainActivity extends Activity {

    public static final String EXTRA_CURRENT = "current";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        findViewById(R.id.back).setOnClickListener(v -> finish());
        ((TextView) findViewById(R.id.capability)).setText(describeCamera());

        String current = getIntent().getStringExtra(EXTRA_CURRENT);
        LinearLayout grid = findViewById(R.id.grid);
        LayoutInflater inf = LayoutInflater.from(this);
        float d = getResources().getDisplayMetrics().density;
        List<Preset> all = Preset.all();

        LinearLayout row = null;
        for (int i = 0; i < all.size(); i++) {
            if (i % 2 == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                rp.topMargin = (int) (10 * d);
                grid.addView(row, rp);
            }
            final Preset p = all.get(i);
            View tile = inf.inflate(R.layout.item_tile, row, false);

            GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                    new int[]{p.color1, p.color2});
            bg.setCornerRadius(20 * d);
            if (p.id.equals(current)) bg.setStroke((int) (2.5f * d), 0xFFFFB547);
            tile.setBackground(bg);
            tile.setClipToOutline(true);

            ((TextView) tile.findViewById(R.id.emoji)).setText(p.emoji);
            ((TextView) tile.findViewById(R.id.name)).setText(p.name);
            ((TextView) tile.findViewById(R.id.settings)).setText(Fmt.brief(p));
            String badge = p.photo && p.video ? "PHOTO · VIDEO" : p.photo ? "PHOTO" : "VIDEO";
            if (p.timerSec > 0) badge = "TRIPOD";
            ((TextView) tile.findViewById(R.id.badge)).setText(badge);

            tile.setOnClickListener(v -> {
                Intent r = new Intent();
                r.putExtra(CameraActivity.EXTRA_PRESET, p.id);
                setResult(RESULT_OK, r);
                finish();
            });

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, (int) (148 * d), 1f);
            if (i % 2 == 0) lp.rightMargin = (int) (5 * d); else lp.leftMargin = (int) (5 * d);
            row.addView(tile, lp);
        }
        if (all.size() % 2 == 1 && row != null) {   // keep the last tile half-width
            row.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));
        }
    }

    /** Tells the user how much control their phone gives third-party apps. */
    private String describeCamera() {
        try {
            CameraManager cm = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
            for (String id : cm.getCameraIdList()) {
                CameraCharacteristics c = cm.getCameraCharacteristics(id);
                Integer facing = c.get(CameraCharacteristics.LENS_FACING);
                if (facing == null || facing != CameraMetadata.LENS_FACING_BACK) continue;
                boolean manual = CamUtil.hasCap(c, CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR);
                Range<Long> exp = c.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE);
                Range<Integer> iso = c.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE);
                Range<Float> zoom = CamUtil.zoomRange(c);
                StringBuilder sb = new StringBuilder();
                if (manual && exp != null && iso != null) {
                    sb.append("✅  Full manual control on this phone\n")
                      .append("ISO ").append(iso.getLower()).append("–").append(iso.getUpper())
                      .append("  ·  Shutter ").append(Fmt.shutter(exp.getLower()))
                      .append(" – ").append(Fmt.shutter(exp.getUpper()));
                } else {
                    sb.append("ℹ️  This phone limits manual shutter/ISO for other apps. ")
                      .append("Scenes still set brightness, colour, focus, zoom and video options.");
                }
                sb.append("  ·  Zoom ").append(Fmt.zoom(zoom.getLower())).append("–").append(Fmt.zoom(zoom.getUpper()));
                return sb.toString();
            }
        } catch (Exception ignored) { }
        return "Pick a scene to start.";
    }
}
