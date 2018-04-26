package io.card.payment.ui.config;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;

import io.card.payment.OverlayView;
import io.card.payment.Preview;
import io.card.payment.R;

public class DefaultUiConfig implements UIConfig {

    @Override
    public int getLayoutId() {
        return R.layout.cio_activity_card_io;
    }

    @Override
    public Preview getPreviewView(ViewGroup rootViewGroup) {
        return (Preview) rootViewGroup.findViewById(R.id.preview_view);
    }

    @Override
    public OverlayView getOverlayView(ViewGroup rootViewGroup) {
        return (OverlayView) rootViewGroup.findViewById(R.id.overlay_view);
    }

    @Override
    public View getManualEntryButton(ViewGroup rootViewGroup) {
        return rootViewGroup.findViewById(R.id.manual_entry_button);
    }

    @Override
    public void onInflated(Activity activity, ViewGroup viewGroup) {

    }
}
