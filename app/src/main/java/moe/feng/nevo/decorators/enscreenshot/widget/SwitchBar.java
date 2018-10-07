package moe.feng.nevo.decorators.enscreenshot.widget;

import android.content.Context;
import android.os.Looper;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import moe.feng.nevo.decorators.enscreenshot.R;
import moe.feng.nevo.decorators.enscreenshot.utils.ResourcesUtils;

public class SwitchBar extends LinearLayout implements Checkable {

    private final TextView mTextView;
    private final Switch mSwitch;

    private int mDisabledBackgroundColor, mEnabledBackgroundColor;
    private CharSequence mDisabledText, mEnabledText;

    private boolean isChecked = false;

    private boolean isBroadcasting = false;

    @Nullable
    private OnCheckedChangeListener mListener = null;

    public SwitchBar(Context context) {
        this(context, null);
    }

    public SwitchBar(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, R.attr.switchBarStyle);
    }

    public SwitchBar(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, R.style.Widget_Material_SwitchBar);
    }

    public SwitchBar(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);

        LayoutInflater.from(context).inflate(R.layout.switch_bar_content, this, true);
        mTextView = findViewById(android.R.id.text1);
        mSwitch = findViewById(android.R.id.checkbox);

        mDisabledBackgroundColor = context.getColor(R.color.material_grey_600);
        mEnabledBackgroundColor = ResourcesUtils.resolveColorAttr(context, android.R.attr.colorAccent);

        mDisabledText = context.getString(R.string.switch_bar_disabled);
        mEnabledText = context.getString(R.string.switch_bar_enabled);

        setOnClickListener(v -> toggle());

        updateViewStates();
    }

    @Override
    public void setChecked(boolean checked) {
        if (!Looper.getMainLooper().isCurrentThread()) {
            throw new IllegalStateException("You should call setChecked on main thread.");
        }
        this.isChecked = checked;
        if (!isBroadcasting) {
            isBroadcasting = true;
            if (mListener != null) {
                mListener.onCheckedChanged(this, isChecked);
            }
            isBroadcasting = false;
        }
        updateViewStates();
    }

    @Override
    public boolean isChecked() {
        return isChecked;
    }

    @Override
    public void toggle() {
        setChecked(!isChecked);
    }

    public void setOnCheckedChangeListener(@Nullable OnCheckedChangeListener listener) {
        mListener = listener;
    }

    private void updateViewStates() {
        mTextView.setText(isChecked ? mEnabledText : mDisabledText);
        setBackgroundColor(isChecked ? mEnabledBackgroundColor : mDisabledBackgroundColor);
        mSwitch.setChecked(isChecked);
    }

    @Override
    protected void onRestoreInstanceState(Parcelable state) {
        if (state instanceof State) {
            final State typedState = (State) state;
            super.onRestoreInstanceState(typedState.getSuperState());
            setChecked(typedState.isChecked);
        } else {
            super.onRestoreInstanceState(state);
        }
    }

    @Nullable
    @Override
    protected Parcelable onSaveInstanceState() {
        final State outState = new State(super.onSaveInstanceState());
        outState.isChecked = isChecked;
        return outState;
    }

    private static class State extends BaseSavedState {

        boolean isChecked;

        State(Parcelable superState) {
            super(superState);
        }

        private State(Parcel source) {
            super(source);
            isChecked = source.readByte() != 0;
        }

        @Override
        public void writeToParcel(Parcel out, int flags) {
            super.writeToParcel(out, flags);
            out.writeByte(isChecked ? (byte) 1 : (byte) 0);
        }

        public static final Creator<State> CREATOR = new Creator<State>() {

            @Override
            public State createFromParcel(Parcel source) {
                return new State(source);
            }

            @Override
            public State[] newArray(int size) {
                return new State[size];
            }

        };

    }

    public interface OnCheckedChangeListener {

        void onCheckedChanged(@NonNull SwitchBar view, boolean isChecked);

    }

}
