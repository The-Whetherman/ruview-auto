package com.whetherman.ruviewauto;

import android.graphics.*;
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

/**
 * Step-1 proof of concept: draws a bouncing ball directly onto the
 * raw Surface Android Auto hands us via the Navigation template's
 * SurfaceCallback. If this renders on the car's screen, we've proven
 * out the "hijack the nav canvas for arbitrary video" trick, and the
 * same render loop is where a real emulator's framebuffer would go
 * instead of ball.draw(canvas).
 */
public class BouncingBallScreen extends Screen implements SurfaceCallback, DefaultLifecycleObserver {
    private static final String TAG = "RuViewPOC";

    private Surface _surface;
    private int _width;
    private int _height;
    private Thread _renderThread;
    private volatile boolean _running = false;

    private float _x = 100, _y = 100;
    private float _vx = 14, _vy = 9;
    private final Paint _ballPaint = new Paint();
    private final Paint _bgPaint = new Paint();
    private final Paint _textPaint = new Paint();

    public BouncingBallScreen(@NonNull CarContext carContext) {
        super(carContext);
        getLifecycle().addObserver(this);
        _ballPaint.setColor(Color.CYAN);
        _ballPaint.setAntiAlias(true);
        _bgPaint.setColor(Color.BLACK);
        _textPaint.setColor(Color.GREEN);
        _textPaint.setTextSize(48);
        _textPaint.setAntiAlias(true);
    }

    @Override
    public void onStart(@NonNull LifecycleOwner owner) {
        getCarContext().getCarService(AppManager.class).setSurfaceCallback(this);
    }

    @NonNull
    @Override
    public Template onGetTemplate() {
        ActionStrip actionStrip = new ActionStrip.Builder()
                .addAction(new Action.Builder()
                        .setTitle("RuView POC")
                        .build())
                .build();

        return new NavigationTemplate.Builder()
                .setActionStrip(actionStrip)
                .build();
    }

    @Override
    public void onSurfaceAvailable(@NonNull SurfaceContainer surfaceContainer) {
        Log.i(TAG, "onSurfaceAvailable " + surfaceContainer.getWidth() + "x" + surfaceContainer.getHeight());
        _surface = surfaceContainer.getSurface();
        _width = surfaceContainer.getWidth();
        _height = surfaceContainer.getHeight();
        startRenderLoop();
    }

    @Override
    public void onSurfaceDestroyed(@NonNull SurfaceContainer surfaceContainer) {
        Log.i(TAG, "onSurfaceDestroyed");
        stopRenderLoop();
        _surface = null;
    }

    @Override
    public void onVisibleAreaChanged(@NonNull Rect visibleArea) {
        // no-op for POC
    }

    private void startRenderLoop() {
        if (_running) return;
        _running = true;
        _renderThread = new Thread(this::renderLoop, "RuView-Render");
        _renderThread.start();
    }

    private void stopRenderLoop() {
        _running = false;
        if (_renderThread != null) {
            try {
                _renderThread.join(500);
            } catch (InterruptedException ignored) {
            }
            _renderThread = null;
        }
    }

    private void renderLoop() {
        while (_running && _surface != null) {
            if (!_surface.isValid()) {
                break;
            }
            Canvas canvas = null;
            try {
                canvas = _surface.lockCanvas(null);
                canvas.drawRect(0, 0, _width, _height, _bgPaint);

                _x += _vx;
                _y += _vy;
                float r = Math.min(_width, _height) * 0.05f;
                if (_x - r < 0 || _x + r > _width) _vx = -_vx;
                if (_y - r < 0 || _y + r > _height) _vy = -_vy;
                canvas.drawCircle(_x, _y, r, _ballPaint);
                canvas.drawText("RuView AA POC - this surface is hijackable", 40, 60, _textPaint);
            } catch (Exception e) {
                Log.w(TAG, "render error", e);
            } finally {
                if (canvas != null) {
                    try {
                        _surface.unlockCanvasAndPost(canvas);
                    } catch (Exception ignored) {
                    }
                }
            }
            try {
                Thread.sleep(16); // ~60fps target
            } catch (InterruptedException ignored) {
            }
        }
    }
}
