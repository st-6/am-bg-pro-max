package dev.zen.lyricbg.capture;

import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Narrow exported IPC endpoint; it never owns or starts audio capture. */
public final class AudioBridgeService extends Service {
    private static final int MSG_REGISTER = 1;
    private static final int MSG_UNREGISTER = 2;
    private static final int MSG_PULSE = 3;
    private static final int MSG_STATE = 4;
    private static final int MSG_STOP = 5;
    private static final String KEY_PULSE = "pulse";
    private static final int STATE_IDLE = 0;
    private static final int STATE_CONNECTED = 1;
    private static final int STATE_UNAUTHORIZED = -2;

    private static final List<Messenger> clients = new ArrayList<>();
    private static volatile int captureState = STATE_IDLE;
    static int currentCaptureState() { return captureState; }
    private int appleMusicUid = -1;
    private final Messenger binder = new Messenger(new IncomingHandler());

    @Override public void onCreate() {
        super.onCreate();
        try { appleMusicUid = getPackageManager().getApplicationInfo("com.apple.android.music", 0).uid; }
        catch (Throwable ignored) { appleMusicUid = -1; }
    }

    @Override public IBinder onBind(Intent intent) { return binder.getBinder(); }

    private final class IncomingHandler extends Handler {
        IncomingHandler() { super(Looper.getMainLooper()); }
        @Override public void handleMessage(Message message) {
            if (appleMusicUid < 0 || message.sendingUid != appleMusicUid) {
                if (message.replyTo != null) sendState(message.replyTo, STATE_UNAUTHORIZED);
                return;
            }
            if (message.what == MSG_REGISTER && message.replyTo != null) {
                synchronized (clients) {
                    if (!contains(message.replyTo)) clients.add(message.replyTo);
                }
                sendState(message.replyTo, captureState == STATE_IDLE ? STATE_CONNECTED : captureState);
            } else if (message.what == MSG_UNREGISTER && message.replyTo != null) {
                synchronized (clients) { remove(message.replyTo); }
            } else if (message.what == MSG_STOP) {
                AudioCaptureService.requestStopFromBridge();
            }
        }
        private boolean contains(Messenger value) {
            for (Messenger client : clients) if (client.getBinder().equals(value.getBinder())) return true;
            return false;
        }
        private void remove(Messenger value) {
            Iterator<Messenger> iterator = clients.iterator();
            while (iterator.hasNext()) if (iterator.next().getBinder().equals(value.getBinder())) iterator.remove();
        }
    }

    static void publishPulse(float value) {
        synchronized (clients) {
            Iterator<Messenger> iterator = clients.iterator();
            while (iterator.hasNext()) {
                Messenger client = iterator.next();
                Message message = Message.obtain(null, MSG_PULSE);
                android.os.Bundle data = new android.os.Bundle();
                data.putFloat(KEY_PULSE, value);
                message.setData(data);
                try { client.send(message); } catch (RemoteException error) { iterator.remove(); }
            }
        }
    }

    static void publishState(int value) {
        captureState = value;
        LocalAudioSocketHub.publishState(value);
        synchronized (clients) {
            for (Messenger client : clients) sendState(client, value);
        }
    }

    private static void sendState(Messenger client, int value) {
        Message message = Message.obtain(null, MSG_STATE, value, 0);
        try { client.send(message); } catch (RemoteException ignored) {}
    }
}
