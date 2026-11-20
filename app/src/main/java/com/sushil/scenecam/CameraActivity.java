package com.sushil.scenecam;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.widget.HorizontalScrollView;
import android.widget.SeekBar;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.MeteringRectangle;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaCodec;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.util.Range;
import android.util.Rational;
import android.util.Size;
import android.view.MotionEvent;
import android.view.OrientationEventListener;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class CameraActivity extends Activity {

    public static final String EXTRA_PRESET = "preset";
    private static final int ACCENT = 0xFFFFB547;
    private static final int REQ_PERMS = 7;

    // ---- views
    private AutoFitTextureView texture;
    private GridOverlay grid;
    private TextView title, tipBanner, modePhoto, modeVideo, countdown, recTime, timerBtn;
    private View shutter, flash, flip, evPanel, modeSeg;
    private ImageView thumb, gridBtn;
    private LinearLayout zoomRow, proStrip, carousel;
    private HorizontalScrollView carouselScroll;
    private SeekBar evSeek;
    private final TextView[] proLabel = new TextView[5];
    private final TextView[] proValue = new TextView[5];
    private SharedPreferences prefs;
    private int timerSec;
    private static final int REQ_SCENE = 8;

    // ---- state chosen by the user / preset
    private Preset preset;
    private boolean videoMode;
    private boolean useFront;
    private boolean want4k = true;
    private float ev;
    private float zoom;
    private float[] focusPoint;        // normalised tap point on the preview, or null
    private int deviceOrientation = 0;

    // ---- camera
    private CameraManager cm;
    private String cameraId;
    private CameraCharacteristics chars;
    private CameraDevice device;
    private CameraCaptureSession session;
    private ImageReader jpegReader;
    private Surface previewSurface;
    private Surface recorderSurface;     // persistent input surface for MediaRecorder
    private HandlerThread bgThread;
    private Handler bg;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private boolean opening;

    // ---- capabilities
    private boolean manualSensor;
    private Range<Integer> isoRange;
    private Range<Long> expRange;
    private float evStep = 1f / 3f;
    private Range<Integer> evRange = new Range<>(0, 0);
    private Range<Float> zoomRange = new Range<>(1f, 1f);
    private Rect activeArray;
    private boolean canFocusManually;

    // ---- sizes / fps
    private Size previewSize, jpegSize, videoSize;
    private int fps = 30;
    private Range<Integer> fpsRange;
    private String videoNote = "";

    // ---- metering from live preview
    private volatile long meteredExp = 0;
    private volatile int meteredIso = 0;
    private volatile long liveExp = 0;
    private volatile int liveIso = 0;
    private long lastReadoutMs = 0;

    // ---- recording
    private MediaRecorder recorder;
    private boolean recording;
    private long[] recordingExposure;   // locked {timeNs, iso} or null for auto
    private Uri videoUri;
    private ParcelFileDescriptor videoPfd;
    private long recStartMs;

    private Uri lastUri;
    private boolean lastIsVideo;
    private boolean capturing;
    private OrientationEventListener orientationListener;

    // =====================================================================
    // Lifecycle
    // =====================================================================

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);
        prefs = getSharedPreferences("scenecam", MODE_PRIVATE);

        String id = getIntent().getStringExtra(EXTRA_PRESET);
        if (id == null) id = prefs.getString("scene", "auto");
        preset = Preset.byId(id);
        want4k = prefs.getBoolean("want4k", true);

        texture = findViewById(R.id.texture);
        grid = findViewById(R.id.grid);
        flash = findViewById(R.id.flash);
        title = findViewById(R.id.title);
        tipBanner = findViewById(R.id.tipBanner);
        modePhoto = findViewById(R.id.modePhoto);
        modeVideo = findViewById(R.id.modeVideo);
        modeSeg = findViewById(R.id.modeSeg);
        countdown = findViewById(R.id.countdown);
        recTime = findViewById(R.id.recTime);
        timerBtn = findViewById(R.id.timerBtn);
        shutter = findViewById(R.id.shutter);
        thumb = findViewById(R.id.thumb);
        zoomRow = findViewById(R.id.zoomRow);
        proStrip = findViewById(R.id.proStrip);
        carousel = findViewById(R.id.carousel);
        carouselScroll = findViewById(R.id.carouselScroll);
        flip = findViewById(R.id.flip);
        gridBtn = findViewById(R.id.gridBtn);
        evPanel = findViewById(R.id.evPanel);
        evSeek = findViewById(R.id.evSeek);

        thumb.setClipToOutline(true);
        // Keep the grid and shutter flash exactly the size of the picture, not the whole screen.
        texture.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            int w = r - l, h = b - t;
            for (View o : new View[]{grid, flash}) {
                android.view.ViewGroup.LayoutParams lp = o.getLayoutParams();
                if (lp.width != w || lp.height != h) {
                    lp.width = w;
                    lp.height = h;
                    o.setLayoutParams(lp);
                }
            }
        });
        evSeek.setProgressTintList(ColorStateList.valueOf(ACCENT));
        evSeek.setThumbTintList(ColorStateList.valueOf(ACCENT));

        findViewById(R.id.scenesBtn).setOnClickListener(v -> openScenes());
        findViewById(R.id.titleBox).setOnClickListener(v -> showInfo());
        gridBtn.setOnClickListener(v -> {
            grid.setGrid(!grid.isGrid());
            prefs.edit().putBoolean("grid", grid.isGrid()).apply();
            updateGridIcon();
        });
        grid.setGrid(prefs.getBoolean("grid", true));
        updateGridIcon();
        timerBtn.setOnClickListener(v -> {
            timerSec = timerSec == 0 ? 3 : timerSec == 3 ? 10 : 0;
            updateTimerButton();
        });

        modePhoto.setOnClickListener(v -> switchMode(false));
        modeVideo.setOnClickListener(v -> switchMode(true));
        flip.setOnClickListener(v -> {
            if (recording || capturing) return;
            useFront = !useFront;
            focusPoint = null;
            zoom = useFront ? 1f : preset.zoom;
            flip.animate().rotationBy(180f).setDuration(300).start();
            reopen();
        });
        shutter.setOnClickListener(v -> {
            v.animate().scaleX(0.88f).scaleY(0.88f).setDuration(80)
                    .withEndAction(() -> v.animate().scaleX(1f).scaleY(1f).setDuration(120).start()).start();
            onShutter();
        });
        thumb.setOnClickListener(v -> openLast());

        evSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                if (!fromUser) return;
                ev = Math.round((progress + evRange.getLower()) * evStep * 10f) / 10f;
                updateProStrip();
                updateRepeating();
            }
            @Override public void onStartTrackingTouch(SeekBar sb) { }
            @Override public void onStopTrackingTouch(SeekBar sb) { }
        });
        findViewById(R.id.evReset).setOnClickListener(v -> {
            ev = preset.ev;
            syncEvSeek();
            updateProStrip();
            updateRepeating();
        });

        texture.setOnTouchListener((v, e) -> {
            if (e.getAction() == MotionEvent.ACTION_UP) {
                tapToFocus(e.getX(), e.getY(), v.getWidth(), v.getHeight());
                v.performClick();
            }
            return true;
        });

        orientationListener = new OrientationEventListener(this) {
            @Override public void onOrientationChanged(int o) {
                if (o != ORIENTATION_UNKNOWN) deviceOrientation = o;
            }
        };

        cm = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        buildProStrip();
        buildCarousel();
        setPreset(Preset.byId(id), true);

        tipBanner.setOnClickListener(v -> {
            if (!permDenied) return;
            if (shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
                askPermissions();
            } else {
                // "Don't ask again" was chosen: only the app's settings page can grant it now.
                Intent i = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", getPackageName(), null));
                startActivity(i);
            }
        });
    }

    private boolean askingPerms, permDenied;

    private void askPermissions() {
        if (askingPerms) return;
        askingPerms = true;
        requestPermissions(new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO}, REQ_PERMS);
    }

    private void showPermissionNeeded() {
        ui.removeCallbacks(hideTip);
        tipBanner.animate().cancel();
        tipBanner.setAlpha(1f);
        tipBanner.setText("📷  SceneCam needs camera access to work.\nTap here to allow it.");
        tipBanner.setVisibility(View.VISIBLE);
    }

    // =====================================================================
    // Scenes
    // =====================================================================

    /** Applies a scene: its mode, camera, brightness, zoom and timer. */
    private void setPreset(Preset p, boolean first) {
        if (recording || capturing) return;
        boolean oldVideo = videoMode, oldFront = useFront;
        preset = p;
        prefs.edit().putString("scene", p.id).apply();
        if (!p.photo) videoMode = true;
        else if (!p.video) videoMode = false;
        else if (first || p.startInVideo) videoMode = p.startInVideo;
        useFront = p.frontCamera;
        ev = p.ev;
        zoom = p.zoom;
        timerSec = p.timerSec;
        focusPoint = null;

        title.setText(p.emoji + "  " + p.name);
        modePhoto.setVisibility(p.photo ? View.VISIBLE : View.GONE);
        modeVideo.setVisibility(p.video ? View.VISIBLE : View.GONE);
        updateTimerButton();
        updateModeLabels();
        highlightCarousel(!first);
        showTip("💡 " + p.tips);

        if (first) return;
        if (oldVideo != videoMode || oldFront != useFront || device == null) {
            reopen();
        } else {
            zoom = clampZoom(zoom);
            buildZoomButtons();
            syncEvSeek();
            updateRepeating();
            updateProStrip();
        }
    }

    private void openScenes() {
        if (recording) return;
        Intent i = new Intent(this, MainActivity.class);
        i.putExtra(MainActivity.EXTRA_CURRENT, preset.id);
        startActivityForResult(i, REQ_SCENE);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_SCENE && resultCode == RESULT_OK && data != null) {
            String id = data.getStringExtra(EXTRA_PRESET);
            if (id != null) setPreset(Preset.byId(id), false);
        }
    }

    private void buildCarousel() {
        carousel.removeAllViews();
        int side = getResources().getDisplayMetrics().widthPixels / 2;
        carousel.setPadding(side - dp(40), 0, side - dp(40), 0);
        for (Preset p : Preset.all()) {
            TextView t = new TextView(this);
            t.setText(p.shortName);
            t.setTextSize(13);
            t.setLetterSpacing(0.08f);
            t.setTypeface(Typeface.DEFAULT_BOLD);
            t.setGravity(Gravity.CENTER);
            t.setPadding(dp(12), 0, dp(12), 0);
            t.setTag(p.id);
            t.setOnClickListener(v -> {
                if (!p.id.equals(preset.id)) setPreset(p, false);
            });
            carousel.addView(t, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.MATCH_PARENT));
        }
    }

    private void highlightCarousel(boolean animate) {
        View sel = null;
        for (int i = 0; i < carousel.getChildCount(); i++) {
            TextView t = (TextView) carousel.getChildAt(i);
            boolean on = preset.id.equals(t.getTag());
            t.setTextColor(on ? ACCENT : 0xB3FFFFFF);
            if (on) sel = t;
        }
        final View target = sel;
        if (target == null) return;
        carouselScroll.post(() -> {
            int x = target.getLeft() + target.getWidth() / 2 - carouselScroll.getWidth() / 2;
            if (animate) carouselScroll.smoothScrollTo(x, 0); else carouselScroll.scrollTo(x, 0);
        });
    }

    private void showTip(String text) {
        tipBanner.setText(text);
        tipBanner.setVisibility(View.VISIBLE);
        tipBanner.setAlpha(0f);
        tipBanner.animate().alpha(1f).setDuration(200).start();
        ui.removeCallbacks(hideTip);
        ui.postDelayed(hideTip, 5000);
    }

    private final Runnable hideTip = () ->
            tipBanner.animate().alpha(0f).setDuration(300)
                    .withEndAction(() -> tipBanner.setVisibility(View.GONE)).start();

    private void updateGridIcon() {
        gridBtn.setColorFilter(grid.isGrid() ? ACCENT : 0xFFFFFFFF);
    }

    private void updateTimerButton() {
        Drawable d = getDrawable(R.drawable.ic_timer);
        if (d != null) {
            d = d.mutate();
            d.setTint(timerSec > 0 ? ACCENT : 0xFFFFFFFF);
            d.setBounds(0, 0, dp(22), dp(22));
        }
        timerBtn.setCompoundDrawables(d, null, null, null);
        timerBtn.setText(timerSec > 0 ? timerSec + "s" : "");
    }

    // =====================================================================
    // Pro strip: ISO · SPEED · EV · WB · FOCUS  (video: RES · FPS · ISO · SPEED · EV)
    // =====================================================================

    private void buildProStrip() {
        proStrip.removeAllViews();
        for (int i = 0; i < 5; i++) {
            LinearLayout cell = new LinearLayout(this);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER);
            cell.setPadding(0, dp(5), 0, dp(5));
            TextView label = new TextView(this);
            label.setTextSize(9.5f);
            label.setLetterSpacing(0.1f);
            label.setTextColor(0x99FFFFFF);
            label.setGravity(Gravity.CENTER);
            TextView value = new TextView(this);
            value.setTextSize(14);
            value.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            value.setTextColor(0xFFFFFFFF);
            value.setGravity(Gravity.CENTER);
            value.setMaxLines(1);
            cell.addView(label);
            cell.addView(value);
            final int idx = i;
            cell.setOnClickListener(v -> onProCell(idx));
            proLabel[i] = label;
            proValue[i] = value;
            proStrip.addView(cell, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        }
        updateProStrip();
    }

    private void onProCell(int idx) {
        String label = proLabel[idx].getText().toString();
        if (label.startsWith("EV")) {
            boolean open = evPanel.getVisibility() != View.VISIBLE;
            evPanel.setVisibility(open ? View.VISIBLE : View.GONE);
            if (open) syncEvSeek();
        } else if (label.startsWith("RES") && !recording) {
            want4k = !want4k;
            prefs.edit().putBoolean("want4k", want4k).apply();
            reopen();
        } else {
            showInfo();
        }
    }

    private void syncEvSeek() {
        evSeek.setMax(Math.max(0, evRange.getUpper() - evRange.getLower()));
        evSeek.setProgress(evIndex() - evRange.getLower());
    }

    private void setCell(int i, String label, String value, boolean sceneSet) {
        proLabel[i].setText(label);
        proValue[i].setText(value);
        proValue[i].setTextColor(sceneSet ? ACCENT : 0xFFFFFFFF);
    }

    private void updateProStrip() {
        if (preset == null || proValue[4] == null) return;
        long[] plan = recording && recordingExposure != null ? recordingExposure : plannedExposure(videoMode);
        String isoV = plan != null ? String.valueOf(plan[1]) : liveIso > 0 ? String.valueOf(liveIso) : "—";
        String spdV = plan != null ? Fmt.shutter(plan[0]) : liveExp > 0 ? Fmt.shutter(liveExp) : "—";
        String isoL = plan != null ? "ISO" : "ISO · A";
        String spdL = plan != null ? (recording ? "SPEED 🔒" : "SPEED") : "SPEED · A";
        boolean evSet = Math.abs(ev) > 0.05f;
        if (videoMode) {
            String res = videoSize == null ? "—" : videoSize.getWidth() >= 3840 ? "4K" : videoSize.getHeight() + "p";
            setCell(0, "RES", res, true);
            setCell(1, "FPS", String.valueOf(fps), true);
            setCell(2, isoL, isoV, plan != null);
            setCell(3, spdL, spdV, plan != null);
            setCell(4, "EV", Fmt.ev(ev).replace("EV ", ""), evSet);
        } else {
            setCell(0, isoL, isoV, plan != null);
            setCell(1, spdL, spdV, plan != null);
            setCell(2, "EV", Fmt.ev(ev).replace("EV ", ""), evSet);
            boolean wbSet = preset.awb != CameraMetadata.CONTROL_AWB_MODE_AUTO;
            setCell(3, "WB", wbSet ? Fmt.awb(preset.awb).replaceAll("^\\S+ ", "") : "Auto", wbSet);
            String af = focusPoint != null ? "Tap" : (preset.focus == Preset.FOCUS_INFINITY && canFocusManually && !useFront) ? "∞" : "Auto";
            setCell(4, "FOCUS", af, !af.equals("Auto"));
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!hasPerm(Manifest.permission.CAMERA)) {
            if (permDenied) showPermissionNeeded(); else askPermissions();
        } else if (permDenied) {
            permDenied = false;           // granted from Settings
            tipBanner.setVisibility(View.GONE);
        }
        startBg();
        if (orientationListener.canDetectOrientation()) orientationListener.enable();
        if (texture.isAvailable()) {
            openCamera();
        } else {
            texture.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
                @Override public void onSurfaceTextureAvailable(SurfaceTexture s, int w, int h) { openCamera(); }
                @Override public void onSurfaceTextureSizeChanged(SurfaceTexture s, int w, int h) { }
                @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture s) { return true; }
                @Override public void onSurfaceTextureUpdated(SurfaceTexture s) { }
            });
        }
    }

    @Override
    protected void onPause() {
        if (recording) stopRecording();
        ui.removeCallbacksAndMessages(null);
        countdown.setVisibility(View.GONE);
        tipBanner.setVisibility(View.GONE);
        capturing = false;
        orientationListener.disable();
        closeCamera();
        stopBg();
        super.onPause();
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        askingPerms = false;
        if (results == null || results.length == 0) return;   // request was interrupted; ask again on resume
        if (hasPerm(Manifest.permission.CAMERA)) {
            permDenied = false;
            tipBanner.setVisibility(View.GONE);
            if (texture.isAvailable() && device == null) openCamera();
        } else {
            permDenied = true;
            showPermissionNeeded();
        }
    }

    private boolean hasPerm(String p) {
        return checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED;
    }

    private void startBg() {
        if (bgThread != null) return;
        bgThread = new HandlerThread("camera");
        bgThread.start();
        bg = new Handler(bgThread.getLooper());
    }

    private void stopBg() {
        if (bgThread == null) return;
        bgThread.quitSafely();
        try { bgThread.join(1500); } catch (InterruptedException ignored) { }
        bgThread = null;
        bg = null;
    }

    // =====================================================================
    // Open / close camera
    // =====================================================================

    private void openCamera() {
        if (!hasPerm(Manifest.permission.CAMERA) || device != null || opening || bg == null) return;
        try {
            cameraId = findCamera(useFront);
            if (cameraId == null) cameraId = findCamera(!useFront);
            if (cameraId == null) { toast("No camera found"); return; }
            chars = cm.getCameraCharacteristics(cameraId);
            readCapabilities();
            chooseSizes();
            buildZoomButtons();
            syncEvSeek();
            updateModeLabels();

            // Portrait screen: preview buffers are landscape, so swap for the view's aspect.
            texture.setAspect(previewSize.getHeight(), previewSize.getWidth());

            opening = true;
            cm.openCamera(cameraId, new CameraDevice.StateCallback() {
                @Override public void onOpened(CameraDevice d) {
                    opening = false;
                    device = d;
                    createSession();
                }
                @Override public void onDisconnected(CameraDevice d) {
                    opening = false;
                    d.close();
                    device = null;
                }
                @Override public void onError(CameraDevice d, int error) {
                    opening = false;
                    d.close();
                    device = null;
                    toast("Camera error " + error + " — another app may be using the camera.");
                }
            }, bg);
        } catch (SecurityException | CameraAccessException | IllegalArgumentException e) {
            opening = false;
            toast("Could not open camera: " + e.getMessage());
        }
    }

    private String findCamera(boolean front) throws CameraAccessException {
        int want = front ? CameraMetadata.LENS_FACING_FRONT : CameraMetadata.LENS_FACING_BACK;
        for (String id : cm.getCameraIdList()) {
            Integer f = cm.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING);
            if (f != null && f == want) return id;
        }
        return null;
    }

    private void closeCamera() {
        try {
            if (session != null) { session.close(); session = null; }
        } catch (Exception ignored) { }
        if (device != null) { device.close(); device = null; }
        if (jpegReader != null) { jpegReader.close(); jpegReader = null; }
        if (recorderSurface != null) { recorderSurface.release(); recorderSurface = null; }
        if (previewSurface != null) { previewSurface.release(); previewSurface = null; }
        meteredExp = 0;
        meteredIso = 0;
    }

    private void reopen() {
        closeCamera();
        openCamera();
    }

    private void readCapabilities() {
        manualSensor = CamUtil.hasCap(chars, CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR);
        isoRange = chars.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE);
        expRange = chars.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE);
        if (isoRange == null || expRange == null) manualSensor = false;
        Rational step = chars.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_STEP);
        if (step != null && step.floatValue() > 0) evStep = step.floatValue();
        Range<Integer> r = chars.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE);
        if (r != null) evRange = r;
        zoomRange = CamUtil.zoomRange(chars);
        activeArray = chars.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
        Float minFocus = chars.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE);
        canFocusManually = minFocus != null && minFocus > 0
                && CamUtil.hasCap(chars, CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR);
        zoom = clampZoom(zoom);
    }

    private void chooseSizes() {
        StreamConfigurationMap map = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        if (map == null) throw new IllegalArgumentException("No stream configurations");
        jpegSize = CamUtil.pick(map.getOutputSizes(ImageFormat.JPEG), 4, 3, 100000);
        if (!videoMode) {
            previewSize = CamUtil.pick(map.getOutputSizes(SurfaceTexture.class), 4, 3, 1920);
            return;
        }
        previewSize = CamUtil.pick(map.getOutputSizes(SurfaceTexture.class), 16, 9, 1920);

        Size uhd = new Size(3840, 2160), fhd = new Size(1920, 1080);
        int wantFps = preset.fps;
        int[][] tries = want4k
                ? new int[][]{{wantFps, 1}, {wantFps, 0}, {30, 1}, {30, 0}}
                : new int[][]{{wantFps, 0}, {30, 0}};
        videoSize = null;
        for (int[] t : tries) {
            Size s = t[1] == 1 ? uhd : fhd;
            if (videoOk(map, s, t[0])) {
                videoSize = s;
                fps = t[0];
                fpsRange = pickFpsRange(fps);
                break;
            }
        }
        if (videoSize == null) {   // last resort: biggest 16:9 the recorder takes, at 30 fps
            videoSize = CamUtil.pick(map.getOutputSizes(MediaRecorder.class), 16, 9, 1920);
            fps = 30;
            fpsRange = pickFpsRange(30);
        }
        StringBuilder note = new StringBuilder();
        if (fps != wantFps) note.append("Your phone can't record ").append(wantFps).append(" fps here, using ").append(fps).append(" fps. ");
        if (want4k && videoSize.getWidth() < 3840) note.append("4K isn't available at this frame rate, using ").append(videoSize.getHeight()).append("p. ");
        videoNote = note.toString();
    }

    private boolean videoOk(StreamConfigurationMap map, Size s, int f) {
        Size[] sizes = map.getOutputSizes(MediaRecorder.class);
        if (sizes == null || !Arrays.asList(sizes).contains(s)) return false;
        long minDur;
        try { minDur = map.getOutputMinFrameDuration(MediaRecorder.class, s); }
        catch (Exception e) { return false; }
        if (minDur > 0 && minDur > 1_000_000_000L / f + 100_000L) return false;
        return pickFpsRange(f) != null;
    }

    private Range<Integer> pickFpsRange(int f) {
        Range<Integer>[] ranges = chars.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES);
        if (ranges == null) return null;
        Range<Integer> loose = null;
        for (Range<Integer> r : ranges) {
            if (r.getLower() == f && r.getUpper() == f) return r;
            if (r.getUpper() == f && (loose == null || r.getLower() > loose.getLower())) loose = r;
        }
        return loose;
    }

    // =====================================================================
    // Session + requests
    // =====================================================================

    private void createSession() {
        if (device == null || !texture.isAvailable()) return;
        try {
            SurfaceTexture st = texture.getSurfaceTexture();
            st.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());
            previewSurface = new Surface(st);
            List<Surface> targets = new ArrayList<>();
            targets.add(previewSurface);

            if (videoMode) {
                recorderSurface = MediaCodec.createPersistentInputSurface();
                // Configure the surface's size by preparing a throw-away recorder once (Google's Camera2Video approach).
                MediaRecorder dummy = newRecorder();
                File f = new File(getCacheDir(), "warmup.mp4");
                configureRecorder(dummy, false, f.getAbsolutePath(), null);
                dummy.prepare();
                dummy.release();
                targets.add(recorderSurface);
            } else {
                jpegReader = ImageReader.newInstance(jpegSize.getWidth(), jpegSize.getHeight(), ImageFormat.JPEG, 2);
                jpegReader.setOnImageAvailableListener(this::onJpeg, bg);
                targets.add(jpegReader.getSurface());
            }

            device.createCaptureSession(targets, new CameraCaptureSession.StateCallback() {
                @Override public void onConfigured(CameraCaptureSession s) {
                    if (device == null) return;
                    session = s;
                    updateRepeating();
                }
                @Override public void onConfigureFailed(CameraCaptureSession s) {
                    if (videoMode && want4k) {
                        // Some phones list 4K but can't combine it with preview; drop to 1080p.
                        runOnUiThread(() -> { want4k = false; reopen(); });
                    } else {
                        toast("This camera setup isn't supported on your phone.");
                    }
                }
            }, bg);
        } catch (Exception e) {
            toast("Camera setup failed: " + e.getMessage());
        }
    }

    /** (Re)builds the live preview / recording request from the current state. */
    private void updateRepeating() {
        if (session == null || device == null) return;
        try {
            CaptureRequest.Builder b = device.createCaptureRequest(
                    videoMode ? CameraDevice.TEMPLATE_RECORD : CameraDevice.TEMPLATE_PREVIEW);
            b.addTarget(previewSurface);
            applyCommon(b, false);
            if (videoMode) {
                if (fpsRange != null) b.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, fpsRange);
                if (recording) {
                    b.addTarget(recorderSurface);
                    if (recordingExposure != null) applyManualExposure(b, recordingExposure, 1_000_000_000L / fps);
                }
            }
            session.setRepeatingRequest(b.build(), previewCallback, bg);
        } catch (CameraAccessException | IllegalStateException | IllegalArgumentException e) {
            // session closed while switching; ignore
        }
    }

    private void applyCommon(CaptureRequest.Builder b, boolean still) {
        b.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
        b.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_ON);

        // India runs on 50 Hz power: this stops tube light / LED flicker and banding.
        int[] ab = chars.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_ANTIBANDING_MODES);
        if (contains(ab, CameraMetadata.CONTROL_AE_ANTIBANDING_MODE_50HZ)) {
            b.set(CaptureRequest.CONTROL_AE_ANTIBANDING_MODE, CameraMetadata.CONTROL_AE_ANTIBANDING_MODE_50HZ);
        }

        b.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, evIndex());

        int[] awbModes = chars.get(CameraCharacteristics.CONTROL_AWB_AVAILABLE_MODES);
        b.set(CaptureRequest.CONTROL_AWB_MODE,
                contains(awbModes, preset.awb) ? preset.awb : CameraMetadata.CONTROL_AWB_MODE_AUTO);

        applyFocus(b);
        applyZoom(b);

        if (videoMode && preset.stabilize) {
            int[] vs = chars.get(CameraCharacteristics.CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES);
            if (contains(vs, CameraMetadata.CONTROL_VIDEO_STABILIZATION_MODE_ON)) {
                b.set(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE, CameraMetadata.CONTROL_VIDEO_STABILIZATION_MODE_ON);
            }
        }
        int[] ois = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION);
        if (contains(ois, CameraMetadata.LENS_OPTICAL_STABILIZATION_MODE_ON)) {
            b.set(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE, CameraMetadata.LENS_OPTICAL_STABILIZATION_MODE_ON);
        }

        if (still) {
            int[] nr = chars.get(CameraCharacteristics.NOISE_REDUCTION_AVAILABLE_NOISE_REDUCTION_MODES);
            if (preset.highQuality && contains(nr, CameraMetadata.NOISE_REDUCTION_MODE_HIGH_QUALITY)) {
                b.set(CaptureRequest.NOISE_REDUCTION_MODE, CameraMetadata.NOISE_REDUCTION_MODE_HIGH_QUALITY);
            }
            int[] edge = chars.get(CameraCharacteristics.EDGE_AVAILABLE_EDGE_MODES);
            if (preset.highQuality && contains(edge, CameraMetadata.EDGE_MODE_HIGH_QUALITY)) {
                b.set(CaptureRequest.EDGE_MODE, CameraMetadata.EDGE_MODE_HIGH_QUALITY);
            }
            b.set(CaptureRequest.JPEG_QUALITY, (byte) 95);
        }
    }

    private void applyFocus(CaptureRequest.Builder b) {
        int[] afModes = chars.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES);
        if (focusPoint != null && contains(afModes, CameraMetadata.CONTROL_AF_MODE_AUTO)) {
            MeteringRectangle[] region = regionFor(focusPoint[0], focusPoint[1]);
            b.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_AUTO);
            Integer maxAf = chars.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AF);
            Integer maxAe = chars.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AE);
            if (region != null && maxAf != null && maxAf > 0) b.set(CaptureRequest.CONTROL_AF_REGIONS, region);
            if (region != null && maxAe != null && maxAe > 0) b.set(CaptureRequest.CONTROL_AE_REGIONS, region);
            return;
        }
        if (preset.focus == Preset.FOCUS_INFINITY && canFocusManually && !useFront) {
            b.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_OFF);
            b.set(CaptureRequest.LENS_FOCUS_DISTANCE, 0f);
            return;
        }
        int want = videoMode ? CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_VIDEO : CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE;
        if (contains(afModes, want)) b.set(CaptureRequest.CONTROL_AF_MODE, want);
        else if (contains(afModes, CameraMetadata.CONTROL_AF_MODE_AUTO)) b.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_AUTO);
    }

    private void applyZoom(CaptureRequest.Builder b) {
        if (Build.VERSION.SDK_INT >= 30 && chars.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE) != null) {
            b.set(CaptureRequest.CONTROL_ZOOM_RATIO, zoom);
        } else if (activeArray != null && zoom > 1.01f) {
            int w = (int) (activeArray.width() / zoom), h = (int) (activeArray.height() / zoom);
            int l = activeArray.left + (activeArray.width() - w) / 2, t = activeArray.top + (activeArray.height() - h) / 2;
            b.set(CaptureRequest.SCALER_CROP_REGION, new Rect(l, t, l + w, t + h));
        }
    }

    private void applyManualExposure(CaptureRequest.Builder b, long[] exp, long maxFrameNs) {
        b.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_OFF);
        b.set(CaptureRequest.SENSOR_EXPOSURE_TIME, exp[0]);
        b.set(CaptureRequest.SENSOR_SENSITIVITY, (int) exp[1]);
        b.set(CaptureRequest.SENSOR_FRAME_DURATION, Math.max(exp[0], maxFrameNs));
    }

    private final CameraCaptureSession.CaptureCallback previewCallback = new CameraCaptureSession.CaptureCallback() {
        @Override
        public void onCaptureCompleted(CameraCaptureSession s, CaptureRequest req, TotalCaptureResult r) {
            Long t = r.get(CaptureResult.SENSOR_EXPOSURE_TIME);
            Integer iso = r.get(CaptureResult.SENSOR_SENSITIVITY);
            Integer ae = r.get(CaptureResult.CONTROL_AE_MODE);
            if (t != null && iso != null) {
                liveExp = t;
                liveIso = iso;
                if (ae == null || ae != CameraMetadata.CONTROL_AE_MODE_OFF) {
                    meteredExp = t;
                    meteredIso = iso;
                }
            }
            long now = SystemClock.uptimeMillis();
            if (now - lastReadoutMs > 350) {
                lastReadoutMs = now;
                runOnUiThread(CameraActivity.this::updateProStrip);
            }
        }
    };

    // =====================================================================
    // Exposure maths (the "pro" part)
    // =====================================================================

    /**
     * Works out {shutterNs, iso} for the shot, or null to let the camera decide.
     * The live preview meters the scene; we keep the same overall brightness but
     * trade ISO for shutter speed the way the preset wants.
     */
    private long[] plannedExposure(boolean forVideo) {
        if (!manualSensor || useFront) return null;
        long isoMin = isoRange.getLower(), isoMax = isoRange.getUpper();
        long tMin = expRange.getLower(), tMax = expRange.getUpper();

        long target;
        long cap;
        long longest;
        if (forVideo) {
            if (preset.vidExp != Preset.VID_CINE) return null;
            target = 1_000_000_000L / (2L * fps);       // 180° shutter rule
            cap = Math.min(3200, isoMax);
            longest = 1_000_000_000L / fps;
        } else if (preset.photoExp == Preset.EXP_MANUAL) {
            long t = Math.max(tMin, Math.min(preset.shutterNs, tMax));
            double shift = Math.pow(2, ev - preset.ev);  // user's +/- nudges ISO
            long iso = preset.iso > 0 ? preset.iso : isoMin;
            iso = Math.max(isoMin, Math.min(isoMax, Math.round(iso * shift)));
            return new long[]{t, iso};
        } else if (preset.photoExp == Preset.EXP_SHUTTER) {
            target = preset.shutterNs;
            cap = Math.min(preset.maxIso, isoMax);
            longest = Math.max(target, Math.min(target * 4, 1_000_000_000L));
        } else {
            return null;
        }

        if (meteredExp <= 0 || meteredIso <= 0) return null;     // not metered yet → auto
        double light = (double) meteredExp * meteredIso;          // exposure the scene needs (includes EV)

        long t = Math.max(tMin, Math.min(target, tMax));
        double iso = light / t;
        if (iso < isoMin) {            // too bright for this shutter → go faster instead of over-exposing
            iso = isoMin;
            t = (long) (light / isoMin);
        } else if (iso > cap) {        // too dark → limit ISO noise, allow a bit slower shutter
            iso = cap;
            t = (long) Math.min(light / cap, longest);
        }
        t = Math.max(tMin, Math.min(t, Math.min(tMax, forVideo ? longest : tMax)));
        long isoL = Math.max(isoMin, Math.min(isoMax, Math.round(iso)));
        return new long[]{t, isoL};
    }

    // =====================================================================
    // Photo
    // =====================================================================

    private void onShutter() {
        if (videoMode) {
            if (recording) stopRecording(); else startRecording();
            return;
        }
        if (capturing) return;
        capturing = true;
        if (timerSec > 0) runCountdown(timerSec);
        else takePhoto();
    }

    private void runCountdown(int n) {
        if (n <= 0) {
            countdown.setVisibility(View.GONE);
            takePhoto();
            return;
        }
        countdown.setVisibility(View.VISIBLE);
        countdown.setText(String.valueOf(n));
        ui.postDelayed(() -> runCountdown(n - 1), 1000);
    }

    private void takePhoto() {
        if (session == null || device == null || jpegReader == null) { capturing = false; return; }
        try {
            CaptureRequest.Builder b = device.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            b.addTarget(jpegReader.getSurface());
            applyCommon(b, true);
            b.set(CaptureRequest.JPEG_ORIENTATION, CamUtil.outputRotation(chars, deviceOrientation));
            long[] exp = plannedExposure(false);
            if (exp != null) applyManualExposure(b, exp, 0);

            flashScreen();
            if (exp != null && exp[0] > 300_000_000L) {
                final String msg = "⏳ Long exposure " + Fmt.shutter(exp[0]) + " — keep the phone completely still…";
                runOnUiThread(() -> showTip(msg));
            }
            session.capture(b.build(), new CameraCaptureSession.CaptureCallback() {
                @Override public void onCaptureFailed(CameraCaptureSession s, CaptureRequest r, android.hardware.camera2.CaptureFailure f) {
                    capturing = false;
                    toast("Capture failed, try again.");
                }
            }, bg);
        } catch (CameraAccessException | IllegalStateException e) {
            capturing = false;
            toast("Capture failed: " + e.getMessage());
        }
    }

    private void onJpeg(ImageReader reader) {
        Image img = null;
        try {
            img = reader.acquireNextImage();
            if (img == null) return;
            ByteBuffer buf = img.getPlanes()[0].getBuffer();
            byte[] data = new byte[buf.remaining()];
            buf.get(data);
            img.close();
            img = null;

            ContentResolver cr = getContentResolver();
            ContentValues v = new ContentValues();
            v.put(MediaStore.MediaColumns.DISPLAY_NAME, "SceneCam_" + stamp() + ".jpg");
            v.put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg");
            v.put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/SceneCam");
            v.put(MediaStore.MediaColumns.IS_PENDING, 1);
            Uri uri = cr.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v);
            if (uri == null) throw new IllegalStateException("could not create file");
            try (OutputStream os = cr.openOutputStream(uri)) {
                if (os == null) throw new IllegalStateException("could not open file");
                os.write(data);
            }
            v.clear();
            v.put(MediaStore.MediaColumns.IS_PENDING, 0);
            cr.update(uri, v, null, null);
            onSaved(uri, false);
        } catch (Exception e) {
            toast("Couldn't save photo: " + e.getMessage());
        } finally {
            if (img != null) img.close();
            capturing = false;
        }
    }

    private void flashScreen() {
        runOnUiThread(() -> {
            flash.setAlpha(0.85f);
            flash.animate().alpha(0f).setDuration(220).start();
        });
    }

    // =====================================================================
    // Video
    // =====================================================================

    private MediaRecorder newRecorder() {
        return Build.VERSION.SDK_INT >= 31 ? new MediaRecorder(this) : new MediaRecorder();
    }

    private void configureRecorder(MediaRecorder r, boolean audio, String path, ParcelFileDescriptor pfd) {
        if (audio) r.setAudioSource(MediaRecorder.AudioSource.CAMCORDER);
        r.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        if (pfd != null) r.setOutputFile(pfd.getFileDescriptor());
        else r.setOutputFile(path);
        boolean uhd = videoSize.getWidth() >= 3840;
        int bitrate = uhd ? (fps > 30 ? 75_000_000 : 50_000_000) : (fps > 30 ? 30_000_000 : 20_000_000);
        r.setVideoEncodingBitRate(bitrate);
        r.setVideoFrameRate(fps);
        r.setVideoSize(videoSize.getWidth(), videoSize.getHeight());
        r.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        if (audio) {
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            r.setAudioEncodingBitRate(192_000);
            r.setAudioSamplingRate(48_000);
        }
        r.setOrientationHint(CamUtil.outputRotation(chars, deviceOrientation));
        r.setInputSurface(recorderSurface);
    }

    private void startRecording() {
        if (session == null || device == null || recorderSurface == null) return;
        try {
            ContentResolver cr = getContentResolver();
            ContentValues v = new ContentValues();
            v.put(MediaStore.MediaColumns.DISPLAY_NAME, "SceneCam_" + stamp() + ".mp4");
            v.put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4");
            v.put(MediaStore.MediaColumns.RELATIVE_PATH, "Movies/SceneCam");
            v.put(MediaStore.MediaColumns.IS_PENDING, 1);
            videoUri = cr.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, v);
            if (videoUri == null) throw new IllegalStateException("could not create video file");
            videoPfd = cr.openFileDescriptor(videoUri, "w");

            recorder = newRecorder();
            configureRecorder(recorder, hasPerm(Manifest.permission.RECORD_AUDIO), null, videoPfd);
            recorder.prepare();

            recordingExposure = plannedExposure(true);
            recording = true;
            updateRepeating();
            recorder.start();
            recStartMs = SystemClock.uptimeMillis();
            setRecordingUi(true);
            tickRecTime();
        } catch (Exception e) {
            recording = false;
            recordingExposure = null;
            cleanupRecorder(true);
            updateRepeating();
            toast("Couldn't start recording: " + e.getMessage());
        }
    }

    private void stopRecording() {
        if (!recording) return;
        recording = false;
        boolean ok = true;
        try {
            recorder.stop();
        } catch (RuntimeException e) {
            ok = false;   // stopped too quickly, no frames
        }
        Uri uri = videoUri;
        cleanupRecorder(!ok);
        recordingExposure = null;
        setRecordingUi(false);
        updateRepeating();
        if (ok && uri != null) {
            ContentValues v = new ContentValues();
            v.put(MediaStore.MediaColumns.IS_PENDING, 0);
            getContentResolver().update(uri, v, null, null);
            final Uri saved = uri;
            if (bg != null) bg.post(() -> onSaved(saved, true));
            else onSaved(saved, true);
        } else {
            toast("Video too short — hold record a little longer.");
        }
    }

    private void cleanupRecorder(boolean deleteFile) {
        if (recorder != null) {
            try { recorder.reset(); } catch (Exception ignored) { }
            recorder.release();
            recorder = null;
        }
        if (videoPfd != null) {
            try { videoPfd.close(); } catch (Exception ignored) { }
            videoPfd = null;
        }
        if (deleteFile && videoUri != null) {
            try { getContentResolver().delete(videoUri, null, null); } catch (Exception ignored) { }
        }
        videoUri = null;
    }

    private void setRecordingUi(boolean on) {
        runOnUiThread(() -> {
            shutter.setBackgroundResource(on ? R.drawable.shutter_stop : R.drawable.shutter_video);
            recTime.setVisibility(on ? View.VISIBLE : View.GONE);
            float a = on ? 0.25f : 1f;
            modeSeg.setAlpha(a);
            carouselScroll.setAlpha(a);
            flip.setAlpha(a);
            thumb.setAlpha(a);
            updateProStrip();
        });
    }

    private void tickRecTime() {
        if (!recording) return;
        long s = (SystemClock.uptimeMillis() - recStartMs) / 1000;
        recTime.setText(String.format(Locale.US, "%s %02d:%02d", (s % 2 == 0) ? "🔴" : "⚫", s / 60, s % 60));
        ui.postDelayed(this::tickRecTime, 500);
    }

    // =====================================================================
    // Saved media
    // =====================================================================

    private void onSaved(Uri uri, boolean isVideo) {
        Bitmap bmp = null;
        try {
            bmp = getContentResolver().loadThumbnail(uri, new Size(160, 160), null);
        } catch (Exception ignored) { }
        final Bitmap fb = bmp;
        runOnUiThread(() -> {
            lastUri = uri;
            lastIsVideo = isVideo;
            if (fb != null) thumb.setImageBitmap(fb);
            Toast.makeText(this, isVideo ? "Video saved" : "Photo saved", Toast.LENGTH_SHORT).show();
        });
    }

    private void openLast() {
        if (lastUri == null || recording) return;
        try {
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(lastUri, lastIsVideo ? "video/*" : "image/*");
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(i);
        } catch (Exception e) {
            toast("No gallery app found");
        }
    }

    // =====================================================================
    // Controls
    // =====================================================================

    private void switchMode(boolean video) {
        if (recording || capturing || video == videoMode) return;
        videoMode = video;
        focusPoint = null;
        updateModeLabels();
        reopen();
    }

    private void updateModeLabels() {
        modePhoto.setBackground(videoMode ? null : getDrawable(R.drawable.segment_on));
        modeVideo.setBackground(videoMode ? getDrawable(R.drawable.segment_on) : null);
        modePhoto.setTextColor(videoMode ? 0xFFFFFFFF : 0xFF111111);
        modeVideo.setTextColor(videoMode ? 0xFF111111 : 0xFFFFFFFF);
        if (!recording) shutter.setBackgroundResource(videoMode ? R.drawable.shutter_video : R.drawable.shutter_photo);
        updateProStrip();
    }

    private int evIndex() {
        if (evStep <= 0) return 0;
        int idx = Math.round(ev / evStep);
        return Math.max(evRange.getLower(), Math.min(evRange.getUpper(), idx));
    }

    private float clampZoom(float z) {
        return Math.max(zoomRange.getLower(), Math.min(zoomRange.getUpper(), z));
    }

    private void buildZoomButtons() {
        runOnUiThread(() -> {
            zoomRow.removeAllViews();
            List<Float> stops = new ArrayList<>();
            float lo = zoomRange.getLower(), hi = zoomRange.getUpper();
            if (lo < 0.95f) stops.add(lo);
            stops.add(1f);
            for (float z : new float[]{2f, 3f, 5f, 10f}) if (z <= hi + 0.01f) stops.add(z);
            if (stops.size() < 2) { zoomRow.setVisibility(View.INVISIBLE); return; }
            zoomRow.setVisibility(View.VISIBLE);
            for (Float z : stops) {
                TextView b = new TextView(this);
                b.setTextSize(11.5f);
                b.setTypeface(Typeface.DEFAULT_BOLD);
                b.setGravity(Gravity.CENTER);
                b.setTag(z);
                b.setOnClickListener(v -> {
                    zoom = clampZoom((Float) v.getTag());
                    focusPoint = null;
                    refreshZoomColors();
                    updateRepeating();
                    updateProStrip();
                });
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(34), dp(34));
                lp.leftMargin = dp(2);
                lp.rightMargin = dp(2);
                zoomRow.addView(b, lp);
            }
            refreshZoomColors();
        });
    }

    private void refreshZoomColors() {
        for (int i = 0; i < zoomRow.getChildCount(); i++) {
            TextView t = (TextView) zoomRow.getChildAt(i);
            float z = (Float) t.getTag();
            boolean on = Math.abs(z - zoom) < 0.05f;
            String label = Fmt.zoom(z).replace("x", "");
            t.setText(on ? label + "×" : label);
            t.setTextColor(on ? ACCENT : 0xFFFFFFFF);
            t.setBackgroundResource(on ? R.drawable.zoom_on : R.drawable.zoom_off);
        }
    }

    private void tapToFocus(float x, float y, int w, int h) {
        if (session == null || device == null || w == 0 || h == 0) return;
        focusPoint = new float[]{x / w, y / h};
        grid.showFocus(x, y);
        updateProStrip();
        try {
            updateRepeating();
            CaptureRequest.Builder b = device.createCaptureRequest(
                    videoMode ? CameraDevice.TEMPLATE_RECORD : CameraDevice.TEMPLATE_PREVIEW);
            b.addTarget(previewSurface);
            applyCommon(b, false);
            if (videoMode && fpsRange != null) b.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, fpsRange);
            if (recording) {
                b.addTarget(recorderSurface);
                if (recordingExposure != null) applyManualExposure(b, recordingExposure, 1_000_000_000L / fps);
            }
            b.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_START);
            session.capture(b.build(), null, bg);
        } catch (Exception ignored) { }
    }

    /** Converts a tap on the portrait preview into a sensor metering region. */
    private MeteringRectangle[] regionFor(float nx, float ny) {
        if (activeArray == null) return null;
        Integer so = chars.get(CameraCharacteristics.SENSOR_ORIENTATION);
        int sensor = so == null ? 90 : so;
        float sx, sy;
        switch (sensor) {
            case 90: sx = ny; sy = 1 - nx; break;
            case 270: sx = 1 - ny; sy = nx; break;
            case 180: sx = 1 - nx; sy = 1 - ny; break;
            default: sx = nx; sy = ny;
        }
        Rect area = new Rect(0, 0, activeArray.width(), activeArray.height());
        boolean zoomRatioApi = Build.VERSION.SDK_INT >= 30
                && chars.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE) != null;
        if (!zoomRatioApi && zoom > 1.01f) {
            int cw = (int) (area.width() / zoom), ch = (int) (area.height() / zoom);
            area = new Rect((area.width() - cw) / 2, (area.height() - ch) / 2,
                    (area.width() - cw) / 2 + cw, (area.height() - ch) / 2 + ch);
        }
        int half = area.width() / 14;
        int cx = area.left + (int) (sx * area.width());
        int cy = area.top + (int) (sy * area.height());
        int l = Math.max(0, cx - half), t = Math.max(0, cy - half);
        int r = Math.min(activeArray.width() - 1, cx + half), btm = Math.min(activeArray.height() - 1, cy + half);
        if (r <= l || btm <= t) return null;
        return new MeteringRectangle[]{new MeteringRectangle(l, t, r - l, btm - t, MeteringRectangle.METERING_WEIGHT_MAX - 1)};
    }

    private void showInfo() {
        StringBuilder sb = new StringBuilder();
        sb.append("WHY THESE SETTINGS\n").append(preset.why).append("\n\n");
        sb.append("HOW TO SHOOT IT\n").append(preset.tips).append("\n\n");
        sb.append("APPLIED\n").append(Fmt.summary(preset)).append("\n\n");
        if (chars != null) {
            if (!manualSensor) {
                sb.append("Your phone doesn't let other apps set shutter/ISO manually, so SceneCam uses smart auto with this scene's brightness, colour, focus and zoom.\n\n");
            } else if (preset.photoExp != Preset.EXP_AUTO && expRange != null && preset.shutterNs > expRange.getUpper()) {
                sb.append("Your phone's longest shutter for apps is ").append(Fmt.shutter(expRange.getUpper()))
                  .append(", so that is used instead of ").append(Fmt.shutter(preset.shutterNs)).append(".\n\n");
            }
            if (videoMode && !videoNote.isEmpty()) sb.append(videoNote).append("\n\n");
        }
        sb.append("Tap the preview to focus and meter on that spot. Tap EV to make it brighter or darker. Amber values are set by this scene.");
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle(preset.emoji + " " + preset.name)
                .setMessage(sb.toString())
                .setPositiveButton("Got it", null)
                .show();
    }

    // =====================================================================
    // Helpers
    // =====================================================================

    private static boolean contains(int[] arr, int v) {
        if (arr == null) return false;
        for (int x : arr) if (x == v) return true;
        return false;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private static String stamp() {
        return new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
    }

    private void toast(String msg) {
        runOnUiThread(() -> Toast.makeText(this, msg, Toast.LENGTH_LONG).show());
    }
}
