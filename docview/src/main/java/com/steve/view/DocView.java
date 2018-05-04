/*
 * Copyright 2018 Steve Tchatchouang
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.steve.view;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.widget.ImageView;

import java.io.File;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import static com.steve.view.DocUtils.getBitmapFromPdf;

/**
 * Created by Steve Tchatchouang on 16/01/2018
 */

public class DocView extends ImageView {

    static final         int    NONE  = 0;
    static final         int    DRAG  = 1;
    static final         int    ZOOM  = 2;
    static final         int    CLICK = 3;
    private static final String TAG   = DocView.class.getSimpleName();
    private static Executor executor;

    static {
        executor = Executors.newSingleThreadExecutor();
    }

    protected float origWidth, origHeight;
    Matrix matrix;
    int    mode     = NONE;
    PointF last     = new PointF();
    PointF start    = new PointF();
    float  minScale = 1f;
    float  maxScale = 3f;
    float[] m;
    int     viewWidth, viewHeight;
    float saveScale = 1f;
    int oldMeasuredWidth, oldMeasuredHeight;
    ScaleGestureDetector mScaleDetector;

    public DocView(Context context) {
        this(context, null);
    }

    public DocView(Context context, AttributeSet attr) {
        this(context, attr, 0);
    }

    public DocView(Context context, AttributeSet attr, int defStyle) {
        super(context, attr, defStyle);
        super.setClickable(true);
        mScaleDetector = new ScaleGestureDetector(context, new ScaleListener());
        matrix = new Matrix();
        m = new float[9];
        setImageMatrix(matrix);
        setScaleType(ScaleType.MATRIX);
        setOnTouchListener(new OnTouchListener() {

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                mScaleDetector.onTouchEvent(event);
                PointF curr = new PointF(event.getX(), event.getY());
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        last.set(curr);
                        start.set(last);
                        mode = DRAG;
                        break;
                    case MotionEvent.ACTION_MOVE:
                        if (mode == DRAG) {
                            float deltaX = curr.x - last.x;
                            float deltaY = curr.y - last.y;
                            float fixTransX = getFixDragTrans(deltaX, viewWidth, origWidth * saveScale);
                            float fixTransY = getFixDragTrans(deltaY, viewHeight, origHeight * saveScale);
                            matrix.postTranslate(fixTransX, fixTransY);
                            fixTrans();
                            last.set(curr.x, curr.y);
                        }
                        break;
                    case MotionEvent.ACTION_UP:
                        mode = NONE;
                        int xDiff = (int) Math.abs(curr.x - start.x);
                        int yDiff = (int) Math.abs(curr.y - start.y);
                        if (xDiff < CLICK && yDiff < CLICK)
                            performClick();
                        break;
                    case MotionEvent.ACTION_POINTER_UP:
                        mode = NONE;
                        break;
                }
                setImageMatrix(matrix);
                invalidate();
                return true; // indicate event was handled
            }

        });
    }

    void fixTrans() {
        matrix.getValues(m);
        float transX = m[Matrix.MTRANS_X];
        float transY = m[Matrix.MTRANS_Y];
        float fixTransX = getFixTrans(transX, viewWidth, origWidth * saveScale);
        float fixTransY = getFixTrans(transY, viewHeight, origHeight * saveScale);
        if (fixTransX != 0 || fixTransY != 0)
            matrix.postTranslate(fixTransX, fixTransY);
    }

    float getFixTrans(float trans, float viewSize, float contentSize) {
        float minTrans, maxTrans;
        if (contentSize <= viewSize) {
            minTrans = 0;
            maxTrans = viewSize - contentSize;
        } else {
            minTrans = viewSize - contentSize;
            maxTrans = 0;
        }
        if (trans < minTrans)
            return -trans + minTrans;
        if (trans > maxTrans)
            return -trans + maxTrans;
        return 0;
    }

    public void setDocument(final File pdfFile) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    final Bitmap bitmap = getBitmapFromPdf(pdfFile.getAbsolutePath(), 0, false);
                    if (bitmap != null) {
                        post(new Runnable() {
                            @Override
                            public void run() {
                                setImageBitmap(bitmap);
                                invalidate();
                            }
                        });
                    }
                } catch (Exception e) {
                    Log.e(TAG, "run: " + pdfFile.getAbsolutePath(), e);
                }
            }
        });
    }

    float getFixDragTrans(float delta, float viewSize, float contentSize) {
        if (contentSize <= viewSize) {
            return 0;
        }
        return delta;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        viewWidth = MeasureSpec.getSize(widthMeasureSpec);
        viewHeight = MeasureSpec.getSize(heightMeasureSpec);

        //
        // Rescales image on rotation
        //
        if (oldMeasuredHeight == viewWidth && oldMeasuredHeight == viewHeight || viewWidth == 0 || viewHeight == 0)
            return;

        oldMeasuredHeight = viewHeight;
        oldMeasuredWidth = viewWidth;
        if (saveScale == 1) {
            //Fit to screen.
            float scale;
            Drawable drawable = getDrawable();
            if (drawable == null || drawable.getIntrinsicWidth() == 0 || drawable.getIntrinsicHeight() == 0)
                return;

            int bmWidth = drawable.getIntrinsicWidth();
            int bmHeight = drawable.getIntrinsicHeight();
            Log.d("bmSize", "bmWidth: " + bmWidth + " bmHeight : " + bmHeight);
            float scaleX = (float) viewWidth / (float) bmWidth;
            float scaleY = (float) viewHeight / (float) bmHeight;
            scale = Math.min(scaleX, scaleY);
            matrix.setScale(scale, scale);
            // Center the image
            float redundantYSpace = (float) viewHeight - (scale * (float) bmHeight);
            float redundantXSpace = (float) viewWidth - (scale * (float) bmWidth);
            redundantYSpace /= (float) 2;
            redundantXSpace /= (float) 2;
            matrix.postTranslate(redundantXSpace, redundantYSpace);
            origWidth = viewWidth - 2 * redundantXSpace;
            origHeight = viewHeight - 2 * redundantYSpace;
            setImageMatrix(matrix);
        }
        fixTrans();

    }

    private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        @Override
        public boolean onScaleBegin(ScaleGestureDetector detector) {
            mode = ZOOM;
            return true;

        }

        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            float mScaleFactor = detector.getScaleFactor();
            float origScale = saveScale;
            saveScale *= mScaleFactor;
            if (saveScale > maxScale) {
                saveScale = maxScale;
                mScaleFactor = maxScale / origScale;
            } else if (saveScale < minScale) {
                saveScale = minScale;
                mScaleFactor = minScale / origScale;
            }
            if (origWidth * saveScale <= viewWidth || origHeight * saveScale <= viewHeight)
                matrix.postScale(mScaleFactor, mScaleFactor, viewWidth / 2, viewHeight / 2);
            else
                matrix.postScale(mScaleFactor, mScaleFactor, detector.getFocusX(), detector.getFocusY());
            fixTrans();
            return true;
        }
    }
}
