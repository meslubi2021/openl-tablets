package org.openl.rules.cmatch.algorithm;

import org.openl.rules.cmatch.ColumnMatch;
import org.openl.rules.cmatch.MatchNode;
import org.openl.rules.cmatch.matcher.IMatcher;
import org.openl.vm.IRuntimeEnv;
import org.openl.vm.trace.Tracer;

public class MatchAlgorithmExecutor implements IMatchAlgorithmExecutor {
    public static final Object NO_MATCH = null;

    private void fillTracer(ColumnMatch columnMatch, MatchNode line, int resultIndex, Object[] params) {
        if (Tracer.isTracerDefined()) {

            ColumnMatchTraceObject traceObject = new ColumnMatchTraceObject(columnMatch, params);
            Object returnValues[] = columnMatch.getReturnValues();
            traceObject.setResult(returnValues[resultIndex]);

            Tracer.begin(traceObject);

            for (MatchNode node : line.getChildren()) {
                Tracer.put(new MatchTraceObject(columnMatch, node.getRowIndex(), resultIndex));
            }

            Tracer.put(new ResultTraceObject(columnMatch, resultIndex));

            Tracer.end();
        }
    }

    public Object invoke(Object target, Object[] params, IRuntimeEnv env, ColumnMatch columnMatch) {
        MatchNode checkTree = columnMatch.getCheckTree();
        Object returnValues[] = columnMatch.getReturnValues();

        // iterate over linearized nodes
        for (MatchNode line : checkTree.getChildren()) {
            if (line.getRowIndex() >= 0) {
                throw new IllegalArgumentException("Linearized MatchNode tree expected!");
            }

            // find matching result value from left to right
            for (int resultIndex = 0; resultIndex < returnValues.length; resultIndex++) {
                boolean success = true;
                // check that all children are MATCH at resultIndex element
                for (MatchNode node : line.getChildren()) {
                    Argument arg = node.getArgument();
                    Object var = arg.extractValue(target, params, env);
                    IMatcher matcher = node.getMatcher();
                    Object checkValue = node.getCheckValues()[resultIndex];
                    if (!matcher.match(var, checkValue)) {
                        success = false;
                        break;
                    }
                }

                if (success) {
                    fillTracer(columnMatch, line, resultIndex, params);
                    return returnValues[resultIndex];
                }
            }
        }

        if (Tracer.isTracerDefined()) {
            ColumnMatchTraceObject traceObject = new ColumnMatchTraceObject(columnMatch, params);
            traceObject.setResult(NO_MATCH);

            Tracer.put(traceObject);
        }
        return NO_MATCH;
    }
}
