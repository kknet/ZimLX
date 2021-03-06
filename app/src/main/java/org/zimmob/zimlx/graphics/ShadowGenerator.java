/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.zimmob.zimlx.graphics;

import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.BlurMaskFilter;
import android.graphics.BlurMaskFilter.Blur;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.support.v4.graphics.ColorUtils;

import org.zimmob.zimlx.LauncherAppState;

/**
 * Utility class to add shadows to bitmaps.
 */
public class ShadowGenerator {

    public static final float BLUR_FACTOR = 0.5f / 48;
    // Percent of actual icon size
    private static final float HALF_DISTANCE = 0.5f;
    // Percent of actual icon size
    private static final float KEY_SHADOW_DISTANCE = 1f / 48;
    private static final int KEY_SHADOW_ALPHA = 61;

    private static final int AMBIENT_SHADOW_ALPHA = 30;

    private static final Object LOCK = new Object();
    // Singleton object guarded by {@link #LOCK}
    private static ShadowGenerator sShadowGenerator;

    private final int mIconSize;

    private final Canvas mCanvas;
    private final Paint mBlurPaint;
    private final Paint mDrawPaint;

    private ShadowGenerator() {
        mIconSize = LauncherAppState.getInstance().getInvariantDeviceProfile().iconBitmapSize;
        mCanvas = new Canvas();
        mBlurPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        mBlurPaint.setMaskFilter(new BlurMaskFilter(mIconSize * BLUR_FACTOR, Blur.NORMAL));
        mDrawPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    }

    public static ShadowGenerator getInstance() {
        synchronized (LOCK) {
            if (sShadowGenerator == null) {
                sShadowGenerator = new ShadowGenerator();
            }
        }
        return sShadowGenerator;
    }

    public static Bitmap createPillWithShadow(int i, int i2, int i3) {
        float f = (((float) i3) * 1.0f) / 32.0f;
        float f2 = (((float) i3) * 1.0f) / 16.0f;
        int i4 = i3 / 2;
        Canvas canvas = new Canvas();
        Paint paint = new Paint(3);
        paint.setMaskFilter(new BlurMaskFilter(f, Blur.NORMAL));
        int max = Math.max(Math.round(((float) (i2 / 2)) + f), Math.round((f + ((float) i4)) + f2));
        int i5 = max * 2;
        Bitmap createBitmap = Bitmap.createBitmap(i5, i5, Config.ARGB_8888);
        canvas.setBitmap(createBitmap);
        int i6 = max - (i2 / 2);
        int i7 = max - (i3 / 2);
        int i8 = max + (i2 / 2);
        int i9 = max + (i3 / 2);
        paint.setAlpha(30);
        canvas.drawRoundRect((float) i6, (float) i7, (float) i8, (float) i9, (float) i4, (float) i4, paint);
        paint.setAlpha(61);
        canvas.drawRoundRect((float) i6, ((float) i7) + f2, (float) i8, ((float) i9) + f2, (float) i4, (float) i4, paint);
        paint = new Paint(3);
        paint.setColor(i);
        canvas.drawRoundRect((float) i6, (float) i7, (float) i8, (float) i9, (float) i4, (float) i4, paint);
        return createBitmap;
    }

    /**
     * Returns the minimum amount by which an icon with {@param bounds} should be scaled
     * so that the shadows do not get clipped.
     */
    public static float getScaleForBounds(RectF bounds) {
        float scale = 1;

        // For top, left & right, we need same space.
        float minSide = Math.min(Math.min(bounds.left, bounds.right), bounds.top);
        if (minSide < BLUR_FACTOR) {
            scale = (HALF_DISTANCE - BLUR_FACTOR) / (HALF_DISTANCE - minSide);
        }

        float bottomSpace = BLUR_FACTOR + KEY_SHADOW_DISTANCE;
        if (bounds.bottom < bottomSpace) {
            scale = Math.min(scale, (HALF_DISTANCE - bottomSpace) / (HALF_DISTANCE - bounds.bottom));
        }
        return scale;
    }

    public synchronized Bitmap recreateIcon(Bitmap icon) {
        return recreateIcon(icon, mIconSize);
    }

    public synchronized Bitmap recreateIcon(Bitmap icon, int size) {
        int[] offset = new int[2];
        Bitmap shadow = icon.extractAlpha(mBlurPaint, offset);
        Bitmap result = Bitmap.createBitmap(size, size, Config.ARGB_8888);
        mCanvas.setBitmap(result);

        // Draw ambient shadow
        mDrawPaint.setAlpha(AMBIENT_SHADOW_ALPHA);
        mCanvas.drawBitmap(shadow, offset[0], offset[1], mDrawPaint);

        // Draw key shadow
        mDrawPaint.setAlpha(KEY_SHADOW_ALPHA);
        mCanvas.drawBitmap(shadow, offset[0], offset[1] + KEY_SHADOW_DISTANCE * size, mDrawPaint);

        // Draw the icon
        mDrawPaint.setAlpha(255);
        mCanvas.drawBitmap(icon, 0, 0, mDrawPaint);

        mCanvas.setBitmap(null);
        return result;
    }

    public synchronized Bitmap createShadow(Bitmap icon, int size) {
        int[] offset = new int[2];
        Bitmap shadow = icon.extractAlpha(mBlurPaint, offset);
        Bitmap result = Bitmap.createBitmap(size, size, Config.ARGB_8888);
        mCanvas.setBitmap(result);

        // Draw ambient shadow
        mDrawPaint.setAlpha(AMBIENT_SHADOW_ALPHA);
        mCanvas.drawBitmap(shadow, offset[0], offset[1], mDrawPaint);

        // Draw key shadow
        mDrawPaint.setAlpha(KEY_SHADOW_ALPHA);
        mCanvas.drawBitmap(shadow, offset[0], offset[1] + KEY_SHADOW_DISTANCE * size, mDrawPaint);

        mCanvas.setBitmap(null);
        return result;
    }

    public static class Builder {

        public final RectF bounds = new RectF();
        public final int color;

        public int ambientShadowAlpha = AMBIENT_SHADOW_ALPHA;

        public float shadowBlur;

        public float keyShadowDistance;
        public int keyShadowAlpha = KEY_SHADOW_ALPHA;
        public float radius;

        public Builder(int color) {
            this.color = color;
        }

        public Builder setupBlurForSize(int height) {
            shadowBlur = height * 1f / 32;
            keyShadowDistance = height * 1f / 16;
            return this;
        }

        public Bitmap createPill(int width, int height) {
            radius = height / 2;

            int centerX = Math.round(width / 2 + shadowBlur);
            int centerY = Math.round(radius + shadowBlur + keyShadowDistance);
            int center = Math.max(centerX, centerY);
            bounds.set(0, 0, width, height);
            bounds.offsetTo(center - width / 2, center - height / 2);

            int size = center * 2;
            Bitmap result = Bitmap.createBitmap(size, size, Config.ARGB_8888);
            drawShadow(new Canvas(result));
            return result;
        }

        public void drawShadow(Canvas c) {
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
            p.setColor(color);

            // Key shadow
            p.setShadowLayer(shadowBlur, 0, keyShadowDistance,
                    ColorUtils.setAlphaComponent(Color.BLACK, keyShadowAlpha));
            c.drawRoundRect(bounds, radius, radius, p);

            // Ambient shadow
            p.setShadowLayer(shadowBlur, 0, 0,
                    ColorUtils.setAlphaComponent(Color.BLACK, ambientShadowAlpha));
            c.drawRoundRect(bounds, radius, radius, p);
        }
    }
}
