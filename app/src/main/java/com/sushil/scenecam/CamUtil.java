package com.sushil.scenecam;

import android.hardware.camera2.CameraCharacteristics;
import android.os.Build;
import android.util.Range;
import android.util.Size;
import android.view.OrientationEventListener;

final class CamUtil {
    private CamUtil() {}

    static boolean hasCap(CameraCharacteristics c, int cap) {
        int[] caps = c.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES);
        if (caps == null) return false;
        for (int x : caps) if (x == cap) return true;
        return false;
    }

    static Range<Float> zoomRange(CameraCharacteristics c) {
        if (Build.VERSION.SDK_INT >= 30) {
            Range<Float> r = c.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE);
            if (r != null) return r;
        }
        Float max = c.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM);
        return new Range<>(1f, max == null ? 1f : max);
    }

    /** Largest size with the wanted aspect ratio (w:h, landscape) not wider than maxW. */
    static Size pick(Size[] sizes, int aw, int ah, int maxW) {
        Size best = null;
        if (sizes == null) return null;
        for (Size s : sizes) {
            if (s.getWidth() > maxW) continue;
            if ((long) s.getWidth() * ah != (long) s.getHeight() * aw) continue;
            if (best == null || (long) s.getWidth() * s.getHeight() > (long) best.getWidth() * best.getHeight()) best = s;
        }
        if (best == null) {
            for (Size s : sizes) {
                if (s.getWidth() > maxW) continue;
                if (best == null || (long) s.getWidth() * s.getHeight() > (long) best.getWidth() * best.getHeight()) best = s;
            }
        }
        if (best == null) best = sizes[0];
        return best;
    }

    /** Rotation for saved photos / videos given how the phone is held. */
    static int outputRotation(CameraCharacteristics c, int deviceOrientation) {
        Integer so = c.get(CameraCharacteristics.SENSOR_ORIENTATION);
        int sensor = so == null ? 90 : so;
        if (deviceOrientation == OrientationEventListener.ORIENTATION_UNKNOWN) deviceOrientation = 0;
        deviceOrientation = (deviceOrientation + 45) / 90 * 90;
        Integer facing = c.get(CameraCharacteristics.LENS_FACING);
        if (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT) deviceOrientation = -deviceOrientation;
        return (sensor + deviceOrientation + 360) % 360;
    }
}
