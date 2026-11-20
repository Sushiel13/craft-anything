package com.sushil.scenecam;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

/** A view that always takes the exact size of the camera preview beside it (first child of its parent). */
public class MatchPreviewView extends View {
    public MatchPreviewView(Context c) { super(c); }
    public MatchPreviewView(Context c, AttributeSet a) { super(c, a); }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        if (getParent() instanceof ViewGroup) {
            View first = ((ViewGroup) getParent()).getChildAt(0);
            if (first != null && first != this && first.getMeasuredWidth() > 0) {
                setMeasuredDimension(first.getMeasuredWidth(), first.getMeasuredHeight());
                return;
            }
        }
        setMeasuredDimension(0, 0);
    }
}
