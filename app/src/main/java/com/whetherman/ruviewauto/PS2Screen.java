package com.whetherman.ruviewauto;

import android.graphics.Rect;
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

import com.virtualapplications.play.NativeInterop;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Step 2: attach Play!'s native PS2 renderer directly to the Android Auto
 * nav-template Surface instead of drawing a Canvas bouncing ball. Boots a
 * free/legal homebrew ELF (no BIOS dump needed; Play! uses an HLE BIOS).
 */
public class PS2Screen extends Screen implements SurfaceCallback, DefaultLifecycleObserver {
    private static final String TAG = "RuViewPS2";
    private static final String DEMO_ASSET = "console_demo.elf";

    private boolean _vmBooted = false;

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
                .addAction(new Action.Builder().setTitle("RuView PS2").build())
                .build();
        return new NavigationTemplate.Builder()
                .setActionStrip(actionStrip)
                .build();
    }

    @Override
    public void onSurfaceAvailable(@NonNull SurfaceContainer surfaceContainer) {
        Surface surface = surfaceContainer.getSurface();
        Log.i(TAG, "onSurfaceAvailable " + surfaceContainer.getWidth() + "x" + surfaceContainer.getHeight());
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
}
