package dev.zen.lyricbg.capture;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/** Single loopback transport for capture state and pulse samples. */
final class LocalAudioSocketHub {
    static final int PORT = 38471;
    private static final List<Client> clients = new ArrayList<>();
    private static volatile ServerSocket server;
    private static volatile boolean running;
    private static long pulseCalls, queuedPackets, acknowledgements, writeFailures;
    private static float lastPulse;
    private static volatile String lastError = "none";

    private LocalAudioSocketHub() {}

    static synchronized void start() {
        if (running) return;
        try {
            pulseCalls = queuedPackets = acknowledgements = writeFailures = 0L;
            lastPulse = 0f;
            lastError = "none";
            ServerSocket socket = new ServerSocket();
            socket.setReuseAddress(true);
            socket.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), PORT), 4);
            server = socket;
            running = true;
            Thread acceptor = new Thread(LocalAudioSocketHub::acceptLoop, "am-bg-pro-max-pulse-accept");
            acceptor.setDaemon(true);
            acceptor.start();
        } catch (IOException error) {
            lastError = error.getClass().getSimpleName() + ": " + error.getMessage();
            running = false;
        }
    }

    private static void acceptLoop() {
        while (running) {
            try {
                Socket socket = server.accept();
                socket.setTcpNoDelay(true);
                Client client = new Client(socket);
                synchronized (clients) {
                    if (clients.size() >= 4) closeClient(clients.remove(0));
                    clients.add(client);
                    client.start();
                    client.enqueue("S " + AudioBridgeService.currentCaptureState());
                    if (pulseCalls > 0L) client.enqueue("P " + lastPulse);
                }
                Thread reader = new Thread(() -> readCommands(client), "am-bg-pro-max-pulse-ack-reader");
                reader.setDaemon(true);
                reader.start();
            } catch (IOException error) {
                if (!running) return;
                lastError = "accept " + error.getClass().getSimpleName() + ": " + error.getMessage();
            }
        }
    }

    private static void readCommands(Client client) {
        try (BufferedReader input = new BufferedReader(new InputStreamReader(client.socket.getInputStream(), "UTF-8"))) {
            String command;
            while ((command = input.readLine()) != null) {
                if ("STOP".equals(command)) AudioCaptureService.requestStopFromBridge();
                else if ("ACK".equals(command)) synchronized (clients) { acknowledgements++; }
            }
        } catch (IOException error) {
            lastError = "read " + error.getClass().getSimpleName() + ": " + error.getMessage();
        } finally {
            synchronized (clients) { clients.remove(client); }
            closeClient(client);
        }
    }

    static void publishState(int state) {
        synchronized (clients) {
            Iterator<Client> iterator = clients.iterator();
            while (iterator.hasNext()) if (!iterator.next().enqueue("S " + state)) iterator.remove();
        }
    }

    static void publishPulse(float pulse) {
        synchronized (clients) {
            pulseCalls++;
            lastPulse = pulse;
            Iterator<Client> iterator = clients.iterator();
            while (iterator.hasNext()) {
                if (iterator.next().enqueue("P " + pulse)) queuedPackets++;
                else iterator.remove();
            }
        }
    }

    static String diagnostics() {
        synchronized (clients) {
            return "clients=" + clients.size() + ", calls=" + pulseCalls + ", queued=" + queuedPackets
                    + ", ACKs=" + acknowledgements + ", write errors=" + writeFailures
                    + ", latest=" + lastPulse + ", I/O=" + lastError;
        }
    }

    static synchronized void stop() {
        running = false;
        ServerSocket old = server;
        server = null;
        if (old != null) try { old.close(); } catch (IOException ignored) {}
        synchronized (clients) {
            for (Client client : clients) closeClient(client);
            clients.clear();
        }
    }

    private static void closeClient(Client client) {
        client.running = false;
        client.writer.interrupt();
        try { client.socket.close(); } catch (IOException ignored) {}
    }

    private static final class Client {
        final Socket socket;
        final BlockingQueue<String> queue = new ArrayBlockingQueue<>(8);
        final Thread writer;
        volatile boolean running = true;
        Client(Socket socket) {
            this.socket = socket;
            writer = new Thread(this::writeLoop, "am-bg-pro-max-pulse-writer");
            writer.setDaemon(true);
        }
        void start() { writer.start(); }
        boolean enqueue(String line) {
            if (!running) return false;
            if (queue.offer(line)) return true;
            queue.poll();
            return queue.offer(line);
        }
        private void writeLoop() {
            try (OutputStreamWriter output = new OutputStreamWriter(socket.getOutputStream(), "UTF-8")) {
                while (running) {
                    String line = queue.poll(1, TimeUnit.SECONDS);
                    if (line == null) continue;
                    output.write(line);
                    output.write('\n');
                    output.flush();
                }
            } catch (IOException error) {
                lastError = "write " + error.getClass().getSimpleName() + ": " + error.getMessage();
                synchronized (clients) { writeFailures++; clients.remove(this); }
                closeClient(this);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }
    }
}