package moe.feng.nevo.decorators.enscreenshot.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.widget.TextView;
import androidx.annotation.Nullable;
import androidx.core.text.HtmlCompat;

public class HtmlTextView extends TextView {

    public HtmlTextView(Context context) {
        this(context, null);
    }

    public HtmlTextView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    public HtmlTextView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }

    public HtmlTextView(Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init(context, attrs);
    }

    private void init(Context context, @Nullable AttributeSet attrs) {
        if (attrs != null) {
            final TypedArray a = context.obtainStyledAttributes(attrs, new int[] { android.R.attr.text });
            String text = a.getString(0);
            if (text != null) {
                setText(HtmlCompat.fromHtml(text, 0));
            }
            a.recycle();
        }
    }

}
