package org.openl.types.java;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.collections.CollectionUtils;
import org.openl.types.IOpenClass;
import org.openl.types.IOpenField;
import org.openl.types.IOpenMember;
import org.openl.types.IOpenMethod;

public class OpenClassHelper {
    
    public static synchronized IOpenClass getOpenClass(IOpenClass moduleOpenClass, Class<?> classToFind) {
        IOpenClass result = null;
        if (classToFind != null) {
            Map<String, IOpenClass> internalTypes = moduleOpenClass.getTypes();
            if (classToFind.isArray()) {
                IOpenClass componentType = findType(classToFind.getComponentType(), internalTypes);
                if (componentType != null) {
                    result = componentType.getAggregateInfo().getIndexedAggregateType(componentType, 1);
                }
            } else {
                result = findType(classToFind, internalTypes);
            }
            
            if (result == null) {
                result = JavaOpenClass.getOpenClass(classToFind);
            }
        }
        return result;
    }

    private static IOpenClass findType(Class<?> classToFind, Map<String, IOpenClass> internalTypes) {
        IOpenClass result = null;
        for (IOpenClass datatypeClass : internalTypes.values()) {
            if (classToFind.equals(datatypeClass.getInstanceClass())) {
                result = datatypeClass;
                break;
            }
        }
        return result;
    }
    
    public static synchronized IOpenClass[] getOpenClasses(IOpenClass moduleOpenClass, Class<?>[] classesToFind) {
        List<IOpenClass> openClassList = new ArrayList<IOpenClass>();
        if (classesToFind.length == 0) {
            return IOpenClass.EMPTY;
        }
        
        for (Class<?> classToFind : classesToFind) {
            openClassList.add(getOpenClass(moduleOpenClass, classToFind));
        }
        return openClassList.toArray(new IOpenClass[openClassList.size()]);
        
    }

    /**
     * Gets members (fields and methods) of given IOpenClass instance.
     * 
     * @param openClass IOpenClass instance
     * @return array of members
     */
    public static IOpenMember[] getClassMembers(IOpenClass openClass) {

        List<IOpenMember> members = new ArrayList<IOpenMember>();

        if (openClass != null) {

            Iterator<IOpenMethod> methodIterator = openClass.methods();
            CollectionUtils.addAll(members, methodIterator);

            Iterator<IOpenField> fieldIterator = openClass.fields();
            CollectionUtils.addAll(members, fieldIterator);
        }

        return members.toArray(new IOpenMember[members.size()]);
    }

    /**
     * Convert open classes to array of instance classes.
     * 
     * @param openClasses array of open classes
     * @return array of instance classes
     */
    public static Class<?>[] getInstanceClasses(IOpenClass[] openClasses) {

        List<Class<?>> classes = new ArrayList<Class<?>>();

        if (openClasses != null) {
            for (IOpenClass openClass : openClasses) {

                Class<?> clazz = openClass.getInstanceClass();
                classes.add(clazz);
            }
        }

        return classes.toArray(new Class<?>[classes.size()]);
    }
}
