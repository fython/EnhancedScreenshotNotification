package moe.feng.nevo.decorators.enscreenshot;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.gms.vision.barcode.Barcode;
import moe.feng.nevo.decorators.enscreenshot.utils.IntentUtils;

public class BarcodeListAdapter extends ListAdapter<Barcode, BarcodeListAdapter.ViewHolder> {

    BarcodeListAdapter() {
        super(new DiffCallback());
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_barcode, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.onBind(getItem(position));
    }

    class ViewHolder extends RecyclerView.ViewHolder {

        private final Context mContext;

        private final TextView mText1, mText2;
        private final Button mButton1, mCopyButton;

        private Barcode mData;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            mContext = itemView.getContext();
            mText1 = itemView.findViewById(android.R.id.text1);
            mText2 = itemView.findViewById(android.R.id.text2);
            mButton1 = itemView.findViewById(android.R.id.button1);
            mCopyButton = itemView.findViewById(android.R.id.copy);
        }

        void onBind(Barcode data) {
            mData = data;

            mText1.setText(data.rawValue);
            mText2.setText(mText2.getResources().getStringArray(R.array.barcode_types)[data.valueFormat]);

            switch (data.valueFormat) {
                case Barcode.URL: {
                    onUrlItemBind();
                    break;
                }
                case Barcode.PHONE: {
                    onPhoneItemBind();
                    break;
                }
                case Barcode.CONTACT_INFO: {
                    onContactInfoItemBind();
                    break;
                }
                default: {
                    onNormalItemBind();
                }
            }
        }

        private void onContactInfoItemBind() {
            bindButton1(R.string.action_add_to_contacts, v -> mContext.startActivity(
                    IntentUtils.createAddContactFromBarcode(mData)
            ));
            bindCopyButton(android.R.string.copy);
        }

        private void onPhoneItemBind() {
            bindButton1(R.string.action_call, v -> mContext.startActivity(
                    IntentUtils.createDialIntent(Uri.parse(mData.rawValue))
            ));
            bindCopyButton(android.R.string.copy);
        }

        private void onUrlItemBind() {
            bindButton1(R.string.action_open_link, v -> mContext.startActivity(Intent.createChooser(
                    IntentUtils.createViewIntent(Uri.parse(mData.rawValue)),
                    mContext.getString(R.string.action_open_link)
            )));
            bindCopyButton(android.R.string.copyUrl);
        }

        private void onNormalItemBind() {
            hideButton1();
            bindCopyButton(android.R.string.copy);
        }

        private void hideButton1() {
            mButton1.setVisibility(View.GONE);
        }

        private void bindButton1(@StringRes int textRes, @NonNull View.OnClickListener listener) {
            mButton1.setVisibility(View.VISIBLE);
            mButton1.setText(textRes);
            mButton1.setOnClickListener(listener);
        }

        private void bindCopyButton(@StringRes int textRes) {
            mCopyButton.setText(textRes);
            mCopyButton.setOnClickListener(v -> mContext.sendBroadcast(IntentUtils.createCopyIntent(mData.rawValue)));
        }

    }

    private static class DiffCallback extends DiffUtil.ItemCallback<Barcode> {

        @Override
        public boolean areItemsTheSame(@NonNull Barcode oldItem, @NonNull Barcode newItem) {
            return oldItem.rawValue.equals(newItem.rawValue);
        }

        @Override
        public boolean areContentsTheSame(@NonNull Barcode oldItem, @NonNull Barcode newItem) {
            return areItemsTheSame(oldItem, newItem);
        }

    }

}
