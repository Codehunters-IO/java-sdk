package io.codehunters.weberrors.web.fixture;

import io.codehunters.weberrors.ApiException;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Valid;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Endpoints that fail in every way the handler must map. */
@RestController
public class SampleController {

  /** The secret the failing endpoints carry in their exception messages. */
  public static final String SECRET = "card 4111111111111111";

  private final Validator validator;

  /**
   * Creates the controller.
   *
   * @param validator the bean validator, used to raise constraint violations like a service would
   */
  public SampleController(Validator validator) {
    this.validator = validator;
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
    throw new ConstraintViolationException(validator.validate(new CreateOrder("", 1)));
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
    throw new ApiException(SampleErrorCode.SERVER_SIDE_FAILURE, List.of(), new IllegalStateException(SECRET));
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
