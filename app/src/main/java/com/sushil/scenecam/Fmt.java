package com.sushil.scenecam;

import android.hardware.camera2.CameraMetadata;

import java.util.Locale;

final class Fmt {
    private Fmt() {}

    static String shutter(long ns) {
        if (ns <= 0) return "auto";
        double s = ns / 1e9;
        if (s >= 0.95) {
            return (Math.abs(s - Math.round(s)) < 0.05)
                    ? Math.round(s) + "s"
                    : String.format(Locale.US, "%.1fs", s);
        }
        return "1/" + Math.round(1.0 / s) + "s";
    }

    static String ev(float ev) {
        if (Math.abs(ev) < 0.05f) return "EV 0";
        return String.format(Locale.US, "EV %s%.1f", ev > 0 ? "+" : "−", Math.abs(ev));
    }

    static String zoom(float z) {
        if (Math.abs(z - Math.round(z)) < 0.05f) return Math.round(z) + "x";
        return String.format(Locale.US, "%.1fx", z);
    }

    static String awb(int mode) {
        switch (mode) {
            case CameraMetadata.CONTROL_AWB_MODE_DAYLIGHT: return "☀ Daylight";
            case CameraMetadata.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT: return "⛅ Warm";
            case CameraMetadata.CONTROL_AWB_MODE_INCANDESCENT: return "💡 Tungsten";
            case CameraMetadata.CONTROL_AWB_MODE_FLUORESCENT: return "Fluorescent";
            case CameraMetadata.CONTROL_AWB_MODE_SHADE: return "Shade";
            default: return "WB auto";
        }
    }

    /** One-line summary shown on the home screen. */
    static String summary(Preset p) {
        StringBuilder sb = new StringBuilder();
        if (p.photo) {
            sb.append("📷 ");
            if (p.photoExp == Preset.EXP_MANUAL) {
                sb.append("ISO low · up to ").append(shutter(p.shutterNs));
            } else if (p.photoExp == Preset.EXP_SHUTTER) {
                sb.append(shutter(p.shutterNs));
            } else {
                sb.append("Auto");
            }
            sb.append(" · ").append(ev(p.ev));
            if (p.awb != CameraMetadata.CONTROL_AWB_MODE_AUTO) sb.append(" · ").append(awb(p.awb));
            if (Math.abs(p.zoom - 1f) > 0.01f) sb.append(" · ").append(zoom(p.zoom));
            if (p.timerSec > 0) sb.append(" · ⏱").append(p.timerSec).append("s");
        }
        if (p.video) {
            if (sb.length() > 0) sb.append("\n");
            sb.append("🎥 ").append(p.fps).append(" fps");
            if (p.vidExp == Preset.VID_CINE) sb.append(" · 1/").append(p.fps * 2).append("s cinema");
            if (p.stabilize) sb.append(" · stabilised");
        }
        if (p.frontCamera) sb.append(" · front cam");
        return sb.toString();
    }

    static String iso(int iso) {
        return "ISO " + iso;
    }
}
