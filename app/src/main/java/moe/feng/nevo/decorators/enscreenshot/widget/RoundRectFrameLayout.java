package moe.feng.nevo.decorators.enscreenshot.widget;

import android.content.Context;
import android.graphics.Outline;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import androidx.annotation.Nullable;
import moe.feng.nevo.decorators.enscreenshot.R;

public class RoundRectFrameLayout extends FrameLayout {

    public RoundRectFrameLayout(Context context) {
        super(context);
        init();
    }

    public RoundRectFrameLayout(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public RoundRectFrameLayout(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    public RoundRectFrameLayout(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init();
    }

    private void init() {
        setClipToOutline(true);
        setOutlineProvider(new RoundRectOutlineProvider(
                getResources().getDimension(R.dimen.floating_window_corner_radius)));
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        invalidateOutline();
    }

    private static class RoundRectOutlineProvider extends ViewOutlineProvider {

        private final float mCornerRadius;

        RoundRectOutlineProvider(float cornerRadius) {
            mCornerRadius = cornerRadius;
        }

        @Override
        public void getOutline(View view, Outline outline) {
            final Rect clipPath = new Rect();
            view.getLocalVisibleRect(clipPath);
            outline.setRoundRect(clipPath, mCornerRadius);
        }

    }

}
