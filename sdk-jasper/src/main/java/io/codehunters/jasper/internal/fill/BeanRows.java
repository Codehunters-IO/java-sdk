package io.codehunters.jasper.internal.fill;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JRField;
import net.sf.jasperreports.engine.data.JRAbstractBeanDataSource;

/**
 * Rows read through JavaBean getters, as JasperReports does, and through record components, which
 * its bean source cannot read. A dotted field ({@code customer.name}) walks either kind.
 */
public final class BeanRows extends JRAbstractBeanDataSource {

  private final List<?> rows;
  private final Map<Class<?>, Map<String, Method>> accessors = new HashMap<>();
  private int index = -1;

  public BeanRows(List<?> rows) {
    super(true);
    this.rows = rows;
  }

  @Override
  public boolean next() {
    index++;
    return index < rows.size();
  }

  @Override
  public void moveFirst() {
    index = -1;
  }

  @Override
  public Object getFieldValue(JRField field) throws JRException {
    Object row = rows.get(index);
    String path = getPropertyName(field);
    if (isCurrentBeanMapping(path)) {
      return row;
    }
    return read(row, path);
  }

  private Object read(Object bean, String path) throws JRException {
    Object value = bean;
    for (String name : path.split("\\.")) {
      if (value == null) {
        return null;
      }
      value = property(value, name);
    }
    return value;
  }

  private Object property(Object bean, String name) throws JRException {
    if (!(bean instanceof Record)) {
      return getBeanProperty(bean, name);
    }
    Method accessor = component(bean.getClass(), name);
    try {
      return accessor.invoke(bean);
    } catch (IllegalAccessException | InvocationTargetException e) {
      throw new JRException("record component " + name + " could not be read", e);
    }
  }

  private Method component(Class<?> type, String name) throws JRException {
    Method accessor = accessors.computeIfAbsent(type, BeanRows::componentsOf).get(name);
    if (accessor == null) {
      throw new JRException(
          "record " + type.getName() + " has no component " + name, (Throwable) null);
    }
    return accessor;
  }

  private static Map<String, Method> componentsOf(Class<?> type) {
    var byName = new HashMap<String, Method>();
    for (RecordComponent component : type.getRecordComponents()) {
      Method accessor = component.getAccessor();
      accessor.trySetAccessible();
      byName.put(component.getName(), accessor);
    }
    return byName;
  }
}
