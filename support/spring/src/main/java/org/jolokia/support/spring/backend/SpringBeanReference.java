package org.jolokia.support.spring.backend;

import javax.management.ObjectName;

/**
 * I am able to represent another bean within the context 
 * that is injected to one bean.
 */
public class SpringBeanReference {
    private final ObjectName beanref;
    
    public SpringBeanReference(final ObjectName beanref) {
        this.beanref=beanref;
    }

    public ObjectName getBeanref() {
        return beanref;
    }
}
