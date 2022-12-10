/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.servicemix.bean;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.logging.Logger;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.annotation.Resource;
import javax.jbi.JBIException;
import javax.jbi.component.ComponentContext;
import javax.jbi.management.MBeanNames;
import javax.jbi.messaging.*;
import javax.jbi.messaging.MessageExchange.Role;
import javax.jbi.servicedesc.ServiceEndpoint;
import javax.management.MBeanServer;
import javax.naming.InitialContext;
import javax.xml.namespace.QName;

import org.aopalliance.intercept.MethodInvocation;
import org.apache.commons.jexl2.Expression;
import org.apache.commons.jexl2.JexlContext;
import org.apache.commons.jexl2.JexlEngine;
import org.apache.commons.jexl2.MapContext;
import org.apache.servicemix.bean.support.BeanInfo;
import org.apache.servicemix.bean.support.DefaultMethodInvocationStrategy;
import org.apache.servicemix.bean.support.DestinationImpl;
import org.apache.servicemix.bean.support.Holder;
import org.apache.servicemix.bean.support.MethodInvocationStrategy;
import org.apache.servicemix.bean.support.ReflectionUtils;
import org.apache.servicemix.bean.support.Request;
import org.apache.servicemix.common.endpoints.ProviderEndpoint;
import org.apache.servicemix.common.util.MessageUtil;
import org.apache.servicemix.common.util.URIResolver;
import org.apache.servicemix.expression.JAXPStringXPathExpression;
import org.apache.servicemix.expression.PropertyExpression;
import org.apache.servicemix.jbi.listener.MessageExchangeListener;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.w3c.dom.Document;
import org.w3c.dom.DocumentFragment;

/**
 * Represents a bean endpoint which consists of a together with a {@link MethodInvocationStrategy}
 * so that JBI message exchanges can be invoked on the bean.
 *
 * @version $Revision: $
 * @org.apache.xbean.XBean element="endpoint"
 */
public class BeanEndpoint extends ProviderEndpoint implements ApplicationContextAware {
    
    /**
     * Property name for the correlation id that is being set on exchanges by the BeanEndpoint 
     */
    public static final String CORRELATION_ID = BeanEndpoint.class.getName().replaceAll("\\.", "_") + "_correlation";

    private ApplicationContext applicationContext;
    private String beanName;
    private Object bean;
    private BeanInfo beanInfo;
    private Class<?> beanType;
    private String beanClassName;
    private MethodInvocationStrategy methodInvocationStrategy;
    private org.apache.servicemix.expression.Expression correlationExpression;

    private Map<String, Holder> exchanges = new ConcurrentHashMap<String, Holder>();
    private Map<Object, Request> requests = new ConcurrentHashMap<Object, Request>();
    private ThreadLocal<Request> currentRequest = new ThreadLocal<Request>();
    private ServiceEndpoint serviceEndpoint;
    
    public BeanEndpoint() {
    }

    public BeanEndpoint(BeanComponent component, ServiceEndpoint serviceEndpoint) {
        super(component, serviceEndpoint);
        this.applicationContext = component.getApplicationContext();
        this.serviceEndpoint = serviceEndpoint;
    }

    public void start() throws Exception {
        super.start();
        if (serviceEndpoint == null) {
            serviceEndpoint = getContext().getEndpoint(getService(), getEndpoint());
        }
        Object pojo = getBean();
        if (pojo != null) {
            beanType = pojo.getClass();
            injectBean(pojo);
            ReflectionUtils.callLifecycleMethod(pojo, PostConstruct.class);
        } else {
            beanType = createBean().getClass();
        }
        if (getMethodInvocationStrategy() == null) {
            throw new IllegalArgumentException("No 'methodInvocationStrategy' property set");
        }
    }


    public void stop() throws Exception {
        super.stop();
        Object pojo = getBean();
        if (pojo != null) {
            ReflectionUtils.callLifecycleMethod(pojo, PreDestroy.class);
        }
    }


    public ApplicationContext getApplicationContext() {
        return applicationContext;
    }

    /**
     * Set the Spring ApplicationContext where the bean can be found.  Defaults to the context defined in xbean.xml
     *
     * @param applicationContext
     * @throws BeansException
     */
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    public String getBeanName() {
        return beanName;
    }

    /**
     * Set the name of the bean in the application context to be used for handling exchanges
     *
     * @param beanName
     */
    public void setBeanName(String beanName) {
        this.beanName = beanName;
    }

    public Object getBean() {
        return bean;
    }

    /**
     * Set the bean to be used for handling exchanges
     *
     * @param bean
     */
    public void setBean(Object bean) {
        this.bean = bean;
    }

    /**
     * @return the beanType
     */
    public Class<?> getBeanType() {
        return beanType;
    }

    /**
     * Set the bean class to be used for handling exchanges.  A new instance will be created on the fly for every exchange.
     *
     * @param beanType the beanType to set
     */
    public void setBeanType(Class<?> beanType) {
        this.beanType = beanType;
    }

    /**
     * @return the beanClassName
     */
    public String getBeanClassName() {
        return beanClassName;
    }

    /**
     * Set the bean class name to be used for handling exchanges.  A new instance will be created on the fly for every exchange.
     *
     * @param beanClassName the beanClassName to set
     */
    public void setBeanClassName(String beanClassName) {
        this.beanClassName = beanClassName;
    }

    public BeanInfo getBeanInfo() {
        if (beanInfo == null) {
            beanInfo = new BeanInfo(beanType, getMethodInvocationStrategy());
            beanInfo.introspect();
        }
        return beanInfo;
    }

    /**
     * Set a custom bean info object to define the bean to be used for handling exchanges
     *
     * @param beanInfo
     */
    public void setBeanInfo(BeanInfo beanInfo) {
        this.beanInfo = beanInfo;
    }

    public MethodInvocationStrategy getMethodInvocationStrategy() {
        if (methodInvocationStrategy == null) {
            methodInvocationStrategy = createMethodInvocationStrategy();
        }
        return methodInvocationStrategy;
    }

    /**
     * Set a custom invocation strategy to define how the bean is being invoked.  The default implementation takes some additional parameter annotations into account. 
     *
     * @param methodInvocationStrategy the strategy
     */
    public void setMethodInvocationStrategy(MethodInvocationStrategy methodInvocationStrategy) {
        this.methodInvocationStrategy = methodInvocationStrategy;
    }


    @Override
    public void process(MessageExchange exchange) throws Exception {
        if (exchange.getRole() == Role.CONSUMER) {
            onConsumerExchange(exchange);
        } else {
            onProviderExchange(exchange);
        }
    }

    protected void onProviderExchange(MessageExchange exchange) throws Exception {
        Request req = getOrCreateCurrentRequest(exchange);
        currentRequest.set(req);
        try {
            // Find or create the request for this provider exchange
            synchronized (req) {
                // If the bean implements MessageExchangeListener,
                // just call the method
                if (req.getBean() instanceof MessageExchangeListener) {
                    ((MessageExchangeListener) req.getBean()).onMessageExchange(exchange);
                } else {
                    // Exchange is finished
                    if (exchange.getStatus() == ExchangeStatus.DONE) {
                        return;
                    // Exchange has been aborted with an exception
                    } else if (exchange.getStatus() == ExchangeStatus.ERROR) {
                        return;
                    // Fault message
                    } else if (exchange.getFault() != null) {
                        // TODO: find a way to send it back to the bean before setting the DONE status
                        done(exchange);
                    } else {
                        MethodInvocation invocation = getMethodInvocationStrategy().createInvocation(
                                req.getBean(), getBeanInfo(), exchange, this);
                        if (invocation == null) {
                            throw new UnknownMessageExchangeTypeException(exchange, this);
                        }
                        try {
                            invocation.proceed();
                        } catch (InvocationTargetException e) {
                            throw new MethodInvocationFailedException(req.getBean(), invocation, exchange, this, e.getCause());
                        } catch (Exception e) {
                            throw e;
                        } catch (Throwable throwable) {
                            throw new MethodInvocationFailedException(req.getBean(), invocation, exchange, this, throwable);
                        }
                        if (exchange.getStatus() == ExchangeStatus.ERROR) {
                            send(exchange);
                        }
                        if (exchange.getFault() == null && exchange.getMessage("out") == null)  {
                            //TODO: Only send DONE if(onProviderExchange(exchange)) as soon as we find a way to solve
                            //      the TODO in evaluateCallbacks(Request)
                            done(exchange);
                        }
                    }
                }
            }
        } finally {
            checkEndOfRequest(req);
            currentRequest.set(null);
        }
    }

    /*
     * Check if the incoming provider exchange should be marked DONE 
     */
    protected boolean onProviderExchangeDone(MessageExchange exchange) {
        return (exchange instanceof InOnly) ||
               (exchange instanceof RobustInOnly && exchange.getFault() == null) ||
               (exchange instanceof InOptionalOut && exchange.getFault() == null && exchange.getMessage("out") == null);
    }

    protected Request getOrCreateCurrentRequest(MessageExchange exchange) throws ClassNotFoundException, InstantiationException, IllegalAccessException, MessagingException {
        if (currentRequest.get() != null) {
            return currentRequest.get();
        }
        Request req = getRequest(exchange);
        if (req == null) {
            Object pojo = getBean();
            if (pojo == null) {
                pojo = createBean();
                injectBean(pojo);
                ReflectionUtils.callLifecycleMethod(pojo, PostConstruct.class);
            }
            req = new Request(getCorrelation(exchange), pojo, exchange);
            requests.put(req.getCorrelationId(), req);
        }
        return req;
    }
    
    protected Request getRequest(MessageExchange exchange) throws MessagingException {
        Object correlation = getCorrelation(exchange);
        return correlation == null ? null : requests.get(correlation);
    }

    protected void onConsumerExchange(MessageExchange exchange) throws Exception {
        Request req = getOrCreateCurrentRequest(exchange);
        if (req == null) {
            throw new IllegalStateException("Receiving unknown consumer exchange: " + exchange);
        }
        currentRequest.set(req);
        
        // if there's a holder for this exchange, act upon that
        // else invoke the MessageExchangeListener interface
        if (exchanges.containsKey(exchange.getExchangeId())) {
            exchanges.remove(exchange.getExchangeId()).set(exchange);
            evaluateCallbacks(req);
            
            //we should done() the consumer exchange here on behalf of the Destination who sent it
            if (exchange instanceof InOut && ExchangeStatus.ACTIVE.equals(exchange.getStatus())) {
                done(exchange);
            }
        } else if (req.getBean() instanceof MessageExchangeListener) {
            ((MessageExchangeListener) req.getBean()).onMessageExchange(exchange);
        } else {
            throw new IllegalStateException("No known consumer exchange found and bean does not implement MessageExchangeListener");
        }
        checkEndOfRequest(req);
        currentRequest.set(null);
    }

    protected Object getCorrelation(MessageExchange exchange) throws MessagingException {
        return getCorrelationExpression().evaluate(exchange, exchange.getMessage("in"));
    }

    protected Object createBean() throws ClassNotFoundException, InstantiationException, IllegalAccessException {
        if (beanType == null && beanClassName != null) {
            beanType = Class.forName(beanClassName, true, getServiceUnit().getConfigurationClassLoader());
        }
        if (beanName == null && beanType == null) {
            throw new IllegalArgumentException("Property 'bean', 'beanName' or 'beanClassName' has not been set!");
        }
        if (beanName != null && applicationContext == null) {
            throw new IllegalArgumentException("Property 'beanName' specified, but no BeanFactory set!");
        }
        if (beanType != null) {
            return beanType.newInstance();
        } else {
            Object answer = applicationContext.getBean(beanName);
            if (answer == null) {
                throw new NoSuchBeanException(beanName, this);
            }
            return answer;
        }
    }

    protected MethodInvocationStrategy createMethodInvocationStrategy() {
        DefaultMethodInvocationStrategy st = new DefaultMethodInvocationStrategy();
        st.loadDefaultRegistry();
        return st;
    }

    /**
     * A strategy method to allow implementations to perform some custom JBI based injection of the POJO
     *
     * @param target the bean to be injected
     */
    protected void injectBean(final Object target) {
        final PojoContext ctx = new PojoContext();
        final DeliveryChannel ch = ctx.channel;
        // Inject fields
        ReflectionUtils.doWithFields(target.getClass(), new ReflectionUtils.FieldCallback() {
            public void doWith(Field f) throws IllegalArgumentException, IllegalAccessException {
                ExchangeTarget et = f.getAnnotation(ExchangeTarget.class);
                if (et != null) {
                    ReflectionUtils.setField(f, target, new DestinationImpl(et.uri(), BeanEndpoint.this));
                }
                if (f.getAnnotation(Resource.class) != null) {
                    if (ComponentContext.class.isAssignableFrom(f.getType())) {
                        ReflectionUtils.setField(f, target, ctx);
                    } else if (DeliveryChannel.class.isAssignableFrom(f.getType())) {
                        ReflectionUtils.setField(f, target, ch);
                    } else if (ServiceEndpoint.class.isAssignableFrom(f.getType())) {
                        ReflectionUtils.setField(f, target, BeanEndpoint.this.serviceEndpoint);
                    }
                }
            }
        });
    }
    
    protected void evaluateCallbacks(final Request req) {
        final Object obj = req.getBean();
        ReflectionUtils.doWithMethods(obj.getClass(), new ReflectionUtils.MethodCallback() {
            @SuppressWarnings("unchecked")
            public void doWith(Method method) throws IllegalArgumentException, IllegalAccessException {
                if (method.getAnnotation(Callback.class) != null) {
                    try {
                        JexlEngine jexl = new JexlEngine();
                        Expression e = jexl.createExpression(
                                method.getAnnotation(Callback.class).condition());
                        JexlContext jc = new MapContext();
                        jc.set("this", obj);
                        Object r = e.evaluate(jc);
                        if (!(r instanceof Boolean)) {
                            throw new RuntimeException("Expression did not returned a boolean value but: " + r);
                        }
                        Boolean oldVal = req.getCallbacks().get(method);
                        Boolean newVal = (Boolean) r;
                        if ((oldVal == null || !oldVal) && newVal) {
                            req.getCallbacks().put(method, newVal);
                            method.invoke(obj, new Object[0]);
                            // TODO: handle return value and sent it as the answer
                        }
                    } catch (Exception e) {
                        throw new RuntimeException("Unable to invoke callback", e);
                    }
                }
            }
        });
    }

    /**
     * Used by POJOs acting as a consumer
     * @param uri
     * @param message
     * @return
     */
    public Future<NormalizedMessage> send(String uri, NormalizedMessage message) {
        try {
            InOut me = getExchangeFactory().createInOutExchange();
            URIResolver.configureExchange(me, getServiceUnit().getComponent().getComponentContext(), uri);
            MessageUtil.transferTo(message, me, "in");
            final Holder h = new Holder();
            getOrCreateCurrentRequest(me).addExchange(me);
            exchanges.put(me.getExchangeId(), h);
            BeanEndpoint.this.send(me);
            return h;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    
    @Override
    protected void send(MessageExchange me) throws MessagingException {
        checkEndOfRequest(me);
        super.send(me);
    }

    /*
     * Checks if the request has ended with the given MessageExchange.  It will only perform the check on non-ACTIVE exchanges
     */
    private void checkEndOfRequest(MessageExchange me) throws MessagingException {
        if (!ExchangeStatus.ACTIVE.equals(me.getStatus())) {
            Request request = getRequest(me);
            if (request != null) {
                checkEndOfRequest(request);
            }
        }
    }

    /**
     * Checks if the request has ended.  If the request has ended, 
     * <ul>
     * <li>the request object is being removed from the list of pending requests</li> 
     * <li>if the bean was created for that request, it is now being destroyed</li>
     * </ul>
     * 
     * @param req the Request instance to check
     */
    protected void checkEndOfRequest(Request req) {
        if (req.isFinished()) {
            requests.remove(req.getCorrelationId());
            if (req.getBean() != bean) {
                ReflectionUtils.callLifecycleMethod(req.getBean(), PreDestroy.class);
            }
        }
    }

    /**
     * @return the correlationExpression
     */
    public org.apache.servicemix.expression.Expression getCorrelationExpression() {
        if (correlationExpression == null) {
            // Find correlation expression
            Correlation cor = beanType.getAnnotation(Correlation.class);
            if (cor != null) {
                if (cor.property() != null) {
                    correlationExpression = new PropertyExpression(cor.property());
                } else if (cor.xpath() != null) {
                    correlationExpression = new JAXPStringXPathExpression(cor.xpath());
                }
            }
            if (correlationExpression == null) {
                correlationExpression = new org.apache.servicemix.expression.Expression() {
                    public Object evaluate(MessageExchange exchange, NormalizedMessage message) 
                        throws MessagingException {
                        if (exchange.getProperty(CORRELATION_ID) != null) {
                            return exchange.getProperty(CORRELATION_ID);
                        }
                        return exchange.getExchangeId();
                    }
                };
            }
        }
        return correlationExpression;
    }

    /**
     * Set a custom expression to use for correlating exchanges into a single request handled by the same bean instance.
     * The default expression uses a correlation ID set on the exchange properties. 
     *
     * @param correlationExpression the correlationExpression to set
     */
    public void setCorrelationExpression(org.apache.servicemix.expression.Expression correlationExpression) {
        this.correlationExpression = correlationExpression;
    }

    protected class PojoContext implements ComponentContext {

        private DeliveryChannel channel = new PojoChannel();

        public ServiceEndpoint activateEndpoint(QName qName, String s) throws JBIException {
            return getContext().activateEndpoint(qName, s);
        }

        public void deactivateEndpoint(ServiceEndpoint serviceEndpoint) throws JBIException {
            getContext().deactivateEndpoint(serviceEndpoint);
        }

        public void registerExternalEndpoint(ServiceEndpoint serviceEndpoint) throws JBIException {
            getContext().registerExternalEndpoint(serviceEndpoint);
        }

        public void deregisterExternalEndpoint(ServiceEndpoint serviceEndpoint) throws JBIException {
            getContext().deregisterExternalEndpoint(serviceEndpoint);
        }

        public ServiceEndpoint resolveEndpointReference(DocumentFragment documentFragment) {
            return getContext().resolveEndpointReference(documentFragment);
        }

        public String getComponentName() {
            return getContext().getComponentName();
        }

        public DeliveryChannel getDeliveryChannel() throws MessagingException {
            return channel;
        }

        public ServiceEndpoint getEndpoint(QName qName, String s) {
            return getContext().getEndpoint(qName, s);
        }

        public Document getEndpointDescriptor(ServiceEndpoint serviceEndpoint) throws JBIException {
            return getContext().getEndpointDescriptor(serviceEndpoint);
        }

        public ServiceEndpoint[] getEndpoints(QName qName) {
            return getContext().getEndpoints(qName);
        }

        public ServiceEndpoint[] getEndpointsForService(QName qName) {
            return getContext().getEndpointsForService(qName);
        }

        public ServiceEndpoint[] getExternalEndpoints(QName qName) {
            return getContext().getExternalEndpoints(qName);
        }

        public ServiceEndpoint[] getExternalEndpointsForService(QName qName) {
            return getContext().getExternalEndpointsForService(qName);
        }

        public String getInstallRoot() {
            return getContext().getInstallRoot();
        }

        public Logger getLogger(String s, String s1) throws MissingResourceException, JBIException {
            return getContext().getLogger(s, s1);
        }

        public MBeanNames getMBeanNames() {
            return getContext().getMBeanNames();
        }

        public MBeanServer getMBeanServer() {
            return getContext().getMBeanServer();
        }

        public InitialContext getNamingContext() {
            return getContext().getNamingContext();
        }

        public Object getTransactionManager() {
            return getContext().getTransactionManager();
        }

        public String getWorkspaceRoot() {
            return getContext().getWorkspaceRoot();
        }
    }

    protected class PojoChannel implements DeliveryChannel {

        public void close() throws MessagingException {
        }

        public MessageExchangeFactory createExchangeFactory() {
            return getChannel().createExchangeFactory();
        }

        public MessageExchangeFactory createExchangeFactory(QName qName) {
            return getChannel().createExchangeFactory(qName);
        }

        public MessageExchangeFactory createExchangeFactoryForService(QName qName) {
            return getChannel().createExchangeFactoryForService(qName);
        }

        public MessageExchangeFactory createExchangeFactory(ServiceEndpoint serviceEndpoint) {
            return getChannel().createExchangeFactory(serviceEndpoint);
        }

        public MessageExchange accept() throws MessagingException {
            return getChannel().accept();
        }

        public MessageExchange accept(long l) throws MessagingException {
            return getChannel().accept(l);
        }

        public void send(MessageExchange messageExchange) throws MessagingException {
            try {
                Request req = getOrCreateCurrentRequest(messageExchange);
                if (messageExchange.getRole() == MessageExchange.Role.CONSUMER
                        && messageExchange.getStatus() == ExchangeStatus.ACTIVE) {
                    if (!(req.getBean() instanceof MessageExchangeListener)) {
                        throw new IllegalStateException("A bean acting as a consumer and using the channel to send exchanges must implement the MessageExchangeListener interface");
                    }
                    req.addExchange(messageExchange);
                }
                if (messageExchange.getStatus() != ExchangeStatus.ACTIVE) {
                    checkEndOfRequest(req);
                }
                getChannel().send(messageExchange);
            } catch (MessagingException e) {
                throw e;
            } catch (Exception e) {
                throw new MessagingException(e);
            }
        }

        public boolean sendSync(MessageExchange messageExchange) throws MessagingException {
            checkEndOfRequest(messageExchange);
            return getChannel().sendSync(messageExchange);
        }

        public boolean sendSync(MessageExchange messageExchange, long l) throws MessagingException {
            checkEndOfRequest(messageExchange);
            return getChannel().sendSync(messageExchange, l);
        }
    }
}
