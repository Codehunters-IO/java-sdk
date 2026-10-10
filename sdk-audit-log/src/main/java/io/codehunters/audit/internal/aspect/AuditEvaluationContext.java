package io.codehunters.audit.internal.aspect;

import java.lang.reflect.Method;
import java.util.List;
import java.util.function.Supplier;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.BeanResolver;
import org.springframework.expression.ConstructorResolver;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.IndexAccessor;
import org.springframework.expression.MethodResolver;
import org.springframework.expression.OperatorOverloader;
import org.springframework.expression.PropertyAccessor;
import org.springframework.expression.TypeComparator;
import org.springframework.expression.TypeConverter;
import org.springframework.expression.TypeLocator;
import org.springframework.expression.TypedValue;
import org.springframework.expression.spel.support.SimpleEvaluationContext;

/**
 * The sandbox one audited call is evaluated in: a read-only {@link SimpleEvaluationContext}
 * (property reads and indexes only; no method calls, types, constructors, bean references or
 * assignments) holding the arguments by name and position and the return value as {@code #result},
 * which wins over a parameter named {@code result}. It also carries the method's label for warnings
 * and whether the call failed.
 */
final class AuditEvaluationContext implements EvaluationContext {

  static final String RESULT = "result";

  private final SimpleEvaluationContext delegate =
      SimpleEvaluationContext.forReadOnlyDataBinding().build();
  private final String label;
  private final boolean failed;

  AuditEvaluationContext(CapturedArguments call, ParameterNameDiscoverer names) {
    this.label = MethodLabels.of(call.method());
    this.failed = call.failed();
    bindArguments(call.args(), names.getParameterNames(call.method()));
    delegate.setVariable(RESULT, call.result());
  }

  private void bindArguments(Object[] args, String[] names) {
    for (int i = 0; i < args.length; i++) {
      if (names != null && i < names.length) {
        delegate.setVariable(names[i], args[i]);
      }
      delegate.setVariable("p" + i, args[i]);
      delegate.setVariable("a" + i, args[i]);
      delegate.setVariable("arg" + i, args[i]);
    }
  }

  String label() {
    return label;
  }

  boolean failed() {
    return failed;
  }

  @Override
  public TypedValue getRootObject() {
    return delegate.getRootObject();
  }

  @Override
  public List<PropertyAccessor> getPropertyAccessors() {
    return delegate.getPropertyAccessors();
  }

  @Override
  public List<IndexAccessor> getIndexAccessors() {
    return delegate.getIndexAccessors();
  }

  @Override
  public List<ConstructorResolver> getConstructorResolvers() {
    return delegate.getConstructorResolvers();
  }

  @Override
  public List<MethodResolver> getMethodResolvers() {
    return delegate.getMethodResolvers();
  }

  @Override
  public BeanResolver getBeanResolver() {
    return delegate.getBeanResolver();
  }

  @Override
  public TypeLocator getTypeLocator() {
    return delegate.getTypeLocator();
  }

  @Override
  public TypeConverter getTypeConverter() {
    return delegate.getTypeConverter();
  }

  @Override
  public TypeComparator getTypeComparator() {
    return delegate.getTypeComparator();
  }

  @Override
  public OperatorOverloader getOperatorOverloader() {
    return delegate.getOperatorOverloader();
  }

  @Override
  public TypedValue assignVariable(String name, Supplier<TypedValue> valueSupplier) {
    return delegate.assignVariable(name, valueSupplier);
  }

  @Override
  public void setVariable(String name, Object value) {
    delegate.setVariable(name, value);
  }

  @Override
  public Object lookupVariable(String name) {
    return delegate.lookupVariable(name);
  }

  @Override
  public boolean isAssignmentEnabled() {
    return delegate.isAssignmentEnabled();
  }

  @Override
  public boolean isCompilationSupported() {
    return delegate.isCompilationSupported();
  }

  /**
   * One audited call.
   *
   * @param method the audited method
   * @param args its arguments
   * @param result its return value, or {@code null}
   * @param failed whether the call failed
   */
  record CapturedArguments(Method method, Object[] args, Object result, boolean failed) {}
}
