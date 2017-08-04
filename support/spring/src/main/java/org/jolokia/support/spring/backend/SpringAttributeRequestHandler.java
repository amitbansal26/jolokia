package org.jolokia.support.spring.backend;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import javax.management.AttributeNotFoundException;
import javax.management.ObjectName;

import org.jolokia.server.core.request.JolokiaObjectNameRequest;
import org.jolokia.server.core.service.api.JolokiaContext;
import org.jolokia.server.core.util.RequestType;
import org.springframework.context.ApplicationContext;
import org.springframework.util.ReflectionUtils;

public abstract class SpringAttributeRequestHandler<T extends JolokiaObjectNameRequest> extends SpringCommandHandler<T> {

    protected SpringAttributeRequestHandler(ApplicationContext pAppContext, JolokiaContext pContext,
            RequestType pType) {
        super(pAppContext, pContext, pType);
    }

    protected Object readAttribute(ObjectName oName, String beanName, Object bean, String attribute)
            throws AttributeNotFoundException {
                // Try get method first
                Class<?> clazz = bean.getClass();
                Method getter = ReflectionUtils.findMethod(clazz,
                        "get" + attribute.substring(0, 1).toUpperCase() + attribute.substring(1));
                if (getter != null && Modifier.isPublic(getter.getModifiers())) {
                    return ReflectionUtils.invokeMethod(getter, bean);
                }
            
                // Next: Direct field access
                Field field = ReflectionUtils.findField(clazz, attribute);
                if (field != null) {
                    boolean isAccessible = field.isAccessible();
                    field.setAccessible(true);
                    try {
                        return ReflectionUtils.getField(field, bean);
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
