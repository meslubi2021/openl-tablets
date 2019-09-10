package org.openl.rules.ruleservice.logging;

import java.lang.reflect.Method;
import java.util.function.Predicate;

import org.openl.binding.MethodUtil;
import org.openl.rules.ruleservice.core.interceptors.ServiceInvocationAdviceListener;
import org.openl.rules.ruleservice.core.interceptors.ServiceMethodAdvice;
import org.openl.rules.ruleservice.logging.advice.ObjectSerializerAware;
import org.openl.rules.ruleservice.logging.advice.StoreLoggingAdvice;
import org.openl.rules.ruleservice.logging.annotation.StoreLogging;
import org.openl.rules.ruleservice.logging.annotation.StoreLoggings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StoreLoggingsServiceInvocationAdviceListener implements ServiceInvocationAdviceListener {
    private final Logger log = LoggerFactory.getLogger(StoreLoggingsServiceInvocationAdviceListener.class);

    private boolean loggingStoreEnabled = false;

    public boolean isLoggingStoreEnabled() {
        return loggingStoreEnabled;
    }

    public void setLoggingStoreEnabled(boolean loggingStoreEnabled) {
        this.loggingStoreEnabled = loggingStoreEnabled;
    }

    public void process(Method interfaceMethod,
            Object[] args,
            Object result,
            Exception lastOccuredException,
            Predicate<StoreLogging> predicate) {
        StoreLoggings storeLoggings = interfaceMethod.getAnnotation(StoreLoggings.class);
        if (storeLoggings != null) {
            RuleServiceLogging ruleServiceLogging = null;
            for (StoreLogging storeLogging : storeLoggings.value()) {
                if (predicate.test(storeLogging)) {
                    StoreLoggingAdvice storeLoggingAdvice = null;
                    try {
                        storeLoggingAdvice = storeLogging.value().newInstance();
                        if (storeLoggingAdvice instanceof ObjectSerializerAware) {
                            ObjectSerializerAware objectSerializerAware = (ObjectSerializerAware) storeLoggingAdvice;
                            if (ruleServiceLogging == null) {
                                ruleServiceLogging = RuleServiceLoggingHolder.get(); // Lazy local variable
                                                                                     // initialization
                            }
                            objectSerializerAware.setObjectSerializer(ruleServiceLogging.getObjectSerializer());
                        }
                    } catch (Exception e) {
                        String msg = String.format(
                            "Failed to instantiate store logging advice for '%s' method. Please, check that '%s' class isn't abstact and has a default constructor.",
                            MethodUtil.printQualifiedMethodName(interfaceMethod),
                            storeLogging.value().getTypeName());
                        log.error(msg, e);
                    }
                    if (storeLoggingAdvice != null) {
                        if (ruleServiceLogging == null) {
                            ruleServiceLogging = RuleServiceLoggingHolder.get(); // Lazy local variable initialization
                        }
                        LoggingCustomData loggingCustomData = storeLoggingAdvice.populateCustomData(
                            getLoggingCustomData(ruleServiceLogging),
                            args,
                            result,
                            lastOccuredException);
                        ruleServiceLogging.setLoggingCustomData(loggingCustomData);
                    }
                }
            }
        }
    }

    @Override
    public void beforeServiceMethodAdvice(ServiceMethodAdvice serviceMethodAdvice,
            Method interfaceMethod,
            Object[] args,
            Object result,
            Exception lastOccuredException) {
        if (isLoggingStoreEnabled()) {
            process(interfaceMethod,
                args,
                result,
                lastOccuredException,
                e -> e.before() && e.bindToServiceMethodAdvice().equals(serviceMethodAdvice.getClass()));
        }
    }

    @Override
    public void afterServiceMethodAdvice(ServiceMethodAdvice serviceMethodAdvice,
            Method interfaceMethod,
            Object[] args,
            Object result,
            Exception lastOccuredException) {
        if (isLoggingStoreEnabled()) {
            process(interfaceMethod,
                args,
                result,
                lastOccuredException,
                e -> !e.before() && e.bindToServiceMethodAdvice().equals(serviceMethodAdvice.getClass()));
        }
    }

    @Override
    public void beforeMethodInvocation(Method interfaceMethod,
            Object[] args,
            Object result,
            Exception lastOccuredException) {
        if (isLoggingStoreEnabled()) {
            process(interfaceMethod,
                args,
                result,
                lastOccuredException,
                e -> e.before() && e.bindToServiceMethodAdvice().equals(StoreLogging.Default.class));
        }
    }

    @Override
    public void afterMethodInvocation(Method interfaceMethod,
            Object[] args,
            Object result,
            Exception lastOccuredException) {
        if (isLoggingStoreEnabled()) {
            process(interfaceMethod,
                args,
                result,
                lastOccuredException,
                e -> !e.before() && e.bindToServiceMethodAdvice().equals(StoreLogging.Default.class));
        }
    }

    private LoggingCustomData getLoggingCustomData(RuleServiceLogging ruleServiceLogging) {
        return ruleServiceLogging.getLoggingCustomData() != null ? ruleServiceLogging.getLoggingCustomData()
                                                                 : new LoggingCustomData();
    }

}
