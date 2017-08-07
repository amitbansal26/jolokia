package org.jolokia.support.spring.backend;

import org.jolokia.server.core.request.JolokiaWriteRequest;
import org.jolokia.server.core.service.api.JolokiaContext;
import org.jolokia.server.core.service.serializer.Serializer;
import org.jolokia.server.core.util.RequestType;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.context.ApplicationContext;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import javax.management.AttributeNotFoundException;
import javax.management.InstanceNotFoundException;
import javax.management.ObjectName;

/**
 * Created by marska on 03.08.2017.
 */
public class SpringWriteHandler extends SpringAttributeRequestHandler<JolokiaWriteRequest> {

    protected SpringWriteHandler(ApplicationContext pAppContext, JolokiaContext pContext) {
        super(pAppContext, pContext, RequestType.WRITE);
    }

    protected void checkForRestriction(JolokiaWriteRequest pRequest) {
        if(!this.getJolokiaContext().isAttributeWriteAllowed(pRequest.getObjectName(), pRequest.getAttributeName())) {
            throw new SecurityException("Writing attribute " + pRequest.getAttributeName() + " forbidden for Spring bean " + pRequest.getObjectNameAsString());
        }
    }

    public Object handleRequest(JolokiaWriteRequest pJmxReq, Object pPreviousResult)
            throws InstanceNotFoundException, AttributeNotFoundException {
        checkForRestriction(pJmxReq);
        final String beanName = findBeanName(pJmxReq.getObjectName());
        try {
            final Object bean = getApplicationContext().getBean(beanName);
            final Object oldValue = readAttribute(pJmxReq.getObjectName(), beanName, bean, pJmxReq.getAttributeName(), pJmxReq);
            writeAttribute(pJmxReq.getObjectName(), beanName, bean, pJmxReq.getAttributeName(), pJmxReq.getValue());
            return oldValue;
        } catch (NoSuchBeanDefinitionException exp) {
            throw (InstanceNotFoundException) new InstanceNotFoundException(
                    "No bean with name " + beanName + " found in application context").initCause(exp);
        }
    }

    private Object adaptValueToRequiredType(final Object value, final Class<?> requiredType) {
        return this.getJolokiaContext().getMandatoryService(Serializer.class).deserialize(requiredType.getName(),
                value);
    }

    private void writeAttribute(ObjectName oName, String beanName, Object bean, String attribute, Object value)
            throws AttributeNotFoundException {

        Class<? extends Object> clazz = bean.getClass();
        // Try get method first
        Method setter = ReflectionUtils.findMethod(clazz,
                "set" + attribute.substring(0, 1).toUpperCase() + attribute.substring(1), (Class<?>[]) null);
        if (setter != null && Modifier.isPublic(setter.getModifiers())
                && setter.getGenericParameterTypes().length == 1) {
            ReflectionUtils.invokeMethod(setter, bean, adaptValueToRequiredType(value, setter.getParameterTypes()[0]));
            return;
        }

        // Next: Direct field access
        Field field = ReflectionUtils.findField(clazz, attribute);
        if (field != null) {
            boolean isAccessible = field.isAccessible();
            field.setAccessible(true);
            try {
                ReflectionUtils.setField(field, bean, adaptValueToRequiredType(value, field.getType()));
                return;
            } finally {
                field.setAccessible(isAccessible);
            }
        }
        throw new AttributeNotFoundException("No attribute " + attribute + " found on bean " + beanName + "(class "
                + clazz + ") while processing " + oName);
    }

}
