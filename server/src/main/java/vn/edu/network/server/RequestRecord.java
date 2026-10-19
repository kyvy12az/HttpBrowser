package vn.edu.network.server;

import vn.edu.network.common.HttpMethodType;

import java.time.LocalDateTime;

record RequestRecord(
        LocalDateTime timestamp,
        String clientIp,
        HttpMethodType method,
        String url,
        int statusCode,
        String reasonPhrase,
        long elapsedMillis,
        boolean successful
) {
}
