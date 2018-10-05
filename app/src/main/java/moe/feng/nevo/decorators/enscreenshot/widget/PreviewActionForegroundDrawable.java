package moe.feng.nevo.decorators.enscreenshot.widget;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import moe.feng.nevo.decorators.enscreenshot.R;

import static java.lang.Math.pow;
import static java.lang.Math.sqrt;

public class PreviewActionForegroundDrawable extends Drawable {

    private final Paint mBackgroundPaint;

    private final int mIconSize;
    private final int mIconMinMargin;

    private Drawable mIconDrawable, mArrowDrawable;

    private int mBackgroundColor;

    private float mProgress = 0f;

    public PreviewActionForegroundDrawable(@NonNull Context context) {
        mBackgroundColor = context.getColor(R.color.material_blue_500);

        mBackgroundPaint = new Paint();
        mBackgroundPaint.setColor(mBackgroundColor);
        mBackgroundPaint.setStyle(Paint.Style.FILL);
        mBackgroundPaint.setAntiAlias(true);

        mIconDrawable = context.getDrawable(R.drawable.ic_open_in_browser_white_24dp);
        mArrowDrawable = context.getDrawable(R.drawable.ic_keyboard_arrow_up_white_24dp);
        mIconSize = context.getResources().getDimensionPixelSize(R.dimen.view_icon_size);
        mIconMinMargin = context.getResources().getDimensionPixelSize(R.dimen.view_icon_min_margin);
    }

    public void setProgress(float progress) {
        if (progress < 0 || progress > 1) {
            throw new IllegalArgumentException("Progress should be set between 0 and 1.");
        }
        mProgress = progress;
        invalidateSelf();
    }

    public float getProgress() {
        return mProgress;
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        // Calculate properties
        final Rect bounds = getBounds();
        final double maxRadius = sqrt(pow(bounds.width() / 2, 2) + pow(bounds.height(), 2));
        final double currentRadius = maxRadius * mProgress;
        final int iconBottom = bounds.bottom - (int) Math.max(mIconMinMargin, (currentRadius - mIconSize) / 2);
        mIconDrawable.setBounds(
                bounds.centerX() - mIconSize / 2, iconBottom - mIconSize,
                bounds.centerX() + mIconSize / 2, iconBottom
        );
        final int arrowBottom = (int) (bounds.bottom - currentRadius - mIconMinMargin / 2);
        mArrowDrawable.setBounds(
                bounds.centerX() - mIconSize / 2, arrowBottom - mIconSize,
                bounds.centerX() + mIconSize / 2, arrowBottom
        );
        final Path path = new Path();
        path.addCircle(bounds.centerX(), bounds.bottom, (float) currentRadius, Path.Direction.CCW);

        // Draw shadow
        canvas.drawColor(Color.argb((int) (255 * Math.min(0.7f, mProgress * 0.7f)), 0, 0, 0));

        // Save and clip
        canvas.save();
        canvas.clipPath(path);

        // Draw round background and icon
        canvas.drawColor(mBackgroundColor);
        mIconDrawable.draw(canvas);

        // Restore
        canvas.restore();

        // Draw arrow outside
        mArrowDrawable.setAlpha((int) (255 * Math.min(1f, mProgress / 0.1f)));
        mArrowDrawable.draw(canvas);
    }

    @Override
    public void setAlpha(int alpha) {
        mBackgroundPaint.setAlpha(alpha);
        invalidateSelf();
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {
        mBackgroundPaint.setColorFilter(colorFilter);
        invalidateSelf();
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSPARENT;
    }

}
