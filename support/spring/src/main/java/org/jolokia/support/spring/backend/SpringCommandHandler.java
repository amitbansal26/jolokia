package org.jolokia.support.spring.backend;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.LinkedList;
import java.util.List;

import javax.management.AttributeNotFoundException;
import javax.management.InstanceNotFoundException;

import org.jolokia.server.core.request.JolokiaRequest;
import org.jolokia.server.core.service.api.JolokiaContext;
import org.jolokia.server.core.util.RequestType;
import org.springframework.context.ApplicationContext;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.ReflectionUtils.FieldCallback;
import org.springframework.util.ReflectionUtils.FieldFilter;

/**
 * Base class for Jolokia commands accessing the spring container
 *
 * @author roland
 * @since 02.12.13
 */
public abstract class SpringCommandHandler<T extends JolokiaRequest> {

    private static Boolean isSetAccessibleAllowed = null;

    // Spring application context
    private ApplicationContext applicationContext;

    // The jolokia context used
    private JolokiaContext context;

    protected SpringCommandHandler(ApplicationContext pAppContext, JolokiaContext pContext, RequestType pType) {
        this.context = pContext;
        this.type = pType;
        this.applicationContext = pAppContext;
    }

    // Request type of this command
    private RequestType type;

    public RequestType getType() {
        return type;
    }

    public JolokiaContext getJolokiaContext() {
        return context;
    }

    public ApplicationContext getApplicationContext() {
        return applicationContext;
    }

    public abstract Object handleRequest(T pJmxReq, Object pPreviousResult)
            throws InstanceNotFoundException, AttributeNotFoundException;

    protected boolean isLiteralValue(Object value) {
        return value == null || value instanceof Boolean || value instanceof String || value instanceof Number
                ;
    }

    protected boolean isLiteralType(Class<?> type) {
        return type != null && (type.isAssignableFrom(Number.class) || type.isAssignableFrom(Boolean.class)
                || type.isAssignableFrom(String.class) || type.isAssignableFrom(Class.class));
    }

    protected List<String> introspectAttributeNamesFromClass(Class<?> clazz) {
        final List<String> attributeNames = new LinkedList<String>();
        attributeNames.add("class");//manually add class, as it is useful
        ReflectionUtils.doWithFields(clazz, new FieldCallback() {
            public void doWith(Field field) throws IllegalArgumentException, IllegalAccessException {
                attributeNames.add(field.getName());

            }
        }, new FieldFilter() {

            public boolean matches(Field field) {
                return !Modifier.isStatic(field.getModifiers());
            }

        });
        return attributeNames;
    }

    protected boolean probeForAccessiblePermission(final Class<?> klass, final String name) {
        if(isSetAccessibleAllowed == null) {
            Field field;
            try {
                field = klass.getField(name);
            } catch (Exception e1) {
                return false;
            } 
            boolean wasAccessible = field.isAccessible();
            try {
            field.setAccessible(true);
            field.setAccessible(wasAccessible);
            isSetAccessibleAllowed = true;
            } catch(Exception e) {
                isSetAccessibleAllowed = false;
            }
        }return isSetAccessibleAllowed;
}

}
