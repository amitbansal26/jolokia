package org.jolokia.support.spring.backend;

import org.json.simple.JSONObject;

/**
 * I am able to represent another bean within the context 
 * that is injected to one bean.
 */
public class SpringBeanReference extends JSONObject {
    private static final long serialVersionUID = 1638198055584924546L;
    
    @SuppressWarnings("unchecked")
    public SpringBeanReference(final String domain, final String beanref) {
        put("domain", domain);
        put("beanref", beanref);
    }
}
