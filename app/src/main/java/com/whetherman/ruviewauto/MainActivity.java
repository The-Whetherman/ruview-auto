package com.whetherman.ruviewauto;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

/** Phone-side placeholder activity (required by AA; does nothing interesting). */
public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        TextView tv = new TextView(this);
        tv.setText("RuView Auto POC\nConnect to Android Auto to see the bouncing ball on your car screen.\nEnable 'Unknown sources' in AA developer settings first.");
        tv.setPadding(40, 100, 40, 40);
        tv.setTextSize(18);
        setContentView(tv);
    }
}
