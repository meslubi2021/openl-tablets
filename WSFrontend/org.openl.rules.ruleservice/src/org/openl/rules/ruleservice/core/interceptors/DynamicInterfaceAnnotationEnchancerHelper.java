package org.openl.rules.ruleservice.core.interceptors;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.regex.Pattern;

import net.sf.cglib.core.ReflectUtils;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassAdapter;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;
import org.openl.exception.OpenLRuntimeException;
import org.openl.rules.ruleservice.core.interceptors.annotations.ServiceCallAfterInterceptor;
import org.openl.rules.ruleservice.core.interceptors.annotations.ServiceCallBeforeInterceptor;
import org.openl.util.generation.InterfaceTransformer;

public class DynamicInterfaceAnnotationEnchancerHelper {

    private static class DynamicInterfaceAnnotationEnchancerClassAdaptor extends ClassAdapter {
        private static final String DEFAULT_ANNOTATION_VALUE = "value";

        private static final String DECORATED_CLASS_NAME_SUFFIX = "$Original";

        private Class<?> templateClass;

        public DynamicInterfaceAnnotationEnchancerClassAdaptor(ClassVisitor arg0, Class<?> templateClass) {
            super(arg0);
            this.templateClass = templateClass;
        }

        /*@Override
        public void visit(int arg0, int arg1, String arg2, String arg3, String arg4, String[] arg5) {
            super.visit(arg0, arg1, arg2 + DECORATED_CLASS_NAME_SUFFIX, arg3, arg4, arg5);
        }*/

        @Override
        public MethodVisitor visitMethod(int arg0, String arg1, String arg2, String arg3, String[] arg4) {
            if (templateClass != null) {
                Method templateMethod = null;
                for (Method method : templateClass.getMethods()) {
                    if (arg1.equals(method.getName())) {
                        Type[] typesInTemplateMethod = Type.getArgumentTypes(method);
                        Type[] typesInCurrentMethod = Type.getArgumentTypes(arg2);
                        if (typesInCurrentMethod.length == typesInCurrentMethod.length) {
                            boolean isCompatible = true;
                            for (int i = 0; i < typesInCurrentMethod.length; i++) {
                                if (!typesInCurrentMethod[i].equals(typesInTemplateMethod[i])) {
                                    Annotation[] annotations = method.getParameterAnnotations()[i];
                                    boolean isAnyTypeParameter = false;
                                    for (Annotation annotation : annotations) {
                                        if (annotation.annotationType().equals(AnyType.class)) {
                                            AnyType anyTypeAnnotation = (AnyType) annotation;
                                            String pattern = anyTypeAnnotation.value();
                                            if (pattern == null || pattern.isEmpty()) {
                                                isAnyTypeParameter = true;
                                            } else {
                                                if (Pattern.matches(pattern, typesInCurrentMethod[i].getClassName())) {
                                                    isAnyTypeParameter = true;
                                                }
                                            }
                                        }
                                    }
                                    if (!isAnyTypeParameter) {
                                        isCompatible = false;
                                        break;
                                    }
                                }
                            }
                            if (isCompatible) {
                                if (templateMethod == null) {
                                    templateMethod = method;
                                } else {
                                    throw new OpenLRuntimeException(
                                            "Invalid template class. Non-obvious choice of method. Please, check the template class!");
                                }
                            }
                        }
                    }
                }
                if (templateMethod != null) {
                    MethodVisitor mv = super.visitMethod(arg0, arg1, arg2, arg3, arg4);
                    for (Annotation annotation : templateMethod.getAnnotations()) {
                        if (annotation instanceof ServiceCallAfterInterceptor) {
                            ServiceCallAfterInterceptor serviceCallAfterInterceptor = (ServiceCallAfterInterceptor) annotation;
                            AnnotationVisitor av = mv.visitAnnotation(Type.getDescriptor(annotation.annotationType()),
                                    true);
                            AnnotationVisitor av1 = av.visitArray(DEFAULT_ANNOTATION_VALUE);
                            for (Class<? extends ServiceMethodAfterAdvice<?>> clazz : serviceCallAfterInterceptor
                                   .value()) {
                                av1.visit(null, Type.getType((Class<?>) clazz));
                            }
                            av1.visitEnd();
                            av.visitEnd();
                        }
                        if (annotation instanceof ServiceCallBeforeInterceptor) {
                            ServiceCallBeforeInterceptor serviceCallBeforeInterceptor = (ServiceCallBeforeInterceptor) annotation;
                            AnnotationVisitor av = mv.visitAnnotation(Type.getDescriptor(annotation.annotationType()),
                                    true);
                            AnnotationVisitor av1 = av.visitArray(DEFAULT_ANNOTATION_VALUE);
                            for (Class<? extends ServiceMethodBeforeAdvice> clazz : serviceCallBeforeInterceptor
                                    .value()) {
                                av1.visit(null, Type.getType((Class<?>) clazz));
                            }
                            av1.visitEnd();
                            av.visitEnd();
                        }
                    }
                    return mv;
                }
            }
            return super.visitMethod(arg0, arg1, arg2, arg3, arg4);
        }
    }

    public static Class<?> decorate(Class<?> originalClass, Class<?> templateClass, ClassLoader classLoader)
            throws Exception {
        ClassWriter cw = new ClassWriter(0);
        DynamicInterfaceAnnotationEnchancerClassAdaptor dynamicInterfaceAnnotationEnchancerClassAdaptor = new DynamicInterfaceAnnotationEnchancerClassAdaptor(
                cw, templateClass);

        String enchancedClassName = originalClass.getCanonicalName()
                + DynamicInterfaceAnnotationEnchancerClassAdaptor.DECORATED_CLASS_NAME_SUFFIX;
        InterfaceTransformer transformer = new InterfaceTransformer(originalClass, enchancedClassName);
        transformer.accept(dynamicInterfaceAnnotationEnchancerClassAdaptor);
        cw.visitEnd();
        Class<?> enchancedClass = ReflectUtils.defineClass(enchancedClassName, cw.toByteArray(),
                classLoader);
        return enchancedClass;
    }

    public static Class<?> decorate(Class<?> originalClass, Class<?> templateClass) throws Exception {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        return decorate(originalClass, templateClass, classLoader);
    }
}