package com.whetherman.ruviewauto;

import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Surface;

import androidx.annotation.NonNull;
import androidx.car.app.AppManager;
import androidx.car.app.CarContext;
import androidx.car.app.Screen;
import androidx.car.app.SurfaceCallback;
import androidx.car.app.SurfaceContainer;
import androidx.car.app.model.Action;
import androidx.car.app.model.ActionStrip;
import androidx.car.app.model.Template;
import androidx.car.app.navigation.model.NavigationTemplate;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;

import com.virtualapplications.play.InputManager;
import com.virtualapplications.play.InputManagerConstants;
import com.virtualapplications.play.NativeInterop;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Step 2: attach Play!'s native PS2 renderer directly to the Android Auto
 * nav-template Surface instead of drawing a Canvas bouncing ball. Boots a
 * free/legal homebrew ELF (no BIOS dump needed; Play! uses an HLE BIOS).
 *
 * Step 3: tap-zone virtual controller using SurfaceCallback.onClick(x, y)
 * (the only input hook androidx.car.app exposes on this surface). No
 * visible button labels -- Play!'s renderer owns the whole surface -- so
 * this is a "lights out" controller: screen split into quadrants.
 *   Left half:  tap relative to its center -> D-pad UP/DOWN/LEFT/RIGHT
 *   Right half: tap relative to its center -> TRIANGLE/CROSS/SQUARE/CIRCLE
 *   Top strip:  left corner = SELECT, right corner = START
 * Each tap is a quick press+release pulse (onClick is a discrete event,
 * not press-and-hold), which is enough for menus/turn-based input but not
 * smooth continuous movement.
 */
public class PS2Screen extends Screen implements SurfaceCallback, DefaultLifecycleObserver {
    private static final String TAG = "RuViewPS2";
    private static final String DEMO_ASSET = "console_demo.elf";
    private static final long TAP_PULSE_MS = 120;

    private boolean _vmBooted = false;
    private int _surfaceWidth = 0;
    private int _surfaceHeight = 0;
    private final Handler _handler = new Handler(Looper.getMainLooper());

    public PS2Screen(@NonNull CarContext carContext) {
        super(carContext);
        getLifecycle().addObserver(this);
        ensureVirtualMachine();
    }

    private void ensureVirtualMachine() {
        if (!NativeInterop.isVirtualMachineCreated()) {
            CarContext ctx = getCarContext();
            NativeInterop.setFilesDirPath(ctx.getFilesDir().getAbsolutePath());
            NativeInterop.setCacheDirPath(ctx.getCacheDir().getAbsolutePath());
            NativeInterop.setAssetManager(ctx.getAssets());
            NativeInterop.createVirtualMachine();
            Log.i(TAG, "Virtual machine created");
        }
    }

    private String extractDemoElf() {
        try {
            File outFile = new File(getCarContext().getFilesDir(), DEMO_ASSET);
            if (!outFile.exists() || outFile.length() == 0) {
                try (InputStream in = getCarContext().getAssets().open(DEMO_ASSET);
                     OutputStream out = new FileOutputStream(outFile)) {
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = in.read(buf)) > 0) {
                        out.write(buf, 0, n);
                    }
                }
            }
            return outFile.getAbsolutePath();
        } catch (Exception e) {
            Log.e(TAG, "Failed to extract demo ELF", e);
            return null;
        }
    }

    @Override
    public void onStart(@NonNull LifecycleOwner owner) {
        getCarContext().getCarService(AppManager.class).setSurfaceCallback(this);
    }

    @NonNull
    @Override
    public Template onGetTemplate() {
        ActionStrip actionStrip = new ActionStrip.Builder()
                .addAction(Action.PAN)
                .addAction(new Action.Builder().setTitle("RuView PS2").build())
                .build();
        return new NavigationTemplate.Builder()
                .setActionStrip(actionStrip)
                .build();
    }

    @Override
    public void onSurfaceAvailable(@NonNull SurfaceContainer surfaceContainer) {
        Surface surface = surfaceContainer.getSurface();
        _surfaceWidth = surfaceContainer.getWidth();
        _surfaceHeight = surfaceContainer.getHeight();
        Log.i(TAG, "onSurfaceAvailable " + _surfaceWidth + "x" + _surfaceHeight);
        try {
            NativeInterop.setupGsHandler(surface);
            if (!_vmBooted) {
                String elfPath = extractDemoElf();
                if (elfPath != null) {
                    NativeInterop.loadElf(elfPath);
                    _vmBooted = true;
                }
            }
            NativeInterop.resumeVirtualMachine();
        } catch (Exception e) {
            Log.e(TAG, "Failed to start PS2 rendering", e);
        }
    }

    @Override
    public void onSurfaceDestroyed(@NonNull SurfaceContainer surfaceContainer) {
        Log.i(TAG, "onSurfaceDestroyed");
        try {
            NativeInterop.pauseVirtualMachine();
        } catch (Exception e) {
            Log.e(TAG, "pause failed", e);
        }
    }

    @Override
    public void onVisibleAreaChanged(@NonNull Rect visibleArea) {
        // no-op
    }

    @Override
    public void onClick(float x, float y) {
        if (_surfaceWidth == 0 || _surfaceHeight == 0) return;

        float topStripHeight = _surfaceHeight * 0.12f;
        if (y < topStripHeight) {
            // top strip: left = SELECT, right = START
            pulseButton(x < _surfaceWidth / 2f
                    ? InputManagerConstants.BUTTON_SELECT
                    : InputManagerConstants.BUTTON_START);
            return;
        }

        boolean leftHalf = x < _surfaceWidth / 2f;
        float halfCenterX = leftHalf ? _surfaceWidth * 0.25f : _surfaceWidth * 0.75f;
        float centerY = (_surfaceHeight + topStripHeight) / 2f;
        float dx = x - halfCenterX;
        float dy = y - centerY;

        int button;
        if (Math.abs(dx) > Math.abs(dy)) {
            if (leftHalf) {
                button = dx > 0 ? InputManagerConstants.BUTTON_RIGHT : InputManagerConstants.BUTTON_LEFT;
            } else {
                button = dx > 0 ? InputManagerConstants.BUTTON_CIRCLE : InputManagerConstants.BUTTON_SQUARE;
            }
        } else {
            if (leftHalf) {
                button = dy > 0 ? InputManagerConstants.BUTTON_DOWN : InputManagerConstants.BUTTON_UP;
            } else {
                button = dy > 0 ? InputManagerConstants.BUTTON_CROSS : InputManagerConstants.BUTTON_TRIANGLE;
            }
        }
        pulseButton(button);
    }

    private void pulseButton(int button) {
        Log.i(TAG, "pulseButton " + button);
        try {
            InputManager.setButtonState(button, true);
            _handler.postDelayed(() -> {
                try {
                    InputManager.setButtonState(button, false);
                } catch (Exception e) {
                    Log.e(TAG, "button release failed", e);
                }
            }, TAP_PULSE_MS);
        } catch (Exception e) {
            Log.e(TAG, "button press failed", e);
        }
    }
}
