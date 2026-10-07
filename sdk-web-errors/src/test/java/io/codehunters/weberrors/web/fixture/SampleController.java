package io.codehunters.weberrors.web.fixture;

import io.codehunters.weberrors.ApiException;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Valid;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Size;
import java.io.IOException;
import java.lang.reflect.UndeclaredThrowableException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;

/** Endpoints that fail in every way the handler must map. */
@RestController
public class SampleController {

  /** The secret the failing endpoints carry in their exception messages. */
  public static final String SECRET = "card 4111111111111111";

  /** The message of the cause behind a client error, meant for the server log only. */
  public static final String CAUSE = "ledger row locked";

  private final Validator validator;

  private final SampleService service;

  /**
   * Creates the controller.
   *
   * @param validator the bean validator, used to raise constraint violations like an entity would
   * @param service the validated service
   */
  public SampleController(Validator validator, SampleService service) {
    this.validator = validator;
    this.service = service;
  }

  @GetMapping("/orders/{id}")
  String order(@PathVariable String id) {
    throw new ApiException(SampleErrorCode.ORDER_NOT_FOUND, List.of(id));
  }

  @GetMapping("/orders/{id}/payment")
  String payment(@PathVariable String id) {
    throw new ApiException(SampleErrorCode.ORDER_ALREADY_PAID);
  }

  @PostMapping("/orders")
  String create(@Valid @RequestBody CreateOrder order) {
    return "created";
  }

  @GetMapping("/search")
  String search(@RequestParam @Size(max = 3) String q) {
    return q;
  }

  @GetMapping("/service")
  String service() {
    return service.create(new CreateOrder("", 1));
  }

  @GetMapping("/entity")
  String entity() {
    throw new ConstraintViolationException(validator.validate(new CreateOrder("", 1)));
  }

  @GetMapping("/codes")
  @Size(max = 1)
  List<String> codes() {
    return List.of("first", "second");
  }

  @GetMapping("/filter")
  String filter(OrderFilter filter) {
    return "filtered";
  }

  @PostMapping("/coupons")
  String coupon(@Valid @RequestBody Coupon coupon) {
    return "redeemed";
  }

  @GetMapping("/teapot-orders/{id}")
  String teapotOrder(@PathVariable String id) {
    throw new ApiException(SampleErrorCode.TEAPOT_ORDER, List.of(id));
  }

  @GetMapping("/paid-with-cause")
  String paidWithCause() {
    throw new ApiException(
        SampleErrorCode.ORDER_ALREADY_PAID, List.of(), new IllegalStateException(CAUSE));
  }

  @GetMapping("/too-large")
  String tooLarge() {
    throw new MaxUploadSizeExceededException(10);
  }

  @GetMapping("/slow")
  String slow() {
    throw new AsyncRequestTimeoutException();
  }

  @GetMapping("/committed")
  void committed(HttpServletResponse response) throws IOException {
    response.getWriter().write("partial");
    response.flushBuffer();
    throw new IllegalStateException(SECRET);
  }

  @GetMapping("/disconnected")
  String disconnected() throws IOException {
    throw new IOException("Broken pipe");
  }

  @GetMapping("/wrapped-denied")
  String wrappedDenied() {
    throw new UndeclaredThrowableException(new AccessDeniedException("denied"));
  }

  @GetMapping("/quantities/{quantity}")
  String quantity(@PathVariable int quantity) {
    return "ok";
  }

  @GetMapping("/boom")
  String boom() {
    throw new IllegalStateException(SECRET);
  }

  @GetMapping("/server-side")
  String serverSide() {
    throw new ApiException(
        SampleErrorCode.SERVER_SIDE_FAILURE, List.of(), new IllegalStateException(SECRET));
  }

  @GetMapping("/out-of-range")
  String outOfRange() {
    throw new ApiException(SampleErrorCode.OUT_OF_RANGE_STATUS);
  }

  @GetMapping("/unknown-status")
  String unknownStatus() {
    throw new ApiException(SampleErrorCode.UNKNOWN_STATUS);
  }

  @GetMapping("/teapot")
  String teapot() {
    throw new TeapotException();
  }

  @GetMapping("/reasoned-teapot")
  String reasonedTeapot() {
    throw new ReasonedTeapotException();
  }

  @GetMapping("/gone")
  String gone() {
    throw new ResponseStatusException(HttpStatus.GONE, "Order archived");
  }

  @GetMapping("/gone-silently")
  String goneSilently() {
    throw new ResponseStatusException(HttpStatus.GONE);
  }

  @GetMapping("/denied")
  String denied() {
    throw new AccessDeniedException("denied");
  }
}
