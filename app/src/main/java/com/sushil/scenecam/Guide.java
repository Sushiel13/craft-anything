package com.sushil.scenecam;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Turns a scene + a phone into a settings table and step-by-step instructions. */
final class Guide {
    private Guide() {}

    static final class Row {
        final String label, value, note;
        Row(String label, String value, String note) { this.label = label; this.value = value; this.note = note; }
    }

    // ------------------------------------------------------------------ helpers

    static String lensName(float z) {
        if (z < 0.9f) return "0.5x ultra-wide";
        if (z < 1.5f) return "1x main";
        return fmtZoom(z) + " zoom";
    }

    static String fmtZoom(float z) {
        return Math.abs(z - Math.round(z)) < 0.05f ? Math.round(z) + "x" : String.format(Locale.US, "%.1fx", z);
    }

    static String cap(String t) {
        return t.isEmpty() ? t : Character.toUpperCase(t.charAt(0)) + t.substring(1);
    }

    static String wb(int k) {
        return k <= 0 ? "Auto" : k + "K";
    }

    static String wbWords(int k) {
        if (k <= 0) return "Auto";
        if (k <= 3500) return k + "K (warm indoor bulbs)";
        if (k <= 4500) return k + "K (street / LED lights)";
        if (k <= 5800) return k + "K (daylight)";
        return k + "K (warm, golden)";
    }

    /** What lens the phone can really use for this zoom. */
    static String lensNote(Phone p, float z) {
        if (z < 0.9f && !p.ultraWide) return "Your phone has no ultra-wide — step back or use Panorama.";
        if (z >= 1.5f) {
            if (p.tele <= 0) return "No zoom lens on this phone, so " + fmtZoom(z) + " is a crop of the main camera — fine at 2x, avoid going past 3x.";
            if (Math.abs(p.tele - z) > 0.3f && (p.tele2 <= 0 || Math.abs(p.tele2 - z) > 0.3f)) {
                return "Your optical zoom lens is " + fmtZoom(p.tele) + (p.tele2 > 0 ? " / " + fmtZoom(p.tele2) : "") + " — use the closest button.";
            }
        }
        return "";
    }

    static String modeName(Scene s, Phone p, boolean video) {
        if (video) {
            switch (s.videoMode) {
                case Scene.V_PRO: return p.videoPro ? "PRO VIDEO" : "VIDEO";
                case Scene.V_CINEMATIC: return p.iphone ? "CINEMATIC" : "PORTRAIT VIDEO / CINEMATIC";
                case Scene.V_ACTION: return p.iphone ? "VIDEO + ACTION MODE" : "VIDEO + SUPER STEADY";
                default: return "VIDEO";
            }
        }
        switch (s.photoMode) {
            case Scene.P_PRO: return p.photoPro ? "PRO" : "PHOTO";
            case Scene.P_NIGHT: return "NIGHT";
            case Scene.P_LONG: return p.photoPro ? "PRO (long exposure)" : "LONG EXPOSURE";
            case Scene.P_PORTRAIT: return "PORTRAIT";
            default: return "PHOTO";
        }
    }

    /** Does this scene ask for manual ISO/shutter that the phone can actually set? */
    static boolean manualPhoto(Scene s, Phone p) {
        return p.photoPro && (s.photoMode == Scene.P_PRO || s.photoMode == Scene.P_LONG);
    }

    static boolean manualVideo(Scene s, Phone p) {
        return p.videoPro && s.videoMode == Scene.V_PRO;
    }

    // ------------------------------------------------------------------ settings table

    static List<Row> rows(Scene s, Phone p, boolean video) {
        List<Row> r = new ArrayList<>();
        if (!video) {
            boolean manual = manualPhoto(s, p);
            r.add(new Row("Mode", modeName(s, p, false), ""));
            r.add(new Row("Camera", s.front ? "Front (selfie)" : lensName(s.lens), s.front ? "" : lensNote(p, s.lens)));
            r.add(new Row("ISO", manual ? s.iso : "Auto", manual ? "" : "Set automatically on this phone"));
            r.add(new Row("Shutter speed", manual ? s.shutter : "Auto", manual ? "" : s.photoMode == Scene.P_LONG ? "Set by Long Exposure" : "Set automatically on this phone"));
            r.add(new Row("Exposure (EV)", s.ev, ""));
            r.add(new Row("White balance", manual ? wbWords(s.wbK) : (s.wbK > 0 && !p.iphone ? "Auto (or " + wb(s.wbK) + " in Pro)" : "Auto"), ""));
            r.add(new Row("Focus", s.focusInfinity && manual ? "Manual → ∞ (infinity)" : "Tap on the subject", ""));
            if (s.timer > 0) r.add(new Row("Timer", s.timer + " seconds", "So your tap doesn't shake the phone"));
            if (s.tripod) r.add(new Row("Support", "Tripod / steady surface", ""));
            if (s.burst) r.add(new Row("Shooting", "Burst", ""));
            if (s.raw && !p.rawHow.isEmpty()) r.add(new Row("Format", "RAW (+ JPEG)", "More detail to edit later"));
            r.add(new Row("Flash", "Off", ""));
        } else {
            boolean manual = manualVideo(s, p);
            r.add(new Row("Mode", modeName(s, p, true), ""));
            r.add(new Row("Camera", s.front ? "Front (selfie)" : lensName(s.videoLens()), s.front ? "" : lensNote(p, s.videoLens())));
            r.add(new Row("Resolution", s.res, ""));
            r.add(new Row("Frame rate", s.fps + " fps", s.fps == 25 ? "Use 30 if your phone has no 25" : ""));
            if (s.videoMode == Scene.V_PRO) {
                r.add(new Row("Shutter speed", manual ? s.vShutter : "Auto", manual ? "Rule: 1 ÷ (2 × fps)" : "Not adjustable on this phone"));
                r.add(new Row("ISO", manual ? s.vIso : "Auto", ""));
            }
            r.add(new Row("Exposure (EV)", s.videoEv(), ""));
            int k = s.videoWbK();
            r.add(new Row("White balance", manual && k > 0 ? wbWords(k) : p.iphone && s.lockExposure ? "Lock White Balance" : "Auto", ""));
            r.add(new Row("Stabilisation", s.stab, ""));
            if (s.lockExposure) r.add(new Row("Exposure lock", "On (AE/AF lock)", "Stops brightness jumping"));
        }
        return r;
    }

    // ------------------------------------------------------------------ steps

    static List<String> steps(Scene s, Phone p, boolean video) {
        List<String> st = new ArrayList<>();
        if (video) videoSteps(s, p, st); else photoSteps(s, p, st);
        return st;
    }

    private static void photoSteps(Scene s, Phone p, List<String> st) {
        boolean manual = manualPhoto(s, p);
        st.add("Open your Camera app" + (s.front ? " and switch to the front camera." : "."));

        switch (s.photoMode) {
            case Scene.P_PRO:
                if (p.photoPro) st.add(p.photoProHow);
                else st.add("Stay in PHOTO mode — this phone's camera doesn't let you set ISO and shutter by hand, so the steps below get you as close as possible.");
                break;
            case Scene.P_LONG:
                if (p.photoPro) st.add(p.photoProHow + " (long exposure is done in Pro).");
                else st.add(p.longExpoHow);
                break;
            case Scene.P_NIGHT:
                st.add(p.nightHow);
                break;
            case Scene.P_PORTRAIT:
                st.add(p.portraitHow);
                break;
            default:
                st.add("Use the normal PHOTO mode.");
        }

        if (!s.front) {
            String note = lensNote(p, s.lens);
            st.add("Tap " + fmtZoom(s.lens) + " on the zoom bar (" + lensName(s.lens) + ")." + (note.isEmpty() ? "" : " " + note));
        }

        if (manual) {
            st.add("Set ISO to " + s.iso + ".");
            st.add("Set shutter speed (S) to " + s.shutter + ".");
            st.add(s.wbK > 0 ? "Set WB to " + wbWords(s.wbK) + "." : "Leave WB on Auto.");
            if (!s.ev.startsWith("0")) st.add("Set EV (exposure) to " + s.ev + ". If ISO and shutter are both fixed, EV may be greyed out — that's normal.");
            st.add(s.focusInfinity ? "Set focus to manual (MF) and slide it all the way to ∞ / the mountain icon." : "Leave focus on AF and tap the subject.");
        } else {
            st.add("Tap the subject to focus.");
            if (!s.ev.startsWith("0")) st.add("Brightness: " + cap(p.evHow) + " to about " + s.ev + ".");
            if (s.wbK > 0) st.add("Colour: " + p.wbHow);
            if (s.photoMode == Scene.P_PRO && (s.shutter.contains("1000") || s.shutter.contains("500"))) {
                st.add("For moving subjects, use " + (p.burstHow.isEmpty() ? "burst" : "burst: " + p.burstHow) + " — the phone picks a faster shutter in bright light.");
            }
        }

        if (s.timer > 0) st.add("Set a " + s.timer + "-second timer: " + p.timerHow);
        if (s.burst && manual) st.add("Burst: " + p.burstHow);
        if (s.raw && !p.rawHow.isEmpty()) st.add("Optional — shoot RAW for editing: " + p.rawHow);
        st.add("Turn on the grid to line things up: " + p.gridHow);
        st.add(s.tripod ? "Put the phone on the tripod, press the shutter, and don't touch it until the photo is saved."
                        : "Hold the phone with both hands, press the shutter, and stay still for a moment.");
    }

    private static void videoSteps(Scene s, Phone p, List<String> st) {
        boolean manual = manualVideo(s, p);
        st.add("Open your Camera app" + (s.front ? " and switch to the front camera." : "."));

        switch (s.videoMode) {
            case Scene.V_PRO:
                if (p.videoPro) st.add(p.videoProHow);
                else st.add("Use VIDEO mode — this phone doesn't let you set the video shutter by hand.");
                break;
            case Scene.V_CINEMATIC:
                st.add(p.portraitVideoHow);
                break;
            case Scene.V_ACTION:
                st.add("Swipe to VIDEO.");
                st.add(p.stabHow);
                break;
            default:
                st.add("Swipe to VIDEO.");
        }

        st.add("Set " + s.res + " at " + s.fps + " fps: " + p.resHow);
        if (s.fps == 25) st.add("25 fps: " + p.fps25How);
        if (s.videoMode == Scene.V_ACTION && p.iphone) st.add("Action mode lowers the resolution; that's expected for very shaky shots.");

        if (!s.front) {
            float z = s.videoLens();
            String note = lensNote(p, z);
            st.add("Tap " + fmtZoom(z) + " on the zoom bar." + (note.isEmpty() ? "" : " " + note));
        }

        if (manual) {
            st.add("Set shutter speed to " + s.vShutter + " (rule: 1 ÷ (2 × fps)).");
            st.add("Set ISO to " + s.vIso + " — if the picture is too bright even at the lowest ISO, use a faster shutter or an ND filter.");
            int k = s.videoWbK();
            st.add(k > 0 ? "Set WB to " + wbWords(k) + " so colours don't shift." : "Leave WB on Auto.");
        } else if (s.videoMode == Scene.V_PRO && p.iphone) {
            st.add("Lock white balance so colours don't shift: Settings → Camera → Record Video → Lock White Balance.");
        }

        String ev = s.videoEv();
        if (!ev.startsWith("0")) st.add("Brightness: " + cap(p.evHow) + " to about " + ev + ".");
        if (s.lockExposure) st.add("Lock exposure and focus: " + p.lockHow);
        if (s.videoMode != Scene.V_ACTION && s.stab.equals("On")) st.add("Make sure video stabilisation is on (it usually is by default).");
        st.add("Hold the phone with both hands, elbows tucked in, and move slowly. Record 5–10 seconds per shot.");
    }
}
