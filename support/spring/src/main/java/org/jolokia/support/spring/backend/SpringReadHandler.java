package org.jolokia.support.spring.backend;

import org.jolokia.server.core.request.JolokiaReadRequest;
import org.jolokia.server.core.service.api.JolokiaContext;
import org.jolokia.server.core.util.RequestType;
import org.json.simple.JSONObject;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.context.ApplicationContext;

import javax.management.AttributeNotFoundException;
import javax.management.InstanceNotFoundException;
import javax.management.ObjectName;
import java.beans.PropertyDescriptor;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;

/**
 * @author roland
 * @since 02.12.13
 */
public class SpringReadHandler extends SpringAttributeRequestHandler<JolokiaReadRequest> {

    protected SpringReadHandler(ApplicationContext pAppContext, JolokiaContext pContext) {
        super(pAppContext, pContext, RequestType.READ);
    }

    /**
     * @return A representation of the value suitable for external representation.
     *          Specifically if it is another spring bean, refer to it by reference rather than directly
     *          because of the possibility of circular / very deep structures and various Spring internal proxies
     */
    private Object externalRepresentation(final ApplicationContext ctx, final Object value, JolokiaReadRequest pJmxReq) {
        // map to bean by name
        for (Entry<String, ? extends Object> candidate : ctx.getBeansOfType(value.getClass()).entrySet()) {
            if (candidate.getValue() == value) {
                return new SpringBeanReference(pJmxReq.getObjectName().getDomain(), candidate.getKey());
            }
        }
        return null;
    }

    @Override
    public Object handleRequest(JolokiaReadRequest pJmxReq, Object pPreviousResult)
            throws InstanceNotFoundException, AttributeNotFoundException {
        final ObjectName oName = pJmxReq.getObjectName();
        final String beanName = findBeanName(oName);

        final ApplicationContext ctx = getApplicationContext();
        try {
            final Object bean = ctx.getBean(beanName);
            final Class<?> clazz = bean.getClass();
            final String attribute = pJmxReq.getAttributeName();
            if (attribute == null) {
                return readAllAttributes(pJmxReq, oName, beanName, ctx, bean);
            } else {
                return readAttribute(oName, beanName, bean, attribute);
            }
        } catch (NoSuchBeanDefinitionException exp) {
            throw (InstanceNotFoundException) new InstanceNotFoundException(
                    "No bean with name " + beanName + " found in application context").initCause(exp);
        }
    }

    @SuppressWarnings("unchecked")
    private Object readAllAttributes(JolokiaReadRequest pJmxReq, final ObjectName oName, final String beanName, final ApplicationContext ctx,
            final Object bean) {
            final Class<?> clazz = bean.getClass();
            List<String> attributeNames = pJmxReq.getAttributeNames();
            if(attributeNames == null || attributeNames.isEmpty()) {
                attributeNames=introspectAttributeNamesFromClass(clazz);
            }
            final JSONObject result = new JSONObject();
            for (String attributeName : attributeNames) {
                try {
                    final Object value = readAttribute(oName, beanName, bean, attributeName);
                    if (isLiteralValue(value)) {
                        result.put(attributeName, value);
                    } else {
                        final Object external = externalRepresentation(ctx, value, pJmxReq);
                        if (external != null) {
                            result.put(attributeName, external);
                        }
                    }
                } catch (AttributeNotFoundException ignore) {
                }
            }
            return result;
    }

    private List<String> introspectAttributeNamesFromClass(Class<?> clazz) {
        List<String> attributeNames=new LinkedList<String>();
        // use bean introspector in the same way as for list requests
        for (PropertyDescriptor descriptor : BeanUtils.getPropertyDescriptors(clazz)) {
            attributeNames.add(descriptor.getName());
        }
        return attributeNames;
    }
}
