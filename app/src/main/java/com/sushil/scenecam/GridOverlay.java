package com.sushil.scenecam;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;

/** Rule-of-thirds grid plus a tap-to-focus ring. */
public class GridOverlay extends MatchPreviewView {
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private boolean showGrid = true;
    private float fx = -1, fy = -1;
    private final Runnable clearFocus = () -> { fx = -1; invalidate(); };

    public GridOverlay(Context c) { this(c, null); }

    public GridOverlay(Context c, AttributeSet a) {
        super(c, a);
        line.setColor(0x66FFFFFF);
        line.setStrokeWidth(2f);
        ring.setColor(0xFFFFB547);
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(4f);
    }

    public void setGrid(boolean on) { showGrid = on; invalidate(); }
    public boolean isGrid() { return showGrid; }

    public void showFocus(float x, float y) {
        fx = x; fy = y;
        invalidate();
        removeCallbacks(clearFocus);
        postDelayed(clearFocus, 1500);
    }

    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        if (showGrid) {
            c.drawLine(w / 3f, 0, w / 3f, h, line);
            c.drawLine(2 * w / 3f, 0, 2 * w / 3f, h, line);
            c.drawLine(0, h / 3f, w, h / 3f, line);
            c.drawLine(0, 2 * h / 3f, w, 2 * h / 3f, line);
        }
        if (fx >= 0) c.drawCircle(fx, fy, 70f, ring);
    }
}
