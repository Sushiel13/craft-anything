package com.sushil.scenecam;

import java.util.ArrayList;
import java.util.List;

/**
 * What a phone's own camera app can do, and where things are in it.
 * Menu names change a little between software versions, so wording says "usually" where it varies.
 */
public class Phone {
    public final String id, brand, series, models;

    public boolean iphone;
    public boolean photoPro;            // manual ISO + shutter for photos
    public boolean videoPro;            // manual shutter/ISO for video
    public String photoProHow = "";     // how to open manual photo mode
    public String videoProHow = "";     // how to open manual video mode
    public String nightHow = "";
    public String longExpoHow = "";     // light trails / silky water without full manual
    public String portraitHow = "";
    public String portraitVideoHow = "";
    public String stabHow = "";         // strongest stabilisation
    public String resHow = "";          // where resolution + fps are chosen
    public String fps25How = "";        // how to get 25 fps (anti-flicker in India)
    public String evHow = "";           // brightness without Pro mode
    public String lockHow = "";         // AE/AF lock
    public String wbHow = "";           // white balance without Pro mode
    public String gridHow = "";
    public String timerHow = "";
    public String rawHow = "";          // empty = no RAW in the stock app
    public String burstHow = "";
    public float tele = 0f;             // optical telephoto (0 = none, zoom is a crop)
    public float tele2 = 0f;            // second telephoto
    public boolean ultraWide = true;

    Phone(String id, String brand, String series, String models) {
        this.id = id;
        this.brand = brand;
        this.series = series;
        this.models = models;
    }

    public String fullName() {
        return brand + " · " + series;
    }

    public static Phone byId(String id) {
        if (id == null) return null;
        for (Phone p : all()) if (p.id.equals(id)) return p;
        return null;
    }

    private static List<Phone> cache;

    public static synchronized List<Phone> all() {
        if (cache != null) return cache;
        List<Phone> l = new ArrayList<>();
        Phone p;

        // ---------------- Samsung ----------------
        p = samsungBase(new Phone("samsung_ultra", "Samsung", "Galaxy S Ultra", "S22 Ultra · S23 Ultra · S24 Ultra · S25 Ultra and newer"));
        p.tele = 3f; p.tele2 = 5f;
        p.rawHow = "For the best detail use Samsung's free Expert RAW app (Camera → MORE → Expert RAW), or in PRO mode turn on RAW: Camera settings → Advanced picture options → Picture formats → RAW copies.";
        l.add(p);

        p = samsungBase(new Phone("samsung_s", "Samsung", "Galaxy S / S+ / FE / Z", "S23 · S24 · S25 (and +, FE), Z Fold · Z Flip"));
        p.tele = 3f;
        p.rawHow = "In PRO mode turn on RAW: Camera settings → Advanced picture options → Picture formats → RAW copies (Expert RAW app also works on most S models).";
        l.add(p);

        p = samsungBase(new Phone("samsung_a", "Samsung", "Galaxy A / M / F", "A35 · A55 · A56 · M & F series"));
        p.videoPro = false;
        p.videoProHow = "";
        p.tele = 0f;
        p.rawHow = "";
        l.add(p);

        // ---------------- Google Pixel ----------------
        p = pixelBase(new Phone("pixel_pro", "Google", "Pixel Pro", "Pixel 8 Pro · 9 Pro · 9 Pro XL · 10 Pro and newer"));
        p.photoPro = true;
        p.photoProHow = "Stay in PHOTO, tap the settings (sliders) icon → Pro tab. Shutter speed, ISO and focus controls appear there (Pro controls; names can vary by update).";
        p.tele = 5f;
        p.rawHow = "Camera settings (gear) → Pro tab → RAW/JPEG control → RAW + JPEG.";
        l.add(p);

        p = pixelBase(new Phone("pixel", "Google", "Pixel / Pixel a", "Pixel 7 · 8 · 9 · 10 and a-series"));
        l.add(p);

        // ---------------- Xiaomi ----------------
        p = new Phone("xiaomi", "Xiaomi", "Xiaomi / Redmi / POCO", "Xiaomi 13–15, Redmi Note, POCO F/X series");
        p.photoPro = true; p.videoPro = true;
        p.photoProHow = "Swipe the mode bar to MORE → PRO (on some models PRO sits directly in the mode bar).";
        p.videoProHow = "Open PRO and tap the video icon inside it (if your model has it), otherwise use VIDEO mode and its Pro/Movie options under MORE.";
        p.nightHow = "Swipe to NIGHT mode. Hold still for 2–3 seconds after tapping the shutter.";
        p.longExpoHow = "MORE → LONG EXPOSURE (choose 'Light trails' or 'Silky water'), or PRO with a long shutter on a tripod.";
        p.portraitHow = "Swipe to PORTRAIT, pick 2x if offered, set the f-number (blur) to about f/2.8–f/4.";
        p.portraitVideoHow = "MORE → MOVIE / Portrait video (name varies by model) — or normal VIDEO.";
        p.stabHow = "In VIDEO, tap the stabilisation (steady hand) icon to turn on 'Super steady' (it may limit you to 1080p).";
        p.resHow = "In VIDEO, tap the resolution and fps chips at the top (e.g. 4K · 30).";
        p.fps25How = "25 fps is usually inside PRO video or not available — 30 fps is fine outdoors.";
        p.evHow = "tap the screen and drag the sun icon up or down";
        p.lockHow = "Press and hold on the subject until 'AE/AF lock' appears.";
        p.wbHow = "Auto is fine; for exact colour use PRO → WB.";
        p.gridHow = "Camera settings (≡ / gear) → Framing lines → Grid.";
        p.timerHow = "Tap the ≡ / ⋮ menu at the top → Timer → 3 s.";
        p.rawHow = "In PRO, tap the RAW toggle at the top (on most Xiaomi models).";
        p.burstHow = "Press and hold the shutter (or swipe it to the left, depending on settings).";
        l.add(p);

        // ---------------- OnePlus / Oppo / Realme ----------------
        p = new Phone("oneplus", "OnePlus / Oppo / Realme", "OnePlus · Oppo · Realme", "OnePlus 11–13, Nord · Oppo Find / Reno · Realme GT / Number series");
        p.photoPro = true; p.videoPro = true;
        p.photoProHow = "Swipe to PRO (OnePlus/Oppo may call it 'Master mode', Realme 'Expert'), or find it under MORE.";
        p.videoProHow = "MORE → PRO VIDEO / Movie (name varies). If missing, use VIDEO mode.";
        p.nightHow = "Swipe to NIGHT. Hold still until the progress circle finishes.";
        p.longExpoHow = "Use PRO with a long shutter on a tripod (some models also have MORE → Long exposure).";
        p.portraitHow = "Swipe to PORTRAIT, pick 2x if available, set blur to about f/2.8–f/4.";
        p.portraitVideoHow = "Swipe to PORTRAIT VIDEO / Cinematic (under MORE on some models).";
        p.stabHow = "In VIDEO, tap the stabilisation icon for 'Ultra steady' (may cap resolution).";
        p.resHow = "In VIDEO, tap the resolution and fps text at the top (e.g. 4K 30fps).";
        p.fps25How = "25 fps is usually not offered — 30 fps is fine outdoors; indoors avoid filming close to tube lights.";
        p.evHow = "tap the screen and drag the sun icon up or down";
        p.lockHow = "Press and hold on the subject until 'AE/AF locked' appears.";
        p.wbHow = "Auto is fine; for exact colour use PRO → WB.";
        p.gridHow = "Camera settings (≡ / gear) → Grid / Composition guides.";
        p.timerHow = "Tap the timer icon at the top → 3 s.";
        p.rawHow = "In PRO/Master mode, tap 'RAW' or 'RAW+' at the top (on most recent models).";
        p.burstHow = "Press and hold the shutter.";
        l.add(p);

        // ---------------- Vivo / iQOO ----------------
        p = new Phone("vivo", "Vivo / iQOO", "Vivo · iQOO", "Vivo X / V / T series, iQOO");
        p.photoPro = true; p.videoPro = true;
        p.photoProHow = "Swipe the mode bar to MORE → PRO (on X series PRO is in the main bar).";
        p.videoProHow = "Open PRO and switch it to video (if your model has it), otherwise use VIDEO mode.";
        p.nightHow = "Swipe to NIGHT. Hold still until it finishes.";
        p.longExpoHow = "MORE → LONG EXPOSURE (Light painting / Silky water), or PRO with a long shutter on a tripod.";
        p.portraitHow = "Swipe to PORTRAIT, pick 2x if offered, set blur to about f/2.8–f/4.";
        p.portraitVideoHow = "MORE → Movie portrait / Cinematic portrait (name varies).";
        p.stabHow = "In VIDEO, turn on 'Ultra stabilisation' from the icons at the top.";
        p.resHow = "In VIDEO, tap the resolution chip at the top, then pick fps.";
        p.fps25How = "25 fps is usually not offered — 30 fps is fine outdoors.";
        p.evHow = "tap the screen and drag the sun icon up or down";
        p.lockHow = "Press and hold on the subject until 'AE/AF lock' appears.";
        p.wbHow = "Auto is fine; for exact colour use PRO → WB.";
        p.gridHow = "Camera settings (gear) → Grid.";
        p.timerHow = "Tap ≡ / ⋮ at the top → Timer → 3 s.";
        p.rawHow = "In PRO, turn on RAW from the top bar (most models).";
        p.burstHow = "Press and hold the shutter.";
        l.add(p);

        // ---------------- Motorola / Nothing / others ----------------
        p = new Phone("other", "Other Android", "Motorola · Nothing · others", "Any Android phone with a Pro / Manual mode");
        p.photoPro = true; p.videoPro = false;
        p.photoProHow = "Look for PRO / MANUAL in the mode bar or under MORE. If there's none, use PHOTO and follow the brightness steps.";
        p.nightHow = "Swipe to NIGHT (Night vision / Night mode).";
        p.longExpoHow = "Use PRO with a long shutter on a tripod if your phone has it.";
        p.portraitHow = "Swipe to PORTRAIT and choose 2x if offered.";
        p.portraitVideoHow = "Use VIDEO (or Portrait video if your phone has it).";
        p.stabHow = "In VIDEO settings turn on video stabilisation.";
        p.resHow = "In VIDEO, tap the resolution/fps chip, or open Camera settings → Video resolution.";
        p.fps25How = "25 fps is usually not offered — 30 fps is fine outdoors.";
        p.evHow = "tap the screen and drag the brightness (sun) slider";
        p.lockHow = "Press and hold on the subject to lock focus and exposure (if your phone supports it).";
        p.wbHow = "Leave on Auto.";
        p.gridHow = "Camera settings → Grid / Composition guide.";
        p.timerHow = "Tap the timer icon → 3 s.";
        p.burstHow = "Press and hold the shutter.";
        l.add(p);

        // ---------------- iPhone ----------------
        p = iphoneBase(new Phone("iphone_pro", "Apple", "iPhone Pro / Pro Max", "iPhone 13 Pro – 17 Pro / Pro Max"));
        p.tele = 3f; p.tele2 = 5f;
        p.rawHow = "Settings → Camera → Formats → turn on ProRAW & Resolution Control (or Photo Mode → ProRAW), then tap 'RAW' in the camera.";
        l.add(p);

        p = iphoneBase(new Phone("iphone", "Apple", "iPhone / Plus / e", "iPhone 13 – 17, Plus, Air, e models"));
        p.tele = 0f;
        l.add(p);

        cache = l;
        return l;
    }

    private static Phone samsungBase(Phone p) {
        p.photoPro = true; p.videoPro = true;
        p.photoProHow = "Swipe the mode bar to MORE → PRO.";
        p.videoProHow = "Swipe the mode bar to MORE → PRO VIDEO.";
        p.nightHow = "Swipe to MORE → NIGHT (Photo mode also switches to night automatically when it's dark — a moon icon appears).";
        p.longExpoHow = "In PRO set the shutter (S) to the long value below; Samsung allows up to 30 s.";
        p.portraitHow = "Swipe to PORTRAIT, tap 2x or 3x, then tap the f-number and set the blur to about f/2.8–f/4.";
        p.portraitVideoHow = "MORE → PORTRAIT VIDEO.";
        p.stabHow = "In VIDEO, tap the shaking-hand icon at the top to turn on Super steady (works at 1080p/2K).";
        p.resHow = "In VIDEO, tap the resolution chip at the top (e.g. UHD 30) and pick UHD 4K and 30/60 fps.";
        p.fps25How = "In PRO VIDEO, tap the fps option and pick 25 if it's listed (stops light flicker indoors); otherwise 30 is fine.";
        p.evHow = "tap the screen and drag the sun (brightness) slider";
        p.lockHow = "Press and hold on the subject until 'AE/AF lock' appears.";
        p.wbHow = "Auto is fine in PHOTO mode; for exact colour use PRO → WB.";
        p.gridHow = "Camera settings (gear) → Grid lines → 3 × 3.";
        p.timerHow = "Tap the ⌃ arrow / quick bar at the top → Timer → 3 s.";
        p.burstHow = "Camera settings → Swipe shutter button to → Take burst; then swipe the shutter down.";
        return p;
    }

    private static Phone pixelBase(Phone p) {
        p.photoPro = false; p.videoPro = false;
        p.photoProHow = "";
        p.nightHow = "Swipe to NIGHT SIGHT (Photo also turns it on automatically in the dark). Hold still until it finishes.";
        p.longExpoHow = "Swipe to MOTION → LONG EXPOSURE. Keep the phone very still (tripod or wall).";
        p.portraitHow = "Swipe to PORTRAIT and pick 2x (or 5x on Pro).";
        p.portraitVideoHow = "In VIDEO, tap CINEMATIC (Cinematic blur).";
        p.stabHow = "In VIDEO settings (gear) → Stabilisation → Active (for walking/vehicles) or Locked (for zoomed shots).";
        p.resHow = "In VIDEO, tap the settings (gear) icon → Resolution 4K and Frame rate 30/60.";
        p.fps25How = "Pixel usually offers 24, 30 and 60 fps — use 24 or 30 indoors.";
        p.evHow = "tap the screen and drag the Brightness slider that appears";
        p.lockHow = "Tap the subject, then tap the lock icon on the slider to lock focus and exposure.";
        p.wbHow = "If your Pixel shows a White balance slider (sliders icon / after tapping the screen), move it left for cooler, right for warmer; otherwise leave Auto.";
        p.gridHow = "Camera settings (gear) → More settings → Grid type → 3 × 3.";
        p.timerHow = "Tap the settings (sliders) icon → Timer → 3 s.";
        p.rawHow = "";
        p.burstHow = "Press and hold the shutter (Top Shot / burst).";
        return p;
    }

    private static Phone iphoneBase(Phone p) {
        p.iphone = true;
        p.photoPro = false; p.videoPro = false;
        p.photoProHow = "";
        p.nightHow = "Stay in PHOTO — Night mode turns on by itself in the dark (yellow moon icon). On a tripod, tap the moon and drag the timer to the max (up to 30 s).";
        p.longExpoHow = "Turn on Live Photo (circle icon), take the shot, then in Photos tap 'Live' (top-left) → LONG EXPOSURE.";
        p.portraitHow = "Swipe to PORTRAIT, pick 2x (or 3x/5x on Pro), tap ƒ and set about f/2.8–f/4.";
        p.portraitVideoHow = "Swipe to CINEMATIC (tap ƒ to set blur, tap people to change focus).";
        p.stabHow = "In VIDEO, tap the running-figure icon to turn on ACTION MODE (it records at a lower resolution than 4K).";
        p.resHow = "In VIDEO, tap the resolution and fps at the top-right (e.g. 4K · 30). Defaults: Settings → Camera → Record Video.";
        p.fps25How = "Settings → Camera → Record Video → turn on 'Show PAL Formats', then pick 25 fps — stops tube-light flicker in India.";
        p.evHow = "tap the subject, then drag the sun ☀ icon next to the focus box up or down";
        p.lockHow = "Press and hold on the subject until 'AE/AF LOCK' appears.";
        p.wbHow = "iPhone has no WB control. Use Photographic Styles (swipe left in the viewfinder / tap the styles icon) for warmer or cooler tones. For video, Settings → Camera → Record Video → Lock White Balance.";
        p.gridHow = "Settings → Camera → Grid (on) and Level (on).";
        p.timerHow = "Tap the ⌃ arrow at the top → timer icon → 3 s.";
        p.burstHow = "Swipe the shutter button to the left and hold (Burst).";
        return p;
    }
}
