package vn.edu.network.server;

import vn.edu.network.common.BrowserRequest;
import vn.edu.network.common.BrowserResponse;
import vn.edu.network.common.HttpMethodType;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

final class ServerController {
    interface Listener {
        void onServerStarted(String host, int port);
        void onServerStopped();
        void onRequestStarted(String clientIp, BrowserRequest request);
        void onRequestFinished(RequestRecord record);
        void onLog(String message, LogType type);
    }

    enum LogType { INFO, METHOD, SUCCESS, ERROR }

    private final HttpProxyService proxyService = new HttpProxyService();
    private final Listener listener;
    private final AtomicBoolean running = new AtomicBoolean();
    private volatile ServerSocket serverSocket;
    private volatile ExecutorService clientPool;

    ServerController(Listener listener) {
        this.listener = listener;
    }

    synchronized void start(String host, int port) throws IOException {
        if (running.get()) {
            return;
        }
        if (port < 1 || port > 65_535) {
            throw new IllegalArgumentException("Cổng phải nằm trong khoảng 1 - 65535.");
        }

        InetAddress address = InetAddress.getByName(host);
        ServerSocket socket = new ServerSocket();
        socket.setReuseAddress(true);
        socket.bind(new InetSocketAddress(address, port));
        serverSocket = socket;
        clientPool = Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable, "http-client-worker");
            thread.setDaemon(true);
            return thread;
        });
        running.set(true);
        Thread acceptThread = new Thread(this::acceptClients, "http-server-accept");
        acceptThread.setDaemon(true);
        acceptThread.start();
        listener.onServerStarted(host, socket.getLocalPort());
    }

    synchronized void stop() {
        if (!running.getAndSet(false)) {
            return;
        }
        closeServerSocket();
        ExecutorService pool = clientPool;
        if (pool != null) {
            pool.shutdownNow();
        }
        listener.onServerStopped();
    }

    boolean isRunning() {
        return running.get();
    }

    private void acceptClients() {
        while (running.get()) {
            try {
                Socket socket = serverSocket.accept();
                socket.setSoTimeout(35_000);
                ExecutorService pool = clientPool;
                if (pool != null && !pool.isShutdown()) {
                    pool.submit(() -> handleClient(socket));
                } else {
                    socket.close();
                }
            } catch (SocketException e) {
                if (running.get()) {
                    listener.onLog("Lỗi socket: " + safeMessage(e), LogType.ERROR);
                    stop();
                }
            } catch (IOException e) {
                if (running.get()) {
                    listener.onLog("Không thể nhận kết nối: " + safeMessage(e), LogType.ERROR);
                }
            }
        }
    }

    private void handleClient(Socket socket) {
        String clientIp = socket.getInetAddress().getHostAddress();
        try (socket;
             ObjectOutputStream output = new ObjectOutputStream(socket.getOutputStream());
             ObjectInputStream input = new ObjectInputStream(socket.getInputStream())) {

            Object value = input.readObject();
            if (!(value instanceof BrowserRequest request)) {
                writeResponse(output, BrowserResponse.error("Dữ liệu request không hợp lệ."));
                listener.onLog("Client " + clientIp + " gửi dữ liệu không hợp lệ", LogType.ERROR);
                return;
            }
            if (request.method() == HttpMethodType.PING) {
                writeResponse(output, BrowserResponse.pong());
                return;
            }

            LocalDateTime startedAt = LocalDateTime.now();
            long startedNanos = System.nanoTime();
            listener.onRequestStarted(clientIp, request);
            BrowserResponse response = proxyService.execute(request);
            long elapsed = Math.max(response.elapsedMillis(),
                    (System.nanoTime() - startedNanos) / 1_000_000);
            boolean successful = response.success()
                    && response.statusCode() >= 200
                    && response.statusCode() < 400;
            listener.onRequestFinished(new RequestRecord(
                    startedAt, clientIp, request.method(), request.url(),
                    response.statusCode(),
                    response.success() ? response.reasonPhrase() : response.errorMessage(),
                    elapsed, successful));
            writeResponse(output, response);
        } catch (Exception e) {
            listener.onLog("Lỗi client " + clientIp + ": " + safeMessage(e), LogType.ERROR);
        }
    }

    private void writeResponse(ObjectOutputStream output, BrowserResponse response) throws IOException {
        output.writeObject(response);
        output.flush();
    }

    private void closeServerSocket() {
        ServerSocket socket = serverSocket;
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException ignored) {
                // Socket is already closing.
            }
        }
    }

    private String safeMessage(Exception exception) {
        return exception.getMessage() == null
                ? exception.getClass().getSimpleName()
                : exception.getMessage();
    }
}
