package vn.edu.network.client;

import vn.edu.network.common.BrowserRequest;
import vn.edu.network.common.BrowserResponse;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;

final class ServerConnection {
    private final String host;
    private final int port;

    ServerConnection(String host, int port) {
        this.host = host;
        this.port = port;
    }

    BrowserResponse send(BrowserRequest request) throws Exception {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 2_000);
            socket.setSoTimeout(40_000);

            try (ObjectOutputStream output = new ObjectOutputStream(socket.getOutputStream());
                 ObjectInputStream input = new ObjectInputStream(socket.getInputStream())) {
                output.writeObject(request);
                output.flush();

                Object value = input.readObject();
                if (value instanceof BrowserResponse response) {
                    return response;
                }
                throw new IllegalStateException("Server trả về dữ liệu không hợp lệ.");
            }
        }
    }

    boolean ping() {
        try {
            return send(BrowserRequest.ping()).success();
        } catch (Exception e) {
            return false;
        }
    }
}
