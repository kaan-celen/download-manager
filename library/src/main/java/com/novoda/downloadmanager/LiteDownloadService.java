package com.novoda.downloadmanager;

import static android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC;

import android.app.ForegroundServiceStartNotAllowedException;
import android.app.Notification;
import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleEventObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ProcessLifecycleOwner;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class LiteDownloadService extends Service implements DownloadService, LifecycleEventObserver {

    private static final long TEN_MINUTES_IN_MILLIS = TimeUnit.MINUTES.toMillis(10);
    private static final String WAKELOCK_TAG = "liteDownloadService:wakelocktag";

    private ExecutorService executor;
    private IBinder binder;
    private PowerManager.WakeLock wakeLock;
    private Boolean appIsInForeground;

    @Override
    public void onCreate() {
        super.onCreate();
        executor = Executors.newSingleThreadExecutor();
        binder = new DownloadServiceBinder();
        ProcessLifecycleOwner.get().getLifecycle().addObserver(this);
    }

    @Override
    public void start(int id, Notification notification) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            startAsForegroundService(id, notification);
        } else if (isAppForeground()) {
            try {
                startAsForegroundService(id, notification);
            } catch (ForegroundServiceStartNotAllowedException e) {
                Logger.e(e, "Failure to start as Foreground service with notification from background");
            }
        } else {
            Logger.e("Failure to start as Foreground service. Does not meet requirements.");
        }
    }

    @Override
    public void stop(boolean removeNotification) {
        stopForeground(removeNotification);
    }

    @Override
    public void onStateChanged(@NonNull LifecycleOwner source, @NonNull Lifecycle.Event event) {
        if (event == Lifecycle.Event.ON_RESUME) {
            appIsInForeground = true;
        }
        if (event == Lifecycle.Event.ON_PAUSE) {
            appIsInForeground = false;
        }
    }

    class DownloadServiceBinder extends Binder {
        DownloadService getService() {
            return LiteDownloadService.this;
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void download(DownloadBatch downloadBatch, DownloadBatchStatusCallback callback) {
        callback.onUpdate(downloadBatch.status().copy());
        downloadBatch.setCallback(callback);

        executor.execute(() -> {
            acquireCpuWakeLock();
            downloadBatch.persist();
            downloadBatch.download();
            releaseHeldCpuWakeLock();
        });
    }

    private boolean isAppForeground() {
        return appIsInForeground != null && appIsInForeground;
    }

    private void startAsForegroundService(int id, Notification notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Apps built with SDK version Build. VERSION_CODES. Q or later can specify
            // the foreground service types using attribute android. R. attr. foregroundServiceType
            // in service element of manifest file.
            startForeground(id, notification, FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
            startForeground(id, notification);
        }
    }

    private void acquireCpuWakeLock() {
        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        if (powerManager != null) {
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKELOCK_TAG);
            wakeLock.acquire(TEN_MINUTES_IN_MILLIS);
        }
    }

    private void releaseHeldCpuWakeLock() {
        if (wakeLock.isHeld()) {
            wakeLock.release();
        }
    }

    @Override
    public void onDestroy() {
        executor.shutdown();
        ProcessLifecycleOwner.get().getLifecycle().removeObserver(this);
        super.onDestroy();
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        super.onTaskRemoved(rootIntent);
        stopSelf();
    }
}
