package com.sushil.scenecam;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
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
    private static final int REQ_PERMS = 7;

    // ---- views
    private AutoFitTextureView texture;
    private GridOverlay grid;
    private TextView title, readout, tip, evText, modePhoto, modeVideo, quality, countdown, recTime, flip;
    private View shutter;
    private ImageView thumb;
    private LinearLayout zoomRow;

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

        preset = Preset.byId(getIntent().getStringExtra(EXTRA_PRESET));
        videoMode = preset.startInVideo || !preset.photo;
        useFront = preset.frontCamera;
        ev = preset.ev;
        zoom = preset.zoom;

        texture = findViewById(R.id.texture);
        grid = findViewById(R.id.grid);
        title = findViewById(R.id.title);
        readout = findViewById(R.id.readout);
        tip = findViewById(R.id.tip);
        evText = findViewById(R.id.evText);
        modePhoto = findViewById(R.id.modePhoto);
        modeVideo = findViewById(R.id.modeVideo);
        quality = findViewById(R.id.quality);
        countdown = findViewById(R.id.countdown);
        recTime = findViewById(R.id.recTime);
        shutter = findViewById(R.id.shutter);
        thumb = findViewById(R.id.thumb);
        zoomRow = findViewById(R.id.zoomRow);
        flip = findViewById(R.id.flip);

        title.setText(preset.emoji + "  " + preset.name);
        tip.setText("💡 " + preset.tips);

        findViewById(R.id.back).setOnClickListener(v -> finish());
        findViewById(R.id.info).setOnClickListener(v -> showInfo());
        findViewById(R.id.gridBtn).setOnClickListener(v -> {
            grid.setGrid(!grid.isGrid());
            ((TextView) v).setTextColor(grid.isGrid() ? 0xFFFFB547 : 0xFFFFFFFF);
        });
        findViewById(R.id.evMinus).setOnClickListener(v -> changeEv(-1));
        findViewById(R.id.evPlus).setOnClickListener(v -> changeEv(+1));

        modePhoto.setVisibility(preset.photo ? View.VISIBLE : View.GONE);
        modeVideo.setVisibility(preset.video ? View.VISIBLE : View.GONE);
        modePhoto.setOnClickListener(v -> switchMode(false));
        modeVideo.setOnClickListener(v -> switchMode(true));
        quality.setOnClickListener(v -> {
            if (recording) return;
            want4k = !want4k;
            reopen();
        });
        flip.setOnClickListener(v -> {
            if (recording || capturing) return;
            useFront = !useFront;
            focusPoint = null;
            zoom = useFront ? 1f : preset.zoom;
            reopen();
        });
        shutter.setOnClickListener(v -> onShutter());
        thumb.setOnClickListener(v -> openLast());

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
        updateEvText();
        updateModeLabels();

        if (!hasPerm(Manifest.permission.CAMERA)) {
            requestPermissions(new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO}, REQ_PERMS);
        } else if (!hasPerm(Manifest.permission.RECORD_AUDIO)) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_PERMS);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
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
        capturing = false;
        orientationListener.disable();
        closeCamera();
        stopBg();
        super.onPause();
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        if (!hasPerm(Manifest.permission.CAMERA)) {
            Toast.makeText(this, "SceneCam needs camera permission to work.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        if (texture.isAvailable() && device == null) openCamera();
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
                runOnUiThread(CameraActivity.this::updateReadout);
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
        if (preset.timerSec > 0) runCountdown(preset.timerSec);
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
                tip.setText("⏳ Long exposure " + Fmt.shutter(exp[0]) + " — keep the phone completely still…");
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
            runOnUiThread(() -> tip.setText("💡 " + preset.tips));
        }
    }

    private void flashScreen() {
        runOnUiThread(() -> {
            texture.setAlpha(0.2f);
            texture.animate().alpha(1f).setDuration(250).start();
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
            shutter.setBackgroundResource(on ? R.drawable.shutter_stop : R.drawable.shutter_rec);
            recTime.setVisibility(on ? View.VISIBLE : View.GONE);
            float a = on ? 0.3f : 1f;
            modePhoto.setAlpha(a);
            quality.setAlpha(a);
            flip.setAlpha(a);
        });
    }

    private void tickRecTime() {
        if (!recording) return;
        long s = (SystemClock.uptimeMillis() - recStartMs) / 1000;
        recTime.setText(String.format(Locale.US, "● %02d:%02d", s / 60, s % 60));
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
        modePhoto.setTextColor(videoMode ? 0xFFFFFFFF : 0xFFFFB547);
        modeVideo.setTextColor(videoMode ? 0xFFFFB547 : 0xFFFFFFFF);
        quality.setVisibility(videoMode ? View.VISIBLE : View.GONE);
        if (videoMode && videoSize != null) {
            String q = videoSize.getWidth() >= 3840 ? "4K" : videoSize.getHeight() + "p";
            quality.setText(q + " · " + fps + " fps  ⇄");
        }
        if (!recording) shutter.setBackgroundResource(videoMode ? R.drawable.shutter_rec : R.drawable.shutter_bg);
    }

    private void changeEv(int dir) {
        float stepStops = Math.max(evStep, 1f / 3f);
        float n = ev + dir * stepStops;
        float lo = evRange.getLower() * evStep, hi = evRange.getUpper() * evStep;
        if (hi > lo) n = Math.max(lo, Math.min(hi, n));
        ev = Math.round(n * 10f) / 10f;
        updateEvText();
        updateRepeating();
    }

    private int evIndex() {
        if (evStep <= 0) return 0;
        int idx = Math.round(ev / evStep);
        return Math.max(evRange.getLower(), Math.min(evRange.getUpper(), idx));
    }

    private void updateEvText() {
        evText.setText(Fmt.ev(ev));
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
            if (stops.size() < 2) { zoomRow.setVisibility(View.GONE); return; }
            zoomRow.setVisibility(View.VISIBLE);
            for (Float z : stops) {
                TextView b = new TextView(this);
                b.setText(Fmt.zoom(z));
                b.setTextSize(13);
                b.setPadding(dp(9), 0, dp(9), 0);
                b.setGravity(android.view.Gravity.CENTER);
                b.setTag(z);
                b.setOnClickListener(v -> {
                    zoom = clampZoom((Float) v.getTag());
                    focusPoint = null;
                    refreshZoomColors();
                    updateRepeating();
                });
                zoomRow.addView(b, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.MATCH_PARENT));
            }
            refreshZoomColors();
        });
    }

    private void refreshZoomColors() {
        for (int i = 0; i < zoomRow.getChildCount(); i++) {
            TextView t = (TextView) zoomRow.getChildAt(i);
            boolean on = Math.abs((Float) t.getTag() - zoom) < 0.05f;
            t.setTextColor(on ? 0xFFFFB547 : 0xFFFFFFFF);
            t.setTypeface(null, on ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
        }
    }

    private void tapToFocus(float x, float y, int w, int h) {
        if (session == null || device == null || w == 0 || h == 0) return;
        focusPoint = new float[]{x / w, y / h};
        grid.showFocus(x, y);
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

    private void updateReadout() {
        StringBuilder sb = new StringBuilder();
        if (videoMode && recording && recordingExposure != null) {
            sb.append("🔒 ").append(Fmt.iso((int) recordingExposure[1])).append(" · ").append(Fmt.shutter(recordingExposure[0]));
        } else if (liveIso > 0) {
            sb.append("Live: ").append(Fmt.iso(liveIso)).append(" · ").append(Fmt.shutter(liveExp));
            long[] plan = plannedExposure(videoMode);
            if (plan != null) {
                sb.append("   →  ").append(videoMode ? "Video" : "Photo").append(": ")
                  .append(Fmt.iso((int) plan[1])).append(" · ").append(Fmt.shutter(plan[0]));
            }
        }
        if (focusPoint != null) sb.append("  · 🎯 locked");
        readout.setText(sb.toString());
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
        sb.append("Tap the preview to focus and meter on that spot. Use − / + to make it darker or brighter.");
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
