package io.codehunters.fileshare.config;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/** A file-share service and token endpoint on a local port, recording what clients send. */
final class FakeFileShareServer implements AutoCloseable {

  static final String FILE_ID = "11111111-1111-1111-1111-111111111111";
  static final String DOCUMENT_PATH = "/api/v1/shared/REF-1/documents/" + FILE_ID;
  static final String ACCESS_TOKEN = "token-1";
  static final int CONTENT_CHUNKS = 4;
  static final int CONTENT_CHUNK_SIZE = 1024;

  private static final String DOCUMENT =
      """
      {"id":"%s","referenceId":"REF-1","workflowId":"22222222-2222-2222-2222-222222222222",
       "contentType":"application/pdf","size":10,"filename":"f.pdf","status":"COMPLETED"}
      """
          .formatted(FILE_ID);
  private static final String TOKEN =
      """
      {"access_token":"%s","token_type":"Bearer","expires_in":300}
      """
          .formatted(ACCESS_TOKEN);

  private final HttpServer server;
  private final List<String> authorizations = new CopyOnWriteArrayList<>();
  private final List<String> customHeaders = new CopyOnWriteArrayList<>();
  private final AtomicInteger tokenRequests = new AtomicInteger();
  private volatile Duration documentDelay = Duration.ZERO;
  private volatile int tokenStatus = 200;
  private volatile Duration contentChunkGap = Duration.ZERO;
  private volatile boolean redirectContent;
  private final AtomicInteger redirectTargetHits = new AtomicInteger();

  private FakeFileShareServer(HttpServer server) {
    this.server = server;
    server.createContext("/token", this::token);
    server.createContext(DOCUMENT_PATH + "/content", this::content);
    server.createContext("/elsewhere", this::elsewhere);
    server.createContext(DOCUMENT_PATH, this::document);
    server.setExecutor(Executors.newCachedThreadPool());
    server.start();
  }

  static FakeFileShareServer start() throws IOException {
    return new FakeFileShareServer(HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0));
  }

  String url() {
    return "http://127.0.0.1:" + server.getAddress().getPort();
  }

  List<String> authorizations() {
    return authorizations;
  }

  List<String> customHeaders() {
    return customHeaders;
  }

  int tokenRequests() {
    return tokenRequests.get();
  }

  void delayDocument(Duration delay) {
    this.documentDelay = delay;
  }

  int redirectTargetHits() {
    return redirectTargetHits.get();
  }

  void pauseBetweenContentChunks(Duration gap) {
    this.contentChunkGap = gap;
  }

  void redirectContent() {
    this.redirectContent = true;
  }

  void failTokenRequests() {
    this.tokenStatus = 401;
  }

  @Override
  public void close() {
    server.stop(0);
  }

  private void token(HttpExchange exchange) throws IOException {
    tokenRequests.incrementAndGet();
    respond(exchange, tokenStatus, tokenStatus == 200 ? TOKEN : "{\"error\":\"invalid_client\"}");
  }

  private void document(HttpExchange exchange) throws IOException {
    authorizations.add(String.valueOf(exchange.getRequestHeaders().getFirst("Authorization")));
    customHeaders.add(String.valueOf(exchange.getRequestHeaders().getFirst("X-Custom")));
    sleep(documentDelay);
    respond(exchange, 200, DOCUMENT);
  }

  // Sends CONTENT_CHUNKS chunks, pausing before each one, so the whole body takes CONTENT_CHUNKS
  // gaps while bytes keep flowing.
  private void content(HttpExchange exchange) throws IOException {
    authorizations.add(String.valueOf(exchange.getRequestHeaders().getFirst("Authorization")));
    if (redirectContent) {
      exchange.getResponseHeaders().add("Location", url() + "/elsewhere");
      exchange.sendResponseHeaders(302, -1);
      exchange.close();
      return;
    }
    exchange.sendResponseHeaders(200, 0);
    try (var out = exchange.getResponseBody()) {
      for (int chunk = 0; chunk < CONTENT_CHUNKS; chunk++) {
        sleep(contentChunkGap);
        out.write(new byte[CONTENT_CHUNK_SIZE]);
        out.flush();
      }
    }
  }

  private void elsewhere(HttpExchange exchange) throws IOException {
    redirectTargetHits.incrementAndGet();
    respond(exchange, 200, "redirected");
  }

  private static void respond(HttpExchange exchange, int status, String body) throws IOException {
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().add("Content-Type", "application/json");
    exchange.sendResponseHeaders(status, bytes.length);
    try (var out = exchange.getResponseBody()) {
      out.write(bytes);
    }
  }

  private static void sleep(Duration delay) {
    try {
      Thread.sleep(delay);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
