package org.jolokia.support.spring.backend;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map.Entry;

import javax.management.AttributeNotFoundException;
import javax.management.InstanceNotFoundException;
import javax.management.ObjectName;

import org.jolokia.server.core.request.JolokiaReadRequest;
import org.jolokia.server.core.service.api.JolokiaContext;
import org.jolokia.server.core.util.RequestType;
import org.json.simple.JSONObject;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.context.ApplicationContext;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.ReflectionUtils.FieldCallback;
import org.springframework.util.ReflectionUtils.FieldFilter;

/**
 * @author roland
 * @since 02.12.13
 */
public class SpringReadHandler extends SpringCommandHandler<JolokiaReadRequest> {

    protected SpringReadHandler(ApplicationContext pAppContext, JolokiaContext pContext) {
        super(pAppContext, pContext, RequestType.READ);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Object handleRequest(JolokiaReadRequest pJmxReq, Object pPreviousResult) throws InstanceNotFoundException, AttributeNotFoundException {
        final ObjectName oName = pJmxReq.getObjectName();
        final String beanName = findBeanName(oName);
        if (beanName == null) {
            throw new IllegalArgumentException("No bean name given with property 'name' when requesting " + oName);
        }

        final ApplicationContext ctx = getApplicationContext();
        try {
            final Object bean = ctx.getBean(beanName);
            final Class<?> clazz = bean.getClass();
            final String attribute = pJmxReq.getAttributeName();
            final JSONObject result = new JSONObject();
            if (attribute == null) {
                ReflectionUtils.doWithFields(clazz, new FieldCallback() {
                    
                    public void doWith(Field field) throws IllegalArgumentException, IllegalAccessException {
                        try {
                            String attributeName = field.getName();
                            final Object value = readAttribute(oName, beanName, bean, clazz, attributeName);
                            if(isLiteralValue(value)) {
                                result.put(attributeName, value);
                            } else { //map to bean by name
                                for (Entry<String, ? extends Object> candidate : ctx.getBeansOfType(value.getClass()).entrySet()) {
                                    if(candidate.getValue() == value) {
                                        result.put(attributeName, new HashMap<String, String>(Collections.singletonMap("beanref", candidate.getKey())));
                                        break;
                                    }
                                }
                            }
                        } catch(AttributeNotFoundException ignore) {
                        }         
                    }
                }, new FieldFilter() {       
                    public boolean matches(Field field) {
                        return !Modifier.isStatic(field.getModifiers());
                    }
                });
                return result;
            } else {
                return readAttribute(oName, beanName, bean, clazz, attribute);
            }
        } catch (NoSuchBeanDefinitionException exp) {
            throw (InstanceNotFoundException)
                    new InstanceNotFoundException("No bean with name " + beanName + " found in application context").initCause(exp);
        }
    }

    private String findBeanName(final ObjectName oName) {
        String beanName = oName.getKeyProperty("name");
        if (beanName == null) {
            beanName = oName.getKeyProperty("id");
        }
        return beanName;
    }

	private boolean isLiteralValue(Object value) {
		return value == null || value instanceof Boolean || value instanceof String || value instanceof Number;
	}

	private Object readAttribute(ObjectName oName, String beanName, Object bean, Class<?> clazz, String attribute)
			throws AttributeNotFoundException {
		// Try get method first
		Method getter = ReflectionUtils.findMethod(
		    clazz, "get" + attribute.substring(0, 1).toUpperCase() + attribute.substring(1));
		if (getter != null && Modifier.isPublic(getter.getModifiers())) {
		    return ReflectionUtils.invokeMethod(getter,bean);
		}

		// Next: Direct field access
		Field field = ReflectionUtils.findField(clazz,attribute);
		if (field != null) {
		    boolean isAccessible = field.isAccessible();
		    field.setAccessible(true);
		    try {
		        return ReflectionUtils.getField(field,bean);
		    } finally {
		        field.setAccessible(isAccessible);
		    }
		}
		throw new AttributeNotFoundException("No attribute " + attribute +
		                                     " found on bean " + beanName + "(class " + clazz + ") while processing " + oName);
	}
}
