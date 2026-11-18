/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  android.content.ComponentName
 *  android.content.Context
 *  android.content.Intent
 */
package dev.zen.lyricbg.audio;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import dev.zen.lyricbg.audio.AudioLevelSource;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;

final class AudioPlaybackBridge {
    private static final int PORT = 38471;
    private final Context app;
    private final AudioLevelSource sink;
    private volatile boolean running;
    private volatile Socket socket;
    private volatile OutputStreamWriter commands;
    private Thread worker;

    AudioPlaybackBridge(Context context, AudioLevelSource audioLevelSource) {
        this.app = context.getApplicationContext();
        this.sink = audioLevelSource;
    }

    synchronized void bind() {
        if (this.running) {
            return;
        }
        this.running = true;
        this.sink.setBridgeState(-3);
        this.worker = new Thread(this::connectLoop, "ampp-audio-bridge-client");
        this.worker.setDaemon(true);
        this.worker.start();
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    private void connectLoop() {
        while (this.running) {
            Socket socket = new Socket();
            try {
                socket.connect(new InetSocketAddress("127.0.0.1", 38471), 900);
                socket.setTcpNoDelay(true);
                this.socket = socket;
                this.commands = new OutputStreamWriter(socket.getOutputStream(), "UTF-8");
                this.sink.setBridgeState(-3);
                try (BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));){
                    String string;
                    while (this.running && (string = bufferedReader.readLine()) != null) {
                        this.accept(string);
                    }
                }
            }
            catch (Throwable throwable) {
            }
            finally {
                this.socket = null;
                this.commands = null;
                try {
                    socket.close();
                }
                catch (Throwable throwable) {}
            }
            if (!this.running) continue;
            this.sink.setBridgeState(-1);
            try {
                Thread.sleep(900L);
            }
            catch (InterruptedException interruptedException) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private void accept(String string) {
        try {
            if (string.startsWith("S ")) {
                this.sink.setBridgeState(Integer.parseInt(string.substring(2).trim()));
            } else if (string.startsWith("P ")) {
                this.sink.acceptExternalPulse(Float.parseFloat(string.substring(2).trim()));
                this.sendAck();
            }
        }
        catch (RuntimeException runtimeException) {
            // empty catch block
        }
    }

    private synchronized void sendAck() {
        OutputStreamWriter outputStreamWriter = this.commands;
        if (outputStreamWriter == null) {
            return;
        }
        try {
            outputStreamWriter.write("ACK\n");
            outputStreamWriter.flush();
        }
        catch (Throwable throwable) {
            // empty catch block
        }
    }

    synchronized void unbind() {
        Socket socket;
        this.running = false;
        OutputStreamWriter outputStreamWriter = this.commands;
        if (outputStreamWriter != null) {
            try {
                outputStreamWriter.write("STOP\n");
                outputStreamWriter.flush();
            }
            catch (Throwable throwable) {
                // empty catch block
            }
        }
        if ((socket = this.socket) != null) {
            try {
                socket.close();
            }
            catch (Throwable throwable) {
                // empty catch block
            }
        }
        Thread thread = this.worker;
        this.worker = null;
        if (thread != null) {
            thread.interrupt();
        }
        this.sink.setBridgeState(0);
    }

    String openHelper() {
        try {
            Intent intent = new Intent().setComponent(new ComponentName("dev.zen.lyricbg.capture", "dev.zen.lyricbg.capture.CaptureActivity"));
            intent.addFlags(0x10000000);
            this.app.startActivity(intent);
            return "已打开音频捕获；请在应用中启动音频分析或同意备用系统授权";
        }
        catch (Throwable throwable) {
            return "无法打开音频捕获，请先更新版应用 APK";
        }
    }
}

