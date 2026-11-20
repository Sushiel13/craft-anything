package com.sushil.scenecam;

import android.content.Context;
import android.util.AttributeSet;
import android.view.TextureView;

/** TextureView that keeps the preview's aspect ratio (width:height as shown on screen). */
public class AutoFitTextureView extends TextureView {
    private int ratioW = 3, ratioH = 4;

    public AutoFitTextureView(Context c) { super(c); }
    public AutoFitTextureView(Context c, AttributeSet a) { super(c, a); }
    public AutoFitTextureView(Context c, AttributeSet a, int s) { super(c, a, s); }

    public void setAspect(int w, int h) {
        ratioW = w;
        ratioH = h;
        requestLayout();
    }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        super.onMeasure(wSpec, hSpec);
        int w = MeasureSpec.getSize(wSpec);
        int h = MeasureSpec.getSize(hSpec);
        int wantH = w * ratioH / ratioW;
        if (wantH <= h || h == 0) setMeasuredDimension(w, wantH);
        else setMeasuredDimension(h * ratioW / ratioH, h);
    }
}
