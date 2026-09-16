package com.sixthpath.game;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * THE CRASH IS A SCREEN, NOT A DISAPPEARANCE. The owner's report was
 * "başlamadan kapanıyor direkt" — an app that dies before its first frame
 * leaves nothing to debug from a phone with no adb cable. Any uncaught
 * throwable in the process now lands HERE, as a scrollable stack trace the
 * owner can screenshot. Ugly on purpose: this screen existing at all means
 * something is broken, and its one job is to say what.
 */
public class CrashActivity extends Activity {
    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        TextView t = new TextView(this);
        t.setTextColor(Color.parseColor("#e0dcd3"));
        t.setBackgroundColor(Color.parseColor("#1a0505"));
        t.setPadding(28, 48, 28, 48);
        t.setTextSize(11f);
        t.setTypeface(android.graphics.Typeface.MONOSPACE);
        String stack = getIntent().getStringExtra("stack");
        t.setText("SIXTH PATH ÇÖKME RAPORU — bu ekranın fotoğrafını gönderin\n\n"
                + (stack == null ? "(iz yok)" : stack));
        ScrollView sc = new ScrollView(this);
        sc.setBackgroundColor(Color.parseColor("#1a0505"));
        sc.addView(t);
        setContentView(sc);
    }
}
