package org.jolokia.support.spring.backend;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.management.AttributeNotFoundException;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

import org.jolokia.server.core.request.JolokiaObjectNameRequest;
import org.jolokia.server.core.service.api.JolokiaContext;
import org.jolokia.server.core.util.RequestType;
import org.springframework.context.ApplicationContext;
import org.springframework.util.ReflectionUtils;

public abstract class SpringAttributeRequestHandler<T extends JolokiaObjectNameRequest>
        extends SpringCommandHandler<T> {

    protected SpringAttributeRequestHandler(ApplicationContext pAppContext, JolokiaContext pContext,
            RequestType pType) {
        super(pAppContext, pContext, pType);
    }

    /**
     * Raw JSON serialization of Spring beans may be problematic, therefore attempt to convert
     * beans returned from this backend into more harmless Java objects
     * @param value the raw mbean
     * @param pJmxReq the original request
     * @return serialization safe value
     */
    protected Object serializationSafeRepresentation(final Object value, JolokiaObjectNameRequest pJmxReq) {
        if (isLiteralValue(value)) {
            return value;
        }
        for (Entry<String, ? extends Object> candidate : getApplicationContext().getBeansOfType(value.getClass()).entrySet()) {
            if (candidate.getValue() == value) {
                try {
                    return new SpringBeanReference(new ObjectName(pJmxReq.getObjectName().getDomain(), "name", candidate.getKey()));
                } catch (MalformedObjectNameException ignore) {//should never fail
                }
            }
        }
        if (value instanceof Map<?, ?>) {
            final Map<Object, Object> mapCopy = new LinkedHashMap<Object, Object>(((Map<?,?>) value).size());
            for (Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                mapCopy.put(serializationSafeRepresentation(entry.getKey(), pJmxReq), serializationSafeRepresentation(entry.getValue(), pJmxReq));
            }
            return mapCopy;
        } else if (value instanceof Iterable<?>) {
            final List<Object> iterableCopy=new LinkedList<Object>();
            for(final Object entry: (Iterable<?>)value) {
                iterableCopy.add(serializationSafeRepresentation(entry, pJmxReq));
            }
            return iterableCopy;
        }
        return String.valueOf(value);
    }

    protected Object readAttribute(ObjectName oName, String beanName, Object bean, String attribute, JolokiaObjectNameRequest pJmxRequest)
            throws AttributeNotFoundException {
        // Try get method first
        Class<?> clazz = bean.getClass();
        Method getter = ReflectionUtils.findMethod(clazz,
                "get" + attribute.substring(0, 1).toUpperCase() + attribute.substring(1));
        if (getter != null && Modifier.isPublic(getter.getModifiers()) && Modifier.isPublic(getter.getDeclaringClass().getModifiers())) {
            return serializationSafeRepresentation(ReflectionUtils.invokeMethod(getter, bean), pJmxRequest);
        }

        // Next: Direct field access
        Field field = ReflectionUtils.findField(clazz, attribute);
        if (field != null) {
            boolean isAccessible = field.isAccessible();
            field.setAccessible(true);
            try {
                return serializationSafeRepresentation(ReflectionUtils.getField(field, bean), pJmxRequest);
            } finally {
                field.setAccessible(isAccessible);
            }
        }
        throw new AttributeNotFoundException("No attribute " + attribute + " found on bean " + beanName + "(class "
                + clazz + ") while processing " + oName);
    }

    protected String findBeanName(final ObjectName oName) {
        String beanName = oName.getKeyProperty("name");
        if (beanName == null) {
            beanName = oName.getKeyProperty("id");
        }
        if (beanName == null) {
            throw new IllegalArgumentException("No bean name given with property 'name' when requesting " + oName);
        }
        return beanName;
    }

}
