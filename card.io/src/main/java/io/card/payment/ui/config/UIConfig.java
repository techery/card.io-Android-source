package io.card.payment.ui.config;

import android.view.View;
import android.view.ViewGroup;

import io.card.payment.OverlayView;
import io.card.payment.Preview;

public interface UIConfig {

    int getLayoutId();

    Preview getPreviewView(ViewGroup rootViewGroup);

    OverlayView getOverlayView(ViewGroup rootViewGroup);

    View getManualEntryButton(ViewGroup rootViewGroup);
}
