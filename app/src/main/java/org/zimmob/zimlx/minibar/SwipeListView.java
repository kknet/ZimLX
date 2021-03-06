package org.zimmob.zimlx.minibar;

import android.content.Context;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.widget.ListView;

import org.zimmob.zimlx.util.Tool;

public class SwipeListView extends ListView {
    private GestureDetector mGestureDetector;
    private OnSwipeRight onSwipeRight;

    public void setOnSwipeRight(OnSwipeRight onSwipeRight) {
        this.onSwipeRight = onSwipeRight;
    }

    public SwipeListView(Context context) {
        super(context);
        init();
    }

    public SwipeListView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public SwipeListView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        if (isInEditMode()) return;
        final float dis = Tool.dp2px(10, getContext());
        final float vDis = Tool.dp2px(30, getContext());
        mGestureDetector = new GestureDetector(getContext(), new GestureDetector.SimpleOnGestureListener() {
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                if (e1 == null) return true;
                int dx = (int) (e2.getX() - e1.getX());
                int dy = (int) (e2.getY() - e1.getY());
                if (Math.abs(dx) > dis && Math.abs(dy) < vDis) {
                    if (velocityX > 0) {
                        try {
                            int pos = pointToPosition((int) e1.getX(), (int) e1.getY());
                            if (pos != -1 && onSwipeRight != null)
                                onSwipeRight.onSwipe(pos, e1.getX(), e1.getY());
                        } catch (Exception ignored) {

                        }
                    }
                }
                return true;
            }
        });
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        mGestureDetector.onTouchEvent(ev);
        return super.dispatchTouchEvent(ev);
    }

    interface OnSwipeRight {
        void onSwipe(int pos, float x, float y);
    }
}
