package org.openl.rules.table.xls.formatters;

import org.openl.util.BooleanUtils;
import org.openl.util.Log;

public class XlsBooleanFormatter extends AXlsFormatter{
    
    public String format(Object value) {
        if (!(value instanceof Boolean)) {
            Log.error("Should be Boolean" + value);
            return null;
        }
        Boolean bool = (Boolean) value;
        String fBoolean = bool.toString(); 
        return fBoolean;        
    }

    public Object parse(String value) {
        Boolean boolValue = BooleanUtils.toBooleanObject(value);
        if (boolValue != null) {
            return boolValue;
        } else {
            Log.warn("Could not parse Boolean: " + value);
            return value;
        }
    }
    
}
