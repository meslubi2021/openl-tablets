package org.openl.rules.lang.xls.binding;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.openl.OpenL;
import org.openl.binding.IBindingContext;
import org.openl.binding.IBindingContextDelegator;
import org.openl.binding.IMemberBoundNode;
import org.openl.engine.OpenLManager;
import org.openl.rules.binding.RulesModuleBindingContext;
import org.openl.rules.lang.xls.syntax.TableSyntaxNode;
import org.openl.rules.table.IGridTable;
import org.openl.rules.table.openl.GridCellSourceCodeModule;
import org.openl.rules.table.properties.ITableProperties;
import org.openl.rules.table.properties.def.TablePropertyDefinitionUtils;
import org.openl.source.IOpenSourceCodeModule;
import org.openl.source.impl.SubTextSourceCodeModule;
import org.openl.types.IOpenClass;
import org.openl.types.impl.OpenMethodHeader;

/**
 * Node binder for executable nodes with check for duplicates.
 * 
 * @author PUdalau
 */
public abstract class AExecutableNodeBinder extends AXlsTableBinder {

    @Override
    public IMemberBoundNode preBind(TableSyntaxNode tableSyntaxNode,
            OpenL openl,
            IBindingContext bindingContext,
            XlsModuleOpenClass module) throws Exception {

        OpenMethodHeader header = createHeader(tableSyntaxNode, openl, bindingContext);
        header.setDeclaringClass(module);

        checkForDuplicates(tableSyntaxNode, (RulesModuleBindingContext) bindingContext, header);

        return createNode(tableSyntaxNode, openl, header, module);
    }

    protected OpenMethodHeader createHeader(TableSyntaxNode tableSyntaxNode, OpenL openl, IBindingContext bindingContext) throws Exception {

        IGridTable table = tableSyntaxNode.getTable().getGridTable();
        IOpenSourceCodeModule source = new GridCellSourceCodeModule(table);

        SubTextSourceCodeModule headerSource = new SubTextSourceCodeModule(source, tableSyntaxNode.getHeader()
            .getHeaderToken()
            .getIdentifier()
            .length());
        IBindingContextDelegator bindingContextDelegator = (IBindingContextDelegator) bindingContext;

        return (OpenMethodHeader) OpenLManager.makeMethodHeader(openl, headerSource, bindingContextDelegator);
    }

    protected abstract IMemberBoundNode createNode(TableSyntaxNode tsn,
            OpenL openl,
            OpenMethodHeader header,
            XlsModuleOpenClass module);

    private void checkForDuplicates(TableSyntaxNode tableSyntaxNode,
            RulesModuleBindingContext bindingContext,
            OpenMethodHeader header) throws DuplicatedTableException {

        String key = makeKey(tableSyntaxNode, header);

        if (!bindingContext.isTableSyntaxNodeExist(key)) {
            bindingContext.registerTableSyntaxNode(key, tableSyntaxNode);
        } else {
            throw new DuplicatedTableException(tableSyntaxNode.getDisplayName(),
                bindingContext.getTableSyntaxNode(key),
                tableSyntaxNode);
        }
    }

    /**
     * Makes table key.
     * 
     * @param tableSyntaxNode table syntax node for key generation.
     * @param header header for executable table syntax node with its signature
     * @return key to check uniqueness of table syntax node(generated by table
     *         name, arguments types, dimensional properties and version)
     */
    private String makeKey(TableSyntaxNode tableSyntaxNode, OpenMethodHeader header) {

        StringBuilder builder = new StringBuilder();
        builder.append(header.getName());

        List<String> names = new ArrayList<String>();

        for (IOpenClass parameter : header.getSignature().getParameterTypes()) {
            names.add(parameter.getName());
        }

        builder.append("(").append(StringUtils.join(names, ", ")).append(")");

        // Dimensional properties and version
        //
        ITableProperties tableProperties = tableSyntaxNode.getTableProperties();
        List<Object> values = new ArrayList<Object>();

        for (String property : TablePropertyDefinitionUtils.getDimensionalTablePropertiesNames()) {
            values.add(tableProperties.getPropertyValue(property));
        }

        builder.append("[").append(StringUtils.join(values, ", ")).append(tableProperties.getVersion()).append("]");

        return builder.toString();
    }

}
