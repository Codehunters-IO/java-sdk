package io.codehunters.measure;

class TestServiceImpl implements TestService {

  @Override
  @Measured(
      name = "service.test",
      tags = {"endpoint", "doWork", "kind", "test"})
  public String doWork() {
    // Simulate some small work
    return "ok";
  }

  @Override
  @Measured(
      name = "service.expr",
      expressions = {"user=#userId"})
  public void processUser(String userId) {
    // no-op
  }

  @Override
  @Measured(name = "service.long", longTask = true)
  public void longOp() {
    try {
      Thread.sleep(5);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  @Override
  @Measured(name = "service.error")
  public void willFail() {
    throw new RuntimeException("boom");
  }

  @Override
  @Measured(
      name = "service.counted",
      count = true,
      expressions = {"input=#input"})
  public String countedWork(String input) {
    return "done-" + input;
  }

  @Override
  @Measured(name = "service.conditional", count = true, countCondition = "#result == 'OK'")
  public String conditionalCount(String status) {
    return status;
  }
}
