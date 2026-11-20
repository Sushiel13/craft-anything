package com.sushil.scenecam;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.os.Bundle;
import android.util.Range;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ((TextView) findViewById(R.id.capability)).setText(describeCamera());

        LinearLayout list = findViewById(R.id.list);
        LayoutInflater inf = LayoutInflater.from(this);
        for (final Preset p : Preset.all()) {
            View item = inf.inflate(R.layout.item_preset, list, false);
            ((TextView) item.findViewById(R.id.emoji)).setText(p.emoji);
            ((TextView) item.findViewById(R.id.name)).setText(p.name);
            ((TextView) item.findViewById(R.id.subtitle)).setText(p.subtitle);
            ((TextView) item.findViewById(R.id.settings)).setText(Fmt.summary(p));
            item.setOnClickListener(v -> {
                Intent i = new Intent(this, CameraActivity.class);
                i.putExtra(CameraActivity.EXTRA_PRESET, p.id);
                startActivity(i);
            });
            list.addView(item);
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
                    sb.append("✅ Your phone allows full manual control.\n")
                      .append("ISO ").append(iso.getLower()).append("–").append(iso.getUpper())
                      .append(" · shutter ").append(Fmt.shutter(exp.getLower()))
                      .append(" to ").append(Fmt.shutter(exp.getUpper()));
                } else {
                    sb.append("ℹ️ Your phone limits manual shutter/ISO for other apps. ")
                      .append("SceneCam will use smart auto + exposure, colour, focus and zoom tuning for each scene.");
                }
                sb.append("\nZoom ").append(Fmt.zoom(zoom.getLower())).append("–").append(Fmt.zoom(zoom.getUpper()));
                return sb.toString();
            }
        } catch (Exception ignored) { }
        return "Pick a scene below to start.";
    }
}
