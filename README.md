# Simple HTTP Browser – Java Client/Server

Đồ án Lập trình mạng xây dựng một browser đơn giản theo mô hình TCP Client–Server.

## Chức năng

- Client Java Swing dùng FlatLaf.
- Client chỉ giao tiếp với Java Server qua TCP Socket tại `localhost:9090`.
- Server gửi HTTP request ra website bằng Java `HttpClient`.
- Hỗ trợ `GET`, `POST`, `HEAD` và chuyển hướng HTTP.
- Hiển thị status code, final URL, headers, thời gian phản hồi và mã HTML.
- Đếm số thẻ `<p>`, `<div>`, `<span>`, `<img>` bằng Jsoup.
- Xem trước HTML bằng JavaFX WebView.
- Client kiểm tra Server Online/Offline và không làm treo giao diện khi gửi request.

## Cấu trúc

```text
SimpleHttpBrowser/
├── common/   DTO dùng chung qua ObjectInputStream/ObjectOutputStream
├── server/   TCP server và HTTP proxy
├── client/   Swing + FlatLaf + JavaFX WebView
├── pom.xml
└── README.md
```127.0.0

## Yêu cầu

- JDK 17 trở lên.
- Apache Maven 3.9 trở lên.
- Máy tính có Internet khi chạy lần đầu để Maven tải dependencies.

Kiểm tra:

```bash
java -version
mvn -version
```

## Chạy bằng IntelliJ IDEA

1. Chọn **Open** và mở thư mục `SimpleHttpBrowser`.
2. Chờ Maven tải dependencies.
3. Chạy `server/src/main/java/vn/edu/network/server/ServerMain.java` trước.
4. Chạy `client/src/main/java/vn/edu/network/client/ClientMain.java` sau.
5. Client phải hiển thị `Server Online - localhost:9090`.

## Chạy bằng terminal

Tại thư mục project:

```bash
mvn clean install
```

Mở terminal thứ nhất để chạy Server:

```bash
java -jar server/target/simple-http-browser-server.jar
```

Mở terminal thứ hai để chạy Client:

```bash
mvn -pl client javafx:run
```

## Cách kiểm thử

### GET

Nhập:

```text
https://example.com
```

Chọn `GET` và nhấn **Gửi yêu cầu**. Client hiển thị HTML, headers và thống kê tag.

### HEAD

Chọn `HEAD` với cùng URL. Response không có body; xem thông tin tại tab **Headers**.

### POST

URL kiểm thử:

```text
https://httpbin.org/post
```

Dữ liệu POST:

```text
name=KyVy&message=Hello
```

Một website thông thường có thể trả `405 Method Not Allowed` nếu endpoint không cho phép POST.

## Luồng hoạt động

```text
Swing Client
    │ TCP Socket, BrowserRequest
    ▼
Java Server localhost:9090
    │ HTTP GET / POST / HEAD
    ▼
Web Server của URL
    │ HTTP response
    ▼
Java Server phân tích HTML
    │ BrowserResponse
    ▼
Swing Client hiển thị kết quả
```

## Lưu ý về tab Xem trước

Server trả HTML về Client và WebView render nội dung đó. Project tự thêm thẻ `<base>` để ảnh, CSS và link tương đối có thể tham chiếu URL gốc. Một số website hiện đại vẫn có thể hiển thị không hoàn chỉnh vì dùng JavaScript phức tạp, cookie, CSP, chống bot hoặc API nội bộ.

## Đổi địa chỉ Server

Mặc định Client kết nối:

```java
new ServerConnection("localhost", 9090)
```

Nếu Server chạy trên máy khác trong LAN, sửa `localhost` thành IP của máy Server, ví dụ:

```java
new ServerConnection("192.168.1.100", 9090)
```

Đồng thời cho phép cổng TCP `9090` qua firewall của máy Server.
