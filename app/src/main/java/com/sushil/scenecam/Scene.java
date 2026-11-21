package com.sushil.scenecam;

import java.util.ArrayList;
import java.util.List;

/** One shooting situation and the settings a pro would use for it. */
public class Scene {

    // Photo modes
    public static final int P_PHOTO = 0, P_PRO = 1, P_NIGHT = 2, P_LONG = 3, P_PORTRAIT = 4;
    // Video modes
    public static final int V_VIDEO = 0, V_PRO = 1, V_CINEMATIC = 2, V_ACTION = 3;

    public final String id, emoji, name, shortDesc;
    public int color1, color2;

    // ---- photo
    public boolean photo = true;
    public int photoMode = P_PHOTO;
    public String iso = "Auto";
    public String shutter = "Auto";
    public String ev = "0";
    public int wbK = 0;                 // 0 = Auto
    public float lens = 1f;
    public boolean focusInfinity = false;
    public int timer = 0;
    public boolean raw = false;
    public boolean burst = false;
    public boolean tripod = false;
    public String photoWhy = "";
    public String photoTips = "";

    // ---- video
    public boolean video = true;
    public int videoMode = V_VIDEO;
    public String res = "4K";
    public int fps = 30;
    public String vShutter = "Auto";
    public String vIso = "Auto";
    public String vEv = null;           // null = same as photo
    public int vWbK = -1;               // -1 = same as photo
    public float vLens = -1f;           // -1 = same as photo
    public String stab = "On";
    public boolean front = false;
    public boolean lockExposure = false;
    public String videoWhy = "";
    public String videoTips = "";

    Scene(String id, String emoji, String name, String shortDesc, int c1, int c2) {
        this.id = id;
        this.emoji = emoji;
        this.name = name;
        this.shortDesc = shortDesc;
        this.color1 = c1;
        this.color2 = c2;
    }

    public String videoEv() { return vEv == null ? ev : vEv; }
    public int videoWbK() { return vWbK < 0 ? wbK : vWbK; }
    public float videoLens() { return vLens < 0 ? lens : vLens; }

    public static Scene byId(String id) {
        for (Scene s : all()) if (s.id.equals(id)) return s;
        return all().get(0);
    }

    private static List<Scene> cache;

    public static synchronized List<Scene> all() {
        if (cache != null) return cache;
        List<Scene> l = new ArrayList<>();
        Scene s;

        s = new Scene("daylight", "☀️", "Everyday Daylight", "Outings, buildings, friends outdoors", 0xFF56CCF2, 0xFF2F80ED);
        s.photoMode = P_PHOTO; s.iso = "Auto (lowest)"; s.shutter = "Auto"; s.ev = "0 to −0.3";
        s.photoWhy = "In good light the phone's own auto mode is excellent. Pulling brightness down a little keeps the sky from going white.";
        s.photoTips = "Keep the sun behind or beside you. Put the main subject on a grid line, not dead centre.";
        s.res = "4K"; s.fps = 30;
        s.videoWhy = "4K 30 fps is the sharp, smooth standard for daytime clips.";
        s.videoTips = "Move slowly; pan for 3–5 seconds per shot. Lock exposure if clouds keep changing the brightness.";
        l.add(s);

        s = new Scene("beach", "🏖️", "Beach & Bright Sun", "Sand, sea, harsh midday light", 0xFF2BD2C4, 0xFF1A8FE3);
        s.photoMode = P_PRO; s.iso = "50–100"; s.shutter = "1/1000 s"; s.ev = "−0.7"; s.wbK = 5500;
        s.photoWhy = "Sand and water reflect a lot of light, so the camera over-brightens and the sky turns white — EV −0.7 fixes that. A fast 1/1000 s shutter freezes waves and splashes. Lowest ISO = cleanest photo. 5500K keeps the sea blue instead of grey.";
        s.photoTips = "Keep the horizon straight and on the lower or upper grid line. Early morning or after 4 pm gives much nicer colour than noon. A clip-on polarising filter makes the sky deeper blue.";
        s.res = "4K"; s.fps = 30; s.vEv = "−0.7";
        s.videoWhy = "Lower brightness protects the bright water. 4K 30 keeps detail in waves.";
        s.videoTips = "For smooth 'movie' motion in strong sun a clip-on ND filter helps. Keep the phone level when panning along the shore.";
        l.add(s);

        s = new Scene("golden", "🌅", "Sunrise & Sunset", "Golden hour, silhouettes, colourful sky", 0xFFFFB75E, 0xFFED5E68);
        s.photoMode = P_PRO; s.iso = "50–100"; s.shutter = "1/250 – 1/500 s"; s.ev = "−1.0"; s.wbK = 6500;
        s.photoWhy = "Exposing for the bright sky (EV −1) keeps the orange and pink colours and turns people and trees into silhouettes. 6500K adds warm, golden tones.";
        s.photoTips = "Tap on the sky, not the ground. Best light is 20 minutes before and after sunset. Put a person or tree against the sky for a silhouette.";
        s.res = "4K"; s.fps = 25; s.videoMode = V_PRO; s.vShutter = "1/50 s"; s.vIso = "Lowest"; s.lockExposure = true;
        s.videoWhy = "Locking exposure stops the brightness from 'pumping' as the sun moves in and out of frame. 25 fps with 1/50 s gives a natural cinematic look.";
        s.videoTips = "Shoot a slow 10-second pan or a static time-lapse. Keep the sun near a third line, not centre.";
        l.add(s);

        s = new Scene("night", "🌃", "Night City (handheld)", "Streets, lights, markets after dark", 0xFF6A5AE0, 0xFF2B1F6B);
        s.photoMode = P_NIGHT; s.iso = "Auto"; s.shutter = "Auto (Night mode)"; s.ev = "−0.3"; s.wbK = 4000; s.lens = 1f;
        s.photoWhy = "Night mode merges many shots for a bright, clean photo. Use the 1x main lens — it has the biggest sensor; the zoom and ultra-wide lenses are noisy at night. EV −0.3 stops street lights from blowing out.";
        s.photoTips = "Hold the phone with both hands, elbows tucked in, and stay still until the capture finishes. Lean on a wall or pole if possible.";
        s.res = "4K"; s.fps = 30; s.vEv = "−0.3";
        s.videoWhy = "1x lens and slightly lower brightness give the cleanest night video. Drop to 1080p if the 4K clip looks grainy.";
        s.videoTips = "Walk slowly and smoothly. Neon signs and shop lights make great subjects.";
        l.add(s);

        s = new Scene("tripod_night", "🌌", "Night on Tripod", "Super clean night shots, phone kept still", 0xFF4364F7, 0xFF1B1F5E);
        s.photoMode = P_PRO; s.iso = "100–200"; s.shutter = "1 – 4 s"; s.ev = "−0.3"; s.wbK = 4000; s.timer = 3; s.tripod = true; s.raw = true;
        s.photoWhy = "On a tripod the phone can keep the shutter open for seconds, so ISO stays low and the photo is bright AND clean. The 3-second timer stops your finger tap from shaking it.";
        s.photoTips = "Use a tripod, wall or bag. Nothing important should move. If your phone has no Pro mode, Night mode on a tripod also works — it automatically uses longer exposures.";
        s.video = false;
        l.add(s);

        s = new Scene("trails", "🚗", "Traffic Light Trails", "Headlights and tail-lights as streaks", 0xFFFF5E62, 0xFF8E2DE2);
        s.photoMode = P_LONG; s.iso = "50–100"; s.shutter = "5 – 15 s"; s.ev = "0"; s.wbK = 3800; s.focusInfinity = true; s.timer = 3; s.tripod = true;
        s.photoWhy = "A long shutter lets moving lights draw lines while buildings stay sharp. Lowest ISO keeps it clean. Focus at infinity (∞) so it doesn't hunt in the dark.";
        s.photoTips = "Tripod is a must. Shoot from a footbridge or roadside overlooking a busy road, 20–40 minutes after sunset while the sky is still deep blue.";
        s.video = false;
        l.add(s);

        s = new Scene("landscape", "🏔️", "Mountains & Landscape", "Valleys, hills, big views", 0xFF5BD68A, 0xFF1E9E7A);
        s.photoMode = P_PRO; s.iso = "50–100"; s.shutter = "1/250 s"; s.ev = "−0.3"; s.wbK = 5500; s.lens = 0.5f; s.focusInfinity = true; s.raw = true;
        s.photoWhy = "The 0.5x ultra-wide shows how big the place is. Lowest ISO and focus at infinity give maximum detail from near to far. EV −0.3 protects clouds.";
        s.photoTips = "Add something in the foreground (rock, path, person) for depth. Keep the horizon level with the grid. Early morning light is best.";
        s.res = "4K"; s.fps = 30; s.vLens = 1f;
        s.videoWhy = "4K at 1x keeps the most detail; the ultra-wide is softer in video.";
        s.videoTips = "Do a very slow pan left-to-right, or stand still and let clouds move. 10 seconds per shot.";
        l.add(s);

        s = new Scene("peaks", "🗻", "Distant Peaks (zoom)", "Layered mountains, far-away subjects", 0xFF8EA6F5, 0xFF4A5BC2);
        s.photoMode = P_PRO; s.iso = "50–200"; s.shutter = "1/500 s"; s.ev = "−0.3"; s.wbK = 5500; s.lens = 3f; s.focusInfinity = true; s.raw = true;
        s.photoWhy = "Zooming in 'compresses' distance, so ridges stack on top of each other — a classic pro look. A faster shutter avoids the extra shake that zoom causes.";
        s.photoTips = "Look for layers of hills in mist or haze. Brace the phone on a railing or rock.";
        s.res = "4K"; s.fps = 30;
        s.videoWhy = "Zoomed video shows every hand-shake, so keep stabilisation on and brace the phone.";
        s.videoTips = "Hold still — don't pan while zoomed in.";
        l.add(s);

        s = new Scene("snow", "❄️", "Snow, Fog & Clouds", "White scenes that usually look grey", 0xFF9BD8F5, 0xFF5B8DEF);
        s.photoMode = P_PRO; s.iso = "50–100"; s.shutter = "1/500 – 1/1000 s"; s.ev = "+0.7"; s.wbK = 5500;
        s.photoWhy = "Cameras see lots of white and darken it, so snow looks grey. EV +0.7 keeps white looking white. Daylight colour avoids a blue cast.";
        s.photoTips = "In fog, focus on something close and visible. Bright clothes make people pop against white.";
        s.res = "4K"; s.fps = 30; s.vEv = "+0.7";
        s.videoWhy = "Same as photos: raise brightness so snow isn't grey.";
        s.videoTips = "Keep the lens warm in your pocket between shots to avoid fogging.";
        l.add(s);

        s = new Scene("roadtrip", "🛣️", "Road Trip & Driving", "From a moving car or bike", 0xFFFF9F43, 0xFFEE5253);
        s.photoMode = P_PRO; s.iso = "Auto"; s.shutter = "1/1000 s"; s.ev = "−0.3";
        s.photoWhy = "A very fast shutter freezes roadside scenes even at speed.";
        s.photoTips = "Press the lens close to the window to avoid reflections; a dark cloth on the dashboard helps.";
        s.res = "4K"; s.fps = 60; s.videoMode = V_ACTION; s.stab = "Maximum (Super steady / Action mode)";
        s.videoWhy = "60 fps makes motion smooth, and the strongest stabilisation removes road bumps.";
        s.videoTips = "Mount the phone — never hand-hold while driving. Clean the windscreen first.";
        l.add(s);

        s = new Scene("street", "🚦", "Traffic & Street (day)", "Busy roads, markets, people moving", 0xFFFECA57, 0xFFFF7A2F);
        s.photoMode = P_PRO; s.iso = "Auto (max 800)"; s.shutter = "1/500 s"; s.ev = "0";
        s.photoWhy = "1/500 s freezes walking people and vehicles; auto ISO keeps the brightness right as you move between sun and shade.";
        s.photoTips = "Wait for one interesting subject (a rickshaw, a vendor) to reach a grid line, then shoot. Burst mode helps.";
        s.burst = true;
        s.res = "4K"; s.fps = 30;
        s.videoWhy = "Standard 4K 30 with stabilisation for walking shots.";
        s.videoTips = "Keep the phone at chest height for a natural view. Record 5–10 seconds per clip.";
        l.add(s);

        s = new Scene("portrait", "🧑", "Portrait", "People, faces, family photos", 0xFFF78CA0, 0xFFD62976);
        s.photoMode = P_PORTRAIT; s.iso = "Auto"; s.shutter = "Auto"; s.ev = "0"; s.lens = 2f;
        s.photoWhy = "2x (or 3x) gives natural face proportions — the 1x wide lens stretches faces up close. Background blur of about f/2.8–f/4 looks real; stronger blur cuts into hair edges.";
        s.photoTips = "Stand in open shade or near a window, not in harsh noon sun. Tap the eyes to focus. Leave a little space above the head.";
        s.res = "4K"; s.fps = 30; s.videoMode = V_CINEMATIC; s.lockExposure = true;
        s.videoWhy = "Cinematic / portrait video blurs the background and keeps focus on the face.";
        s.videoTips = "Keep 1–2 metres from the person. Tap a different face to change focus mid-shot.";
        l.add(s);

        s = new Scene("cinematic", "🎬", "Cinematic Video", "Movie-like reels and travel films", 0xFF434343, 0xFFB06AB3);
        s.photo = false;
        s.res = "4K"; s.fps = 25; s.videoMode = V_PRO; s.vShutter = "1/50 s"; s.vIso = "Lowest possible"; s.ev = "−0.3"; s.wbK = 5600; s.lockExposure = true;
        s.videoWhy = "Film-makers use the 180° rule: shutter = 1 ÷ (2 × fps). At 25 fps that's 1/50 s, which gives natural motion blur. Fixed white balance and locked exposure stop colours and brightness from jumping. 25 fps also matches India's 50 Hz lights, so nothing flickers.";
        s.videoTips = "Move slowly: walk smoothly, pan gently, or use a gimbal. In bright sun, a clip-on ND filter lets you keep 1/50 s without over-exposing.";
        l.add(s);

        s = new Scene("action", "🏃", "Action, Sports & Kids", "Fast movement, freeze the moment", 0xFFFF6B6B, 0xFFE52E71);
        s.photoMode = P_PRO; s.iso = "Auto (max 1600)"; s.shutter = "1/1000 s"; s.ev = "0"; s.burst = true;
        s.photoWhy = "1/1000 s freezes a cricket shot, a running child or a pet mid-jump. ISO is allowed to rise so the photo isn't too dark at that speed.";
        s.photoTips = "Use burst mode and pick the best frame later. Follow the subject with the phone.";
        s.res = "4K"; s.fps = 60;
        s.videoWhy = "60 fps captures fast motion smoothly and can be slowed down 2× later.";
        s.videoTips = "Works best in good light. For true slow motion use the SLO-MO mode (120/240 fps).";
        l.add(s);

        s = new Scene("indoor", "🏠", "Indoor & Events", "Home, parties, weddings, functions", 0xFFF7B267, 0xFFC56B8A);
        s.photoMode = P_PHOTO; s.iso = "Auto"; s.shutter = "Auto"; s.ev = "0"; s.lens = 1f;
        s.photoWhy = "The 1x main lens is the best in low light. The phone's auto mode (and auto Night mode) handles mixed warm and white lights well.";
        s.photoTips = "Turn on more lights or move people near a window. Don't zoom indoors — walk closer instead. Turn flash off unless it's very dark.";
        s.res = "4K"; s.fps = 25; s.videoMode = V_PRO; s.vShutter = "1/50 s"; s.vIso = "Auto";
        s.videoWhy = "India's mains power is 50 Hz, so tube lights and LEDs flicker at a rate that shows up as banding in 30/60 fps video. 25 fps with a 1/50 s shutter removes it.";
        s.videoTips = "If your phone has no 25 fps, use 30 fps and avoid filming close to tube lights.";
        l.add(s);

        s = new Scene("food", "🍛", "Food", "Thalis, dishes, café shots", 0xFFFFA94D, 0xFFE8590C);
        s.photoMode = P_PHOTO; s.iso = "Auto"; s.shutter = "Auto"; s.ev = "−0.3"; s.lens = 2f;
        s.photoWhy = "2x avoids the distorted wide-angle look and makes the plate the hero. Slightly darker (EV −0.3) keeps rich colour in curries and sauces.";
        s.photoTips = "Use window light from the side. Shoot from a 45° angle, or straight down for a full thali. Wipe the plate edges first.";
        s.res = "4K"; s.fps = 30;
        s.videoWhy = "A slow 4K clip of steam or a pour looks great on reels.";
        s.videoTips = "Try a slow slide-in towards the plate. Film pouring or tearing bread for movement.";
        l.add(s);

        s = new Scene("waterfall", "🌊", "Silky Water (tripod)", "Waterfalls and rivers smoothed out", 0xFF43E2C6, 0xFF1294A8);
        s.photoMode = P_LONG; s.iso = "50 (lowest)"; s.shutter = "1/2 – 2 s"; s.ev = "0"; s.wbK = 5500; s.timer = 3; s.tripod = true;
        s.photoWhy = "A slow shutter turns moving water into a smooth, silky flow while the rocks stay sharp.";
        s.photoTips = "Tripod or a steady rock is a must. Best in shade, mornings or cloudy weather — in bright sun the photo may come out too bright (an ND filter fixes that).";
        s.video = false;
        l.add(s);

        s = new Scene("vlog", "🤳", "Vlog & Selfie", "Front camera, talking to camera", 0xFFB06AB3, 0xFF6A5AE0);
        s.photoMode = P_PORTRAIT; s.iso = "Auto"; s.shutter = "Auto"; s.ev = "0"; s.front = true;
        s.photoWhy = "Front camera in Portrait mode blurs the background behind your face.";
        s.photoTips = "Face a window or light source. Hold the phone slightly above eye level.";
        s.res = "4K"; s.fps = 30;
        s.videoWhy = "4K 30 on the front camera with stabilisation is the standard for YouTube and Instagram vlogs.";
        s.videoTips = "Look at the lens, not the screen. Use wired or wireless mic if it's windy.";
        l.add(s);

        cache = l;
        return l;
    }
}
