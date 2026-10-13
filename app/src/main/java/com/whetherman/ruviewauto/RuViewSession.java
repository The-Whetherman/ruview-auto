package com.whetherman.ruviewauto;

import androidx.annotation.NonNull;
import androidx.car.app.Screen;
import androidx.car.app.Session;
import androidx.car.app.CarContext;
import android.content.Intent;

public class RuViewSession extends Session {
    @NonNull
    @Override
    public Screen onCreateScreen(@NonNull Intent intent) {
        return new PS2Screen(getCarContext());
    }
}
