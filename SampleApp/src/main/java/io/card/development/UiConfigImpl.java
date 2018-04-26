package io.card.development;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;

import io.card.payment.OverlayView;
import io.card.payment.Preview;
import io.card.payment.ui.config.UIConfig;

public class UiConfigImpl implements UIConfig {

    @Override
    public int getLayoutId() {
        return R.layout.scanner_activity;
    }

    @Override
    public Preview getPreviewView(ViewGroup rootViewGroup) {
        return (Preview) rootViewGroup.findViewById(R.id.scanner_preview_view);
    }

    @Override
    public OverlayView getOverlayView(ViewGroup rootViewGroup) {
        return (OverlayView) rootViewGroup.findViewById(R.id.scanner_overlay_view);
    }

    @Override
    public View getManualEntryButton(ViewGroup rootViewGroup) {
        return rootViewGroup.findViewById(R.id.scanner_manual_entry_button);
    }

    @Override
    public void onInflated(Activity activity, ViewGroup viewGroup) {

    }
}
