package org.jolokia.support.spring.backend;

import java.util.List;

import javax.management.AttributeNotFoundException;
import javax.management.InstanceNotFoundException;
import javax.management.ObjectName;

import org.jolokia.server.core.request.JolokiaReadRequest;
import org.jolokia.server.core.service.api.JolokiaContext;
import org.jolokia.server.core.util.RequestType;
import org.json.simple.JSONObject;
import org.springframework.aop.framework.Advised;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.context.ApplicationContext;

/**
 * @author roland
 * @since 02.12.13
 */
public class SpringReadHandler extends SpringAttributeRequestHandler<JolokiaReadRequest> {

    protected SpringReadHandler(ApplicationContext pAppContext, JolokiaContext pContext) {
        super(pAppContext, pContext, RequestType.READ);
    }

    @Override
    public Object handleRequest(JolokiaReadRequest pJmxReq, Object pPreviousResult)
            throws InstanceNotFoundException, AttributeNotFoundException {
        final ObjectName oName = pJmxReq.getObjectName();
        final String beanName = findBeanName(oName);

        final ApplicationContext ctx = getApplicationContext();
        try {
            final Object bean = ctx.getBean(beanName);
            final String attribute = pJmxReq.getAttributeName();
            if (attribute == null) {
                return readAllAttributes(pJmxReq, oName, beanName, ctx, bean);
            } else {
                return readAttribute(oName, beanName, bean, attribute, pJmxReq);
            }
        } catch (NoSuchBeanDefinitionException exp) {
            throw (InstanceNotFoundException) new InstanceNotFoundException(
                    "No bean with name " + beanName + " found in application context").initCause(exp);
        }
    }

    @SuppressWarnings("unchecked")
    private Object readAllAttributes(JolokiaReadRequest pJmxReq, final ObjectName oName, final String beanName, final ApplicationContext ctx,
            Object bean) {
            Class<?> clazz = bean.getClass();
            List<String> attributeNames = pJmxReq.getAttributeNames();
            if(bean instanceof Advised) {
                try {
                    bean = ((Advised)bean).getTargetSource().getTarget();
                    clazz = bean.getClass();
                } catch (Exception ignore) {
                }
            }
            if(attributeNames == null || attributeNames.isEmpty()) {
                attributeNames=introspectAttributeNamesFromClass(clazz);
            }
            final JSONObject result = new JSONObject();
            for (String attributeName : attributeNames) {
                try {
                    result.put(attributeName,  readAttribute(oName, beanName, bean, attributeName, pJmxReq));
                } catch (AttributeNotFoundException ignore) {
                }
            }
            return result;
    }


}
