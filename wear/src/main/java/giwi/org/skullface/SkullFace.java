/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package giwi.org.skullface;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.view.SurfaceHolder;

import java.lang.ref.WeakReference;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * Analog watch face with a ticking second hand. In ambient mode, the second hand isn't shown. On
 * devices with low-bit ambient mode, the hands are drawn without anti-aliasing in ambient mode.
 */
public class SkullFace extends CanvasWatchFaceService {
    /**
     * Update rate in milliseconds for interactive mode. We update once a second to advance the
     * second hand.
     */
    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1);
    /**
     * The Back ground image.
     */
    private Bitmap backGroundImage;
    /**
     * The Back ground image low.
     */
    private Bitmap backGroundImageLow;
    private Bitmap hrHand;
    private Bitmap minHand;
    /**
     * The Seconds image.
     */
    private Bitmap secondsImage;
    /**
     * The Ifilter.
     */
    private IntentFilter ifilter;
    /**
     * The Date format.
     */
    private DateFormat dateFormat;
    /**
     * Handler message id for updating the time periodically in interactive mode.
     */
    private static final int MSG_UPDATE_TIME = 0;

    /**
     * On create engine engine.
     *
     * @return the engine
     */
    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    /**
     * The type Engine.
     */
    private class Engine extends CanvasWatchFaceService.Engine {
        /**
         * The Battery status.
         */
        private Intent batteryStatus;
        /**
         * The M background paint.
         */
        Paint mBackgroundPaint;
        /**
         * The Mback batt paint.
         */
        Paint mbackBattPaint;
        /**
         * The M gauge batt paint.
         */
        Paint mGaugeBattPaint;
        /**
         * The Seconds paint.
         */
        Paint secondsPaint;
        /**
         * The Date paint.
         */
        Paint datePaint;
        /**
         * The M ambient.
         */
        boolean mAmbient;
        /**
         * The M time.
         */
        GregorianCalendar mTime = new GregorianCalendar();

        /**
         * The M update time handler.
         */
        final Handler mUpdateTimeHandler = new EngineHandler(this);

        /**
         * The M time zone receiver.
         */
        final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mTime.setTimeZone(TimeZone.getTimeZone(intent.getStringExtra("time-zone")));
            }
        };
        /**
         * The M registered time zone receiver.
         */
        boolean mRegisteredTimeZoneReceiver = false;

        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */
        boolean mLowBitAmbient;

        /**
         * On create.
         *
         * @param holder the holder
         */
        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);
            Locale current = getResources().getConfiguration().locale;
            dateFormat = new SimpleDateFormat("dd", current);
            ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
            backGroundImage = BitmapFactory.decodeResource(getResources(), R.drawable.skull_and_bones);
            backGroundImageLow = BitmapFactory.decodeResource(getResources(), R.drawable.skull_and_bones_low);
            secondsImage = BitmapFactory.decodeResource(getResources(), R.drawable.bg_gradient_black);
            hrHand = BitmapFactory.decodeResource(getResources(), R.drawable.hr_hand);
            minHand = BitmapFactory.decodeResource(getResources(), R.drawable.min_hand);

            setWatchFaceStyle(new WatchFaceStyle.Builder(SkullFace.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_SHORT)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .build());

            Resources resources = SkullFace.this.getResources();

            mBackgroundPaint = new Paint();
            mBackgroundPaint.setFilterBitmap(true);

            secondsPaint = new Paint();
            secondsPaint.setColor(resources.getColor(R.color.material_lightblue));
            secondsPaint.setStrokeWidth(resources.getDimension(R.dimen.analog_hand_stroke));
            secondsPaint.setAntiAlias(true);
            secondsPaint.setStrokeCap(Paint.Cap.ROUND);

            mbackBattPaint = new Paint();
            mbackBattPaint.setColor(resources.getColor(R.color.material_grey));
            mbackBattPaint.setStrokeWidth(resources.getDimension(R.dimen.battery_back_stroke));
            mbackBattPaint.setAntiAlias(true);
            mbackBattPaint.setStyle(Paint.Style.STROKE);
            mGaugeBattPaint = new Paint();
            mGaugeBattPaint.setColor(resources.getColor(R.color.material_red));
            mGaugeBattPaint.setStrokeWidth(resources.getDimension(R.dimen.analog_hand_stroke));
            mGaugeBattPaint.setAntiAlias(true);
            mGaugeBattPaint.setStyle(Paint.Style.STROKE);

            datePaint = new Paint();
            datePaint.setColor(resources.getColor(R.color.material_lightblue));
            datePaint.setTextAlign(Paint.Align.CENTER);
            datePaint.setTextSize(20f);
            datePaint.setAntiAlias(true);
            mTime = new GregorianCalendar();
        }

        /**
         * On destroy.
         */
        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            super.onDestroy();
        }

        /**
         * On properties changed.
         *
         * @param properties the properties
         */
        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
        }

        /**
         * On time tick.
         */
        @Override
        public void onTimeTick() {
            super.onTimeTick();
            invalidate();
        }

        /**
         * On ambient mode changed.
         *
         * @param inAmbientMode the in ambient mode
         */
        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
            if (mAmbient != inAmbientMode) {
                mAmbient = inAmbientMode;
                if (mLowBitAmbient) {
                    mbackBattPaint.setAntiAlias(!inAmbientMode);
                }
                invalidate();
            }
            updateTimer();
        }

        /**
         * On draw.
         *
         * @param canvas the canvas
         * @param bounds the bounds
         */
        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            batteryStatus = getApplicationContext().registerReceiver(null, ifilter);
            mTime = new GregorianCalendar();
            int status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
     /*       boolean isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL;*/
            int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            float batteryPct = level / (float) scale;
            if (mAmbient) {
                datePaint.setColor(getResources().getColor(R.color.material_grey));
                mGaugeBattPaint.setColor(getResources().getColor(R.color.material_grey));
            } else {
                datePaint.setColor(getResources().getColor(R.color.material_lightblue));
                if (batteryPct < 0.25) {
                    mGaugeBattPaint.setColor(getResources().getColor(R.color.material_red));
                } else {
                    mGaugeBattPaint.setColor(getResources().getColor(R.color.material_green));
                }
            }
            int width = bounds.width();
            int height = bounds.height();
            float centerX = width / 2f;
            float centerY = height / 2f;

            canvas.drawColor(Color.BLACK);
            if (!mAmbient) {
                canvas.drawBitmap(Bitmap.createScaledBitmap(backGroundImage, canvas.getWidth(), canvas.getHeight(), false), 0, 0, mBackgroundPaint);
              //  canvas.drawCircle(40f, centerY, 30f, mbackBattPaint);
                RectF rectF = new RectF(10f, centerY - 30f, 70f, centerY + 30f);
                canvas.drawArc(rectF, 0, batteryPct * 360, false, mGaugeBattPaint);
            } else {
                canvas.drawBitmap(Bitmap.createScaledBitmap(backGroundImageLow, canvas.getWidth(), canvas.getHeight(), false), 0, 0, mBackgroundPaint);
            }

            canvas.drawText(dateFormat.format(mTime.getTime()), width - 60f, centerY + 5f, datePaint);
            canvas.drawText(String.valueOf((int) (batteryPct * 100)) + "%", 40f, centerY + 5f, datePaint);

            float secLength = centerX - 20;
            float minLength = centerX - 40;
            float hrLength = centerX - 80;
            float secRot = mTime.get(GregorianCalendar.SECOND) / 30f * (float) Math.PI;
            if (!mAmbient) {
               /* Matrix matrix = new Matrix();
                matrix.postTranslate(-3, 80);
                matrix.postRotate(mTime.second  * 360 / 60 + 180, 0, 0);
                matrix.postTranslate(centerX, centerY);*/
                float secStartX = (float) Math.sin(secRot) * 85;
                float secStartY = (float) -Math.cos(secRot) * 85;
                float secX = (float) Math.sin(secRot) * secLength;
                float secY = (float) -Math.cos(secRot) * secLength;
                canvas.drawLine( centerX + secStartX,  centerY + secStartY, centerX + secX, centerY + secY, secondsPaint);
                //     canvas.drawBitmap(Bitmap.createScaledBitmap(secondsImage, 6, (int) secLength - 100, true), matrix, null);
            }
            Matrix minMatrix = new Matrix();

            if (!mAmbient) {
                minMatrix.postTranslate(-10,  -centerY + 30);
                minMatrix.postRotate(mTime.get(GregorianCalendar.MINUTE) * 360 / 60, 0, 0);
                minMatrix.postTranslate(centerX, centerY);
                canvas.drawBitmap(Bitmap.createScaledBitmap(minHand, 20, 50, true), minMatrix, mBackgroundPaint);
            } else {
                minMatrix.postTranslate(-3, 50);
                minMatrix.postRotate(mTime.get(GregorianCalendar.MINUTE) * 360 / 60 + 180, 0, 0);
                minMatrix.postTranslate(centerX, centerY);
                canvas.drawBitmap(Bitmap.createScaledBitmap(secondsImage, 6, (int) minLength - 50, true), minMatrix, mBackgroundPaint);
            }
            Matrix hrMatrix = new Matrix();
            if (!mAmbient) {
                hrMatrix.postTranslate(-20,  -centerY + 40);
                hrMatrix.postRotate((mTime.get(GregorianCalendar.HOUR) + (mTime.get(GregorianCalendar.MINUTE) / 60f)) * 360 / 12, 0, 0);
                hrMatrix.postTranslate(centerX, centerY);
                canvas.drawBitmap(Bitmap.createScaledBitmap(hrHand,40, 40, true), hrMatrix, mBackgroundPaint);
            } else {
                hrMatrix.postTranslate(-3, 50);
                hrMatrix.postRotate((mTime.get(GregorianCalendar.HOUR) + (mTime.get(GregorianCalendar.MINUTE) / 60f)) * 360 / 12 + 180, 0, 0);
                hrMatrix.postTranslate(centerX, centerY);
                canvas.drawBitmap(Bitmap.createScaledBitmap(secondsImage, 6, (int) hrLength - 50, true), hrMatrix, mBackgroundPaint);
            }
        }

        /**
         * On visibility changed.
         *
         * @param visible the visible
         */
        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);
            if (visible) {
                registerReceiver();
            } else {
                unregisterReceiver();
            }
            updateTimer();
        }

        /**
         * Register receiver.
         */
        private void registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            SkullFace.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        /**
         * Unregister receiver.
         */
        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            SkullFace.this.unregisterReceiver(mTimeZoneReceiver);
        }

        /**
         * Starts the {@link #mUpdateTimeHandler} timer if it should be running and isn't currently
         * or stops it if it shouldn't be running but currently is.
         */
        private void updateTimer() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }

        /**
         * Returns whether the {@link #mUpdateTimeHandler} timer should be running. The timer should
         * only run when we're visible and in interactive mode.
         *
         * @return the boolean
         */
        private boolean shouldTimerBeRunning() {
            return isVisible() && !isInAmbientMode();
        }

        /**
         * Handle updating the time periodically in interactive mode.
         */
        private void handleUpdateTimeMessage() {
            invalidate();
            if (shouldTimerBeRunning()) {
                long timeMs = System.currentTimeMillis();
                long delayMs = INTERACTIVE_UPDATE_RATE_MS
                        - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
                mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
            }
        }
    }

    /**
     * The type Engine handler.
     */
    private static class EngineHandler extends Handler {
        /**
         * The M weak reference.
         */
        private final WeakReference<SkullFace.Engine> mWeakReference;

        /**
         * Instantiates a new Engine handler.
         *
         * @param reference the reference
         */
        public EngineHandler(SkullFace.Engine reference) {
            mWeakReference = new WeakReference<>(reference);
        }

        /**
         * Handle message.
         *
         * @param msg the msg
         */
        @Override
        public void handleMessage(Message msg) {
            SkullFace.Engine engine = mWeakReference.get();
            if (engine != null) {
                switch (msg.what) {
                    case MSG_UPDATE_TIME:
                        engine.handleUpdateTimeMessage();
                        break;
                }
            }
        }
    }
}
