package com.ncu.beanyong.zoomimage;

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.support.v4.view.ViewPager;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewTreeObserver;
import android.widget.ImageView;

/**
 * 可与ViewPager配合使用的任意缩放图片控件
 * Created by BeanYong on 2015/8/19.
 */
public class ZoomImageView extends ImageView implements ViewTreeObserver.OnGlobalLayoutListener, View.OnTouchListener, ScaleGestureDetector.OnScaleGestureListener {

    private boolean isOnce = true;//是否第一次加载
    private float mInitScale = 0f;//初始的缩放比例
    private float mMidScale = 0f;//中等的缩放比例
    private float mMaxScale = 0f;//最大的缩放比例
    private Matrix mMatrix;
    private ScaleGestureDetector mScaleGestureDetector;
    //---------------------------自由移动
    /**
     * 记录上一次多点触控的手指数量
     */
    private int mLastPointerCount;

    /**
     * 上一次触控的中心坐标
     */
    private float mLastX, mLastY;

    /**
     * 获取系统的滑动距离标准值
     */
    private int mTouchSlop;

    /**
     * 是否可以拖动
     */
    private boolean isCanDrag;

    /**
     * 是否需要检测
     */
    private boolean isCheckLeftAndRight, isCheckTopAndBottom;

    //-----------------------双击缩放
    /**
     * 监听双击事件
     */
    private GestureDetector mGestureDetector;

    public ZoomImageView(Context context) {
        this(context, null);
    }

    public ZoomImageView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ZoomImageView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
        setKeepScreenOn(true);
    }

    private void init(Context context) {
        mMatrix = new Matrix();
        setScaleType(ScaleType.MATRIX);
        mScaleGestureDetector = new ScaleGestureDetector(context, this);
        setOnTouchListener(this);
        mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        mGestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDoubleTap(MotionEvent e) {
                float x = e.getX();
                float y = e.getY();
                if (getCurrentScale() < mMidScale) {
                    postDelayed(new SlowScaleRunnable(x, y, mMidScale), 16);
                } else {
                    postDelayed(new SlowScaleRunnable(x, y, mInitScale), 16);
                }
                checkBorderAndCenterWhenDoubleClick();
                setImageMatrix(mMatrix);
                return true;
            }
        });
    }

    private void checkBorderAndCenterWhenDoubleClick() {
        RectF rectF = getScaleRectF();

        float deltaX = 0f, deltaY = 0f;

        int width = getWidth();
        int height = getHeight();

        if (rectF.width() <= width) {//如果图片宽度小于等于控件宽度，水平居中
            deltaX = width / 2 - rectF.right + rectF.width() / 2;
        } else {
            if (rectF.left > 0) {//左侧有空白
                deltaX = -rectF.left;
            }

            if (rectF.right < width) {//右侧有空白
                deltaX = width - rectF.right;
            }
        }

        if (rectF.height() <= height) {//如果图片高度小于等于控件高度，竖直居中
            deltaY = height / 2 - rectF.bottom + rectF.height() / 2;
        } else {
            if (rectF.top > 0) {//顶部有空白
                deltaY = -rectF.top;
            }

            if (rectF.bottom < height) {
                deltaY = height - rectF.bottom;
            }
        }

        mMatrix.postTranslate(deltaX, deltaY);
    }

    /**
     * 获取当前缩放比例
     *
     * @return
     */
    private float getCurrentScale() {
        float[] scale = new float[9];
        mMatrix.getValues(scale);
        return scale[Matrix.MSCALE_X];
    }

    @Override
    public void onGlobalLayout() {
        if (isOnce) {
            int width = getWidth();//获取控件宽度
            int height = getHeight();//获取控件高度
            Drawable d = getDrawable();//获取到此控件内的图片
            int dw = d.getIntrinsicWidth();//获取图片宽度
            int dh = d.getIntrinsicHeight();//获取图片高度

            //确定图片的缩放比例
            float scale = 0f;//缩放比例，为了提高效率，先存入本地变量，然后赋值给成员变量

            if (dw > width && dh < height) {//图片宽度大于控件宽度，但图片高度小于控件高度
                scale = width * 1.0f / dw;
            }
            if (dw < width && dh > height) {//图片高度大于控件高度，但图片宽度小于控件宽度
                scale = height * 1.0f / dh;
            }
            if ((dw > width && dh > height) || (dw < width && dh < height)) {//图片的高度和宽度都大于或小于控件的高度和宽度
                scale = Math.min(width * 1.0f / dw, height * 1.0f / dh);
            }

            mInitScale = scale;
            mMidScale = scale * 2;
            mMaxScale = scale * 4;

            //计算将图片移动至控件中心需要的水平和竖直距离
            int tx = width / 2 - dw / 2;
            int ty = height / 2 - dh / 2;

            mMatrix.postTranslate(tx, ty);
            mMatrix.postScale(scale, scale, width / 2, height / 2);

            setImageMatrix(mMatrix);

            isOnce = false;//以上代码只执行一次
        }
    }

    @Override
    public boolean onScale(ScaleGestureDetector detector) {
        float scaleFactor = detector.getScaleFactor();
        float scale = getCurrentScale();//当手势缩放时，在动态改变mMatrix的scale值，所以在此处要动态获取当前的scale

        if (getDrawable() == null) {
            return true;
        }

        if ((scale < mMaxScale && scaleFactor > 1.0f) || (scale > mInitScale && scaleFactor < 1.0f)) {
            if (scale * scaleFactor > mMaxScale) {
                scaleFactor = mMaxScale / scale;
            }

            if (scale * scaleFactor < mInitScale) {
                scaleFactor = mInitScale / scale;
            }

            mMatrix.postScale(scaleFactor, scaleFactor, detector.getFocusX(), detector.getFocusY());
            checkForBorderAndCenterWhenScale();
            setImageMatrix(mMatrix);
        }
        return true;
    }

    private void checkForBorderAndCenterWhenScale() {
        RectF rectF = getScaleRectF();

        int width = getWidth();//控件的宽度
        int height = getHeight();//控件的高度

        float deltaX = 0f, deltaY = 0f;//两个方向上的偏移值

        if (rectF != null) {
            if (rectF.width() > width) {//图片宽度大于控件宽度
                if (rectF.left > 0) {//图片左侧有白边
                    deltaX = -rectF.left;
                }

                if (rectF.right < width) {
                    deltaX = width - rectF.right;
                }
            }

            if (rectF.height() > height) {//图片高度小于控件高度
                if (rectF.top > 0) {//图片顶部有白边
                    deltaY = -rectF.top;
                }

                if (rectF.bottom < height) {//控件底部有白边
                    deltaY = height - rectF.bottom;
                }
            }

            if (rectF.width() < width) {//图片宽度小于控件宽度，居中显示
                deltaX = width / 2 - rectF.right + rectF.width() / 2;
            }

            if (rectF.height() < height) {//图片高度小于控件高度，居中显示
                deltaY = height / 2 - rectF.bottom + rectF.height() / 2;
            }

            mMatrix.postTranslate(deltaX, deltaY);
        }
    }

    /**
     * 获取缩放之后的图片相关距离
     *
     * @return
     */
    private RectF getScaleRectF() {
        Matrix matrix = mMatrix;
        Drawable d = getDrawable();
        if (d != null) {
            RectF rectF = new RectF(0, 0, d.getIntrinsicWidth(), d.getIntrinsicHeight());
            matrix.mapRect(rectF);
            return rectF;
        }
        return null;
    }

    @Override
    public boolean onScaleBegin(ScaleGestureDetector detector) {
        return true;
    }

    @Override
    public void onScaleEnd(ScaleGestureDetector detector) {

    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        if (mGestureDetector.onTouchEvent(event)) {
            return true;
        }

        mScaleGestureDetector.onTouchEvent(event);

        int x = 0;
        int y = 0;
        //记录多点触控手指的数量
        int pointerCount = event.getPointerCount();

        for (int i = 0; i < pointerCount; i++) {
            x += event.getX(i);
            y += event.getY(i);
        }

        //获取触控的中心点坐标
        x /= pointerCount;
        y /= pointerCount;

        if (mLastPointerCount != pointerCount) {
            mLastX = x;
            mLastY = y;
            mLastPointerCount = pointerCount;
            isCanDrag = false;
        }
        RectF rectf = getScaleRectF();
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                if (rectf.width() > getWidth() || rectf.height() > getHeight()) {
                    if (getParent() instanceof ViewPager) {
                        getParent().requestDisallowInterceptTouchEvent(true);
                    }
                }
                break;

            case MotionEvent.ACTION_MOVE:
                if (rectf.width() > getWidth() || rectf.height() > getHeight()) {
                    if (getParent() instanceof ViewPager) {
                        getParent().requestDisallowInterceptTouchEvent(true);
                    }
                }

                float dx = x - mLastX;
                float dy = y - mLastY;

                if (!isCanDrag) {
                    isCanDrag = isMoveAction(dx, dy);
                }

                if (isCanDrag) {
                    isCheckLeftAndRight = isCheckTopAndBottom = true;//默认都需要检测
                    RectF rectF = getScaleRectF();
                    if (rectF.width() < getWidth()) {//如果当前图片宽度小于控件宽度，不允许横向移动
                        isCheckLeftAndRight = false;
                        dx = 0;
                    }

                    if (rectF.height() < getHeight()) {//如果当前图片高度小于控件高度，不允许纵向移动
                        isCheckTopAndBottom = false;
                        dy = 0;
                    }

                    mMatrix.postTranslate(dx, dy);

                    checkBorderWhenTranslate();

                    setImageMatrix(mMatrix);

                    mLastX = x;
                    mLastY = y;
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                mLastPointerCount = 0;
                break;
        }
        return true;
    }

    /**
     * 当滑动的时候检测边界，防止出现白边
     */
    private void checkBorderWhenTranslate() {
        RectF rectF = getScaleRectF();

        float deltaX = 0f, deltaY = 0f;

        int width = getWidth();
        int height = getHeight();

        if (isCheckLeftAndRight) {
            if (rectF.left > 0) {
                deltaX = -rectF.left;
            }

            if (rectF.right < width) {
                deltaX = width - rectF.right;
            }
        }

        if (isCheckTopAndBottom) {
            if (rectF.top > 0) {
                deltaY = -rectF.top;
            }

            if (rectF.bottom < height) {
                deltaY = height - rectF.bottom;
            }
        }
        mMatrix.postTranslate(deltaX, deltaY);
    }

    /**
     * 判断滑动是否足以触发滑动事件
     *
     * @param dx
     * @param dy
     * @return
     */
    private boolean isMoveAction(float dx, float dy) {
        return Math.sqrt(dx * dx + dy * dy) > mTouchSlop;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        getViewTreeObserver().addOnGlobalLayoutListener(this);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        getViewTreeObserver().removeOnGlobalLayoutListener(this);
    }

    class SlowScaleRunnable implements Runnable {

        private final float SCALE_BIGGER = 1.07f;
        private final float SCALE_SMALLER = 0.93f;
        private float cx, cy, targetScale, tempScale;

        public SlowScaleRunnable(float cx, float cy, float targetScale) {
            this.targetScale = targetScale;
            this.cx = cx;
            this.cy = cy;

            if (getCurrentScale() < targetScale) {
                tempScale = SCALE_BIGGER;
            }

            if (getCurrentScale() > targetScale) {
                tempScale = SCALE_SMALLER;
            }
        }

        @Override
        public void run() {
            mMatrix.postScale(tempScale, tempScale, cx, cy);

            float currentScale = getCurrentScale();

            if ((tempScale > 1.0f && currentScale < targetScale) || (tempScale < 1.0f && currentScale > targetScale)) {
                postDelayed(this, 16);
            }

            if ((tempScale > 1.0f && currentScale > targetScale) || (tempScale < 1.0f && currentScale < targetScale)) {
                mMatrix.postScale(targetScale / currentScale, targetScale / currentScale, cx, cy);
            }
            checkBorderAndCenterWhenDoubleClick();
            setImageMatrix(mMatrix);
        }
    }
}
