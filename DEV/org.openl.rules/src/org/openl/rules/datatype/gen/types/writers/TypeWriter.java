package org.openl.rules.datatype.gen.types.writers;

import org.objectweb.asm.MethodVisitor;
import org.openl.rules.datatype.gen.FieldDescription;

public interface TypeWriter {

    void writeFieldValue(MethodVisitor methodVisitor, FieldDescription fieldType);

}
