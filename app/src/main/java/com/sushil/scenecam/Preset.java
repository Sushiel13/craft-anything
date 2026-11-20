package com.sushil.scenecam;

import android.hardware.camera2.CameraMetadata;

import java.util.ArrayList;
import java.util.List;

/**
 * One shooting scene: everything the camera should do for it.
 *
 * Exposure styles:
 *  AUTO    – camera meters the scene, we only shift brightness (EV).
 *  SHUTTER – we fix the shutter speed, ISO is worked out from the scene's light
 *            (like "shutter priority" on a pro camera).
 *  MANUAL  – fixed ISO and fixed shutter (light trails, star / long exposures).
 */
public class Preset {

    public static final int EXP_AUTO = 0;
    public static final int EXP_SHUTTER = 1;
    public static final int EXP_MANUAL = 2;

    public static final int VID_AUTO = 0;   // camera adapts exposure while filming
    public static final int VID_CINE = 1;   // 180° rule: shutter = 1 / (2 × fps), exposure locked

    public static final int FOCUS_AUTO = 0;
    public static final int FOCUS_INFINITY = 1;

    public final String id;
    public final String emoji;
    public final String name;
    public final String subtitle;
    public final String why;     // explains the settings in plain words
    public final String tips;    // how to shoot it

    // Photo
    public boolean photo = true;
    public int photoExp = EXP_AUTO;
    public long shutterNs = 0;      // for SHUTTER / MANUAL
    public int iso = 0;             // for MANUAL (0 = lowest the phone has)
    public int maxIso = 3200;       // cap when ISO is worked out automatically
    public float ev = 0f;           // brightness shift in stops
    public int awb = CameraMetadata.CONTROL_AWB_MODE_AUTO;
    public float zoom = 1f;
    public int focus = FOCUS_AUTO;
    public int timerSec = 0;
    public boolean highQuality = true;

    // Video
    public boolean video = true;
    public int fps = 30;
    public int vidExp = VID_AUTO;
    public boolean stabilize = true;
    public float videoEv = Float.NaN; // NaN = same as photo EV

    public boolean frontCamera = false;
    public boolean startInVideo = false;

    // Look
    public String shortName = "";
    public int color1 = 0xFF2B3240, color2 = 0xFF151A22;

    Preset(String id, String emoji, String name, String subtitle, String why, String tips) {
        this.id = id;
        this.emoji = emoji;
        this.name = name;
        this.subtitle = subtitle;
        this.why = why;
        this.tips = tips;
    }

    public float videoEv() {
        return Float.isNaN(videoEv) ? ev : videoEv;
    }

    static long sec(double s) {
        return (long) (s * 1_000_000_000L);
    }

    public static Preset byId(String id) {
        for (Preset p : all()) if (p.id.equals(id)) return p;
        return all().get(0);
    }

    private static List<Preset> cache;

    public static synchronized List<Preset> all() {
        if (cache != null) return cache;
        List<Preset> l = new ArrayList<>();
        Preset p;

        p = new Preset("auto", "✨", "Smart Auto",
                "Point and shoot, with pro clean-up",
                "Camera decides exposure and colour. We still turn on high-quality noise reduction, 50 Hz anti-flicker (Indian lighting) and stabilisation.",
                "Use this when nothing else fits. Tap the subject to focus.");
        l.add(p);

        p = new Preset("beach", "🏖️", "Beach & Bright Sun",
                "Sand, sea, harsh daylight",
                "Sand and water bounce a lot of light, so brightness is lowered (EV −0.7) to keep the sky and waves from turning white. Fast 1/1000 s shutter freezes waves and splashes. Daylight colour (≈5500K) stops the blue sea turning grey. Lowest ISO for the cleanest picture.",
                "Keep the sun behind you or to the side. Put the horizon on a grid line, not in the middle. For video, a clip-on ND filter gives smoother movie-like motion in strong sun.");
        p.photoExp = EXP_SHUTTER; p.shutterNs = sec(1.0 / 1000); p.maxIso = 400; p.ev = -0.7f;
        p.awb = CameraMetadata.CONTROL_AWB_MODE_DAYLIGHT; p.fps = 25; p.vidExp = VID_AUTO;
        l.add(p);

        p = new Preset("golden", "🌅", "Sunrise & Sunset",
                "Golden hour, silhouettes, rich sky",
                "Brightness is lowered a full stop (EV −1) so the sky keeps its orange and pink and people become silhouettes. Warm 'cloudy' white balance (≈6500K) adds golden tones. Video uses the cinema rule (25 fps, 1/50 s) with exposure locked so it doesn't pump.",
                "Tap on the bright sky, not the ground. Shoot 20 min before sunset and just after. Put people against the sky for silhouettes.");
        p.photoExp = EXP_AUTO; p.ev = -1.0f; p.awb = CameraMetadata.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT;
        p.fps = 25; p.vidExp = VID_CINE;
        l.add(p);

        p = new Preset("night", "🌃", "Night City (handheld)",
                "Streets, lights, buildings after dark",
                "Uses your main 1x lens (the biggest sensor — other lenses are noisy at night). Slightly darker (EV −0.3) so street lights don't blow out. Maximum noise reduction. Video at 25 fps matches India's 50 Hz lights so nothing flickers.",
                "Hold the phone with both hands, elbows tucked in, and stay still for a second after the click. Lean on a wall or pole if you can.");
        p.photoExp = EXP_AUTO; p.ev = -0.3f; p.zoom = 1f; p.fps = 25;
        l.add(p);

        p = new Preset("tripod_night", "🌌", "Night on Tripod",
                "Super clean night shots, phone kept still",
                "With the phone on a tripod or flat surface we can use a long 1 second shutter and keep ISO low (max 400), so the photo is bright and clean instead of grainy. 3-second timer so your finger tap doesn't shake it.",
                "Put the phone on a tripod, wall or bag. Nothing in the scene should move except lights. If your phone limits the shutter, the app uses the longest it allows.");
        p.photoExp = EXP_SHUTTER; p.shutterNs = sec(1.0); p.maxIso = 400; p.ev = -0.3f; p.timerSec = 3;
        p.video = false;
        l.add(p);

        p = new Preset("trails", "🚗", "Traffic Light Trails",
                "Car lights as streaks of colour",
                "Fixed low ISO and the longest shutter your phone allows (up to 10 s) so moving headlights and tail lights draw lines while buildings stay sharp. Focus is locked at infinity so it doesn't hunt in the dark. 3-second timer avoids shake.",
                "Tripod is a must. Shoot from a bridge or footpath overlooking a busy road, 20–40 min after sunset when the sky is still deep blue.");
        p.photoExp = EXP_MANUAL; p.iso = 0; p.shutterNs = sec(10); p.focus = FOCUS_INFINITY; p.timerSec = 3;
        p.video = false;
        l.add(p);

        p = new Preset("landscape", "🏔️", "Mountains & Landscape",
                "Wide valleys, hills, big views",
                "Ultra-wide lens (0.5x) to show how big the place is. Lowest ISO with a 1/250 s shutter for maximum detail. Focus locked at infinity so the far hills are sharp. Slightly darker (EV −0.3) to protect clouds. Daylight colour.",
                "Keep the horizon straight using the grid. Add something in the foreground (a rock, path or person) for depth. Early morning light is the best.");
        p.photoExp = EXP_SHUTTER; p.shutterNs = sec(1.0 / 250); p.maxIso = 400; p.ev = -0.3f;
        p.awb = CameraMetadata.CONTROL_AWB_MODE_DAYLIGHT; p.zoom = 0.5f; p.focus = FOCUS_INFINITY; p.fps = 25;
        l.add(p);

        p = new Preset("peaks", "🗻", "Distant Peaks (zoom)",
                "Layered mountains, far-away subjects",
                "3x zoom 'compresses' distance so mountain ridges stack on top of each other — a classic pro look. Faster 1/500 s shutter avoids the extra shake zoom causes. Focus at infinity.",
                "Look for layers of hills in mist or haze. Brace the phone against something solid.");
        p.photoExp = EXP_SHUTTER; p.shutterNs = sec(1.0 / 500); p.maxIso = 800; p.ev = -0.3f;
        p.awb = CameraMetadata.CONTROL_AWB_MODE_DAYLIGHT; p.zoom = 3f; p.focus = FOCUS_INFINITY; p.fps = 25;
        l.add(p);

        p = new Preset("snow", "❄️", "Snow, Fog & Clouds",
                "White scenes that usually look grey",
                "Cameras see lots of white and make it darker, so snow looks grey. We push brightness up (EV +0.7) so white stays white. Daylight colour avoids a blue cast.",
                "If snow still looks grey, tap a darker area. In fog, focus on something close and visible.");
        p.photoExp = EXP_AUTO; p.ev = 0.7f; p.awb = CameraMetadata.CONTROL_AWB_MODE_DAYLIGHT; p.fps = 25;
        l.add(p);

        p = new Preset("roadtrip", "🛣️", "Road Trip & Driving",
                "From a moving car or bike",
                "Video starts by default at 60 fps (smooth motion) with strong stabilisation. Photos use a very fast 1/1000 s shutter so roadside scenes aren't blurred by speed.",
                "Mount the phone — don't hand-hold while driving. Press the lens close to the windscreen and put a dark cloth on the dashboard to kill reflections.");
        p.photoExp = EXP_SHUTTER; p.shutterNs = sec(1.0 / 1000); p.ev = -0.3f;
        p.fps = 60; p.vidExp = VID_AUTO; p.startInVideo = true;
        l.add(p);

        p = new Preset("street", "🚦", "Traffic & Street (day)",
                "Busy roads, markets, people moving",
                "A 1/500 s shutter freezes walking people and vehicles. Auto colour handles mixed shade and sun. Video at 25 fps for natural motion.",
                "Wait for one interesting subject (a rickshaw, a vendor) to be on a grid line, then shoot. Keep the phone at chest height for a natural view.");
        p.photoExp = EXP_SHUTTER; p.shutterNs = sec(1.0 / 500); p.fps = 25;
        l.add(p);

        p = new Preset("portrait", "🧑", "Portrait",
                "People, faces, family photos",
                "2x zoom — the 1x wide lens stretches faces up close, 2x gives natural proportions and nicer background blur. Auto exposure follows the face. Video uses the cinema rule (25 fps, 1/50 s).",
                "Stand in open shade or near a window, not in harsh noon sun. Tap the eyes to focus. Leave space above the head.");
        p.photoExp = EXP_AUTO; p.zoom = 2f; p.fps = 25; p.vidExp = VID_CINE;
        l.add(p);

        p = new Preset("cinematic", "🎬", "Cinematic Video",
                "Movie-like look for reels and travel films",
                "25 fps with a 1/50 s shutter (the 180° rule pro filmmakers use) gives natural motion blur. Exposure is locked when you press record, so brightness doesn't jump around. Slightly darker (EV −0.3) for a moodier film look. Stabilisation on.",
                "Move slowly — walk smoothly or pan gently. Tap to switch focus between subjects. In bright sun, a clip-on ND filter keeps the 1/50 s shutter.");
        p.photo = false; p.video = true; p.fps = 25; p.vidExp = VID_CINE; p.ev = -0.3f; p.startInVideo = true;
        l.add(p);

        p = new Preset("action", "🏃", "Action, Sports & Kids",
                "Fast movement, freeze the moment",
                "1/1000 s shutter freezes running kids, cricket shots and pets. ISO can rise up to 1600 to keep that speed. Video at 60 fps so you can slow it down later.",
                "Keep shooting — take several shots in a row. Follow the subject with the phone. Works best in good light.");
        p.photoExp = EXP_SHUTTER; p.shutterNs = sec(1.0 / 1000); p.maxIso = 1600; p.fps = 60;
        l.add(p);

        p = new Preset("indoor", "🏠", "Indoor & Events",
                "Home, parties, weddings, functions",
                "Main 1x lens for best low light. 50 Hz anti-flicker and 25 fps video so tube lights and LEDs don't flicker or band. Auto colour handles warm and white lights.",
                "Turn on more lights, or move people near a window. Avoid zooming indoors — walk closer instead.");
        p.photoExp = EXP_AUTO; p.fps = 25;
        l.add(p);

        p = new Preset("food", "🍛", "Food",
                "Thalis, dishes, cafe shots",
                "2x lens avoids the distorted wide-angle look and makes the plate the hero. Slightly darker (EV −0.3) keeps rich colour in curries and sauces.",
                "Use window light from the side. Shoot from a 45° angle, or straight down for a full thali. Clean the plate edges first.");
        p.photoExp = EXP_AUTO; p.zoom = 2f; p.ev = -0.3f; p.fps = 30;
        l.add(p);

        p = new Preset("waterfall", "🌊", "Silky Water (tripod)",
                "Waterfalls, rivers, waves smoothed out",
                "A slow 1/2 s shutter turns moving water into a smooth silky flow. Lowest ISO. 3-second timer. In very bright light the phone may need a faster shutter to avoid over-exposing — shoot in shade or on a cloudy day.",
                "Tripod or a steady rock is a must. Best in shade, mornings or cloudy weather.");
        p.photoExp = EXP_SHUTTER; p.shutterNs = sec(0.5); p.maxIso = 200; p.timerSec = 3;
        p.video = false;
        l.add(p);

        p = new Preset("vlog", "🤳", "Vlog & Selfie",
                "Front camera, talking to camera",
                "Front camera, auto exposure that follows your face, 30 fps and stabilisation — the standard for YouTube and Instagram vlogs.",
                "Face a window or light source. Hold the phone at eye level, arm slightly up. Look at the lens, not the screen.");
        p.photoExp = EXP_AUTO; p.fps = 30; p.frontCamera = true; p.startInVideo = true;
        l.add(p);

        for (Preset x : l) style(x);
        cache = l;
        return l;
    }

    /** Short label for the camera's scene bar, and the tile colours for the scene gallery. */
    private static void style(Preset p) {
        switch (p.id) {
            case "auto":         look(p, "AUTO",      0xFF3A4A63, 0xFF1B2230); break;
            case "beach":        look(p, "BEACH",     0xFF0FA3B1, 0xFF0B5C8A); break;
            case "golden":       look(p, "SUNSET",    0xFFF29E4C, 0xFFB3433C); break;
            case "night":        look(p, "NIGHT",     0xFF3B2A7A, 0xFF120F2E); break;
            case "tripod_night": look(p, "TRIPOD",    0xFF22305C, 0xFF0A0F22); break;
            case "trails":       look(p, "TRAILS",    0xFFD9354C, 0xFF3A0E2E); break;
            case "landscape":    look(p, "LANDSCAPE", 0xFF4C8C4A, 0xFF1E4A3B); break;
            case "peaks":        look(p, "PEAKS",     0xFF6C7FA6, 0xFF2E3A5A); break;
            case "snow":         look(p, "SNOW",      0xFF8FB8D6, 0xFF4A6B8A); break;
            case "roadtrip":     look(p, "ROAD TRIP", 0xFF5A6B7A, 0xFF22282E); break;
            case "street":       look(p, "STREET",    0xFFE0A030, 0xFF7A4A12); break;
            case "portrait":     look(p, "PORTRAIT",  0xFFC46A8E, 0xFF5A2A4A); break;
            case "cinematic":    look(p, "CINEMATIC", 0xFF2D2D2D, 0xFF8A5A1A); break;
            case "action":       look(p, "ACTION",    0xFFE8572E, 0xFF8A1E12); break;
            case "indoor":       look(p, "INDOOR",    0xFFB07A4A, 0xFF4A3020); break;
            case "food":         look(p, "FOOD",      0xFFD9822B, 0xFF7A3A12); break;
            case "waterfall":    look(p, "SILKY",     0xFF2E9C8E, 0xFF14504A); break;
            case "vlog":         look(p, "VLOG",      0xFF7A5AE0, 0xFF3A2A8A); break;
            default:             look(p, p.name.toUpperCase(), 0xFF2B3240, 0xFF151A22);
        }
    }

    private static void look(Preset p, String shortName, int c1, int c2) {
        p.shortName = shortName;
        p.color1 = c1;
        p.color2 = c2;
    }
}
