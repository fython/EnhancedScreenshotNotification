package moe.feng.nevo.decorators.enscreenshot;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.gms.vision.barcode.Barcode;
import moe.feng.nevo.decorators.enscreenshot.widget.DividerItemDecoration;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class ViewBarcodeActivity extends Activity {

    public static final String ACTION = "moe.feng.intent.action.VIEW_BARCODE";

    public static final String EXTRA_BARCODE = "moe.feng.intent.extra.BARCODE";

    private static final String TAG = ViewBarcodeActivity.class.getSimpleName();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final Intent intent = getIntent();
        if (intent == null || !ACTION.equals(intent.getAction()) || !intent.hasExtra(EXTRA_BARCODE)) {
            Log.e(TAG, "Received Intent is not valid.");
            finish();
            return;
        }

        List<Barcode> barcodeList = intent.getParcelableArrayListExtra(EXTRA_BARCODE);
        if (barcodeList.isEmpty()) {
            Log.e(TAG, "No Barcode");
            finish();
            return;
        }

        if (savedInstanceState == null) {
            ViewBarcodeDialog.newInstance(barcodeList)
                    .show(getFragmentManager(), ViewBarcodeDialog.class.getSimpleName());
        }
    }

    public static class ViewBarcodeDialog extends DialogFragment {

        public static ViewBarcodeDialog newInstance(@NonNull List<Barcode> data) {
            final Bundle args = new Bundle();
            args.putParcelableArrayList(EXTRA_BARCODE, (ArrayList<Barcode>) data);
            final ViewBarcodeDialog dialog = new ViewBarcodeDialog();
            dialog.setArguments(args);
            return dialog;
        }

        private final BarcodeListAdapter mAdapter = new BarcodeListAdapter();

        private DividerItemDecoration mDividerDecoration;

        private List<Barcode> mData;

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            mData = Objects.requireNonNull(getArguments()).getParcelableArrayList(EXTRA_BARCODE);
            mData.add(mData.get(0));
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
            builder.setTitle(R.string.action_view_barcode);
            builder.setView(R.layout.dialog_layout_view_barcode);
            builder.setNegativeButton(android.R.string.cancel, null);
            final AlertDialog dialog = builder.create();
            dialog.setOnShowListener(this::onShow);
            return dialog;
        }

        public void onShow(DialogInterface dialogInterface) {
            final AlertDialog dialog = (AlertDialog) dialogInterface;
            final RecyclerView listView = dialog.findViewById(android.R.id.list);
            listView.setAdapter(mAdapter);
            if (mDividerDecoration == null) {
                mDividerDecoration = new DividerItemDecoration(getContext(), DividerItemDecoration.VERTICAL);
                mDividerDecoration.setDoNotDrawForLastItem(true);
            }
            listView.addItemDecoration(mDividerDecoration);
            mAdapter.submitList(mData);
        }

        @Override
        public void onDismiss(DialogInterface dialog) {
            super.onDismiss(dialog);
            getActivity().finish();
        }
    }

}
