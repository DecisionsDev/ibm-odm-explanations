/*
 * Copyright IBM Corp. 2026
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ibm.rules.sample.explanation.usefulvalues;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.stream.Collectors;

import com.ibm.rules.brl.dt.compilation.DtSyntaxTreeTransformerContext;

import ilog.rules.brl.IlrBRL;
import ilog.rules.brl.IlrBRLElement;
import ilog.rules.brl.IlrBRLRuleElement;
import ilog.rules.brl.bal60.IlrBAL;
import ilog.rules.brl.bal60.IlrBALBuilder;
import ilog.rules.brl.brldf.IlrBRLDefinition;
import ilog.rules.brl.semantic.IlrBRLSemanticAction;
import ilog.rules.brl.semantic.IlrBRLSemanticContext;
import ilog.rules.brl.syntaxtree.IlrNodePathError;
import ilog.rules.brl.syntaxtree.IlrSyntaxTree;
import ilog.rules.brl.util.IlrBRLBuilder;
import ilog.rules.vocabulary.model.IlrCardinality;
import ilog.rules.vocabulary.model.IlrConcept;
import ilog.rules.vocabulary.model.IlrRole;
import ilog.rules.vocabulary.model.IlrSentence;
import ilog.rules.vocabulary.model.IlrSentenceCategory;
import ilog.rules.vocabulary.model.IlrVocabulary;
import ilog.rules.vocabulary.model.bom.IlrBOMVocabulary;
import ilog.rules.vocabulary.model.helper.IlrVocabularyHelper;

/**
 * This is a global semantic action that is referenced in the user vocabulary through the key/pair
 * GlobalSemanticAction = usefulValues.semanticAction
 * <p>
 * The link between the GlobalSemanticAction value 'usefulValues.semanticAction' and this class
 * is done in the plugin.xml file.
 * <p>
 * This global action can expand two sentences (defined in the user vocabulary as well):
 * - collect useful values
 * - collect conditions
 */
public class UsefulValuesAction extends IlrBRLSemanticAction {

    /**
     * The default constructor of this semantic aciton.
     *
     * @param args The arguments given in the vocabulary file. This plugin has no argument so far.
     */
    public UsefulValuesAction(String[] args) {
        super(args);
    }

    @Override
    public void apply(IlrBRLSemanticContext semanticContext, IlrSyntaxTree.Node root) {
        IlrSyntaxTree syntaxTree = root.getSyntaxTree();
        IlrBRLElement brlElement = syntaxTree.getEditedElement();
        IlrBOMVocabulary vocabulary = (IlrBOMVocabulary)semanticContext.getVocabulary();
        DtSyntaxTreeTransformerContext dtContext = null;
        List<UsefulAction> usefulValuesActions = null;
        List<UsefulValue> usefulValues = null;
        IlrSyntaxTree.Node conditions = null;

        // Collect:
        //  - The usage of the sentences to expand ('collect useful values' and 'collect conditions')
        //  - The values used in the condition part of a rule or decision table
        if (IlrBAL.RULE_AXIOM.equals(root.getName())) {
            // -- Action Rule
            // Scan the action part of the rule and identify if there is a 'usefulValues' sentence used.
            usefulValuesActions = collectUsefulValuesActions(getNodeFromPath(syntaxTree, "/rule/actions"), vocabulary);
            if (usefulValuesActions != null && !usefulValuesActions.isEmpty()) {
                // 'usefulValues' sentence is currenrly used in the action part of the rule,
                // we need to collect the path expressions used in the bindings and conditions
                String ruleText = getRuleText(syntaxTree);
                usefulValues = new ArrayList<>();
                collectUsefulValues(ruleText, getNodeFromPath(syntaxTree, "/rule/bindings"), vocabulary, usefulValues);
                conditions = getNodeFromPath(syntaxTree, "/rule/conditions");
                collectUsefulValues(ruleText, conditions, vocabulary, usefulValues);
            }
        } else {
            // -- Decision Table
            dtContext = (DtSyntaxTreeTransformerContext)semanticContext.getExtension().getProperty("dtTransformerContext");
            UsefulValuesStack usefulValuesStack = dtContext.getUserData(UsefulValuesStack.class);
            if (usefulValuesStack == null) {
                dtContext.addUserData(usefulValuesStack = new UsefulValuesStack(dtContext));
            }
            switch (root.getName()) {
                case IlrBAL.SIMPLE_RULE_AXIOM:
                    // Decision table precondition
                    usefulValues = new ArrayList<>();
                    collectUsefulValues(dtContext.getDefinition(), getNodeFromPath(syntaxTree, "/simple-rule/bindings"), vocabulary, usefulValues);
                    collectUsefulValues(dtContext.getDefinition(), getNodeFromPath(syntaxTree, "/simple-rule/conditions"), vocabulary, usefulValues);
                    usefulValuesStack.pushPreconditions(usefulValues);
                    break;
                case IlrBAL.PREDICATE_OR_ACTION_AXIOM:
                    // Decision table action or condition
                    if (dtContext.isAction()) {
                        usefulValuesActions = collectUsefulValuesActions(getNodeFromPath(syntaxTree, "/predicate-or-action/action"), vocabulary);
                        usefulValues = usefulValuesStack.getUsefulValues();
                        usefulValuesStack.fillMissingConditionsBeforeActions();
                    } else {
                        usefulValues = collectUsefulValues(dtContext.getDefinition(), getNodeFromPath(syntaxTree, "/predicate-or-action/condition"), vocabulary);
                        usefulValuesStack.pushCondition(dtContext.getRow(), dtContext.getColumn(), usefulValues);
                    }
                    break;
            }
        }

        // Once we collected the usage and the reference of the 'usefulValues',
        // we can perform expansion
        if (usefulValuesActions != null && !usefulValuesActions.isEmpty()) {
            if (usefulValues != null) {
                // expand 'collect useful values'
                IlrSyntaxTree.Node usefulValuesNode = computeNodeReplacementForUsefulValuesActions(brlElement.getName(), dtContext != null ? dtContext.getRow() : -1,
                        usefulValues, semanticContext.getDefinition(), vocabulary);
                if (usefulValuesNode != null) {
                    for (UsefulAction usefulAction : usefulValuesActions) {
                        if (usefulAction.kind == UsefulAction.Kind.VALUES) {
                            usefulAction.node.setProperty("ShadowNode", usefulValuesNode);
                        }
                    }
                }
            }
            if (brlElement instanceof IlrBRLRuleElement ruleElement && conditions != null) {
                // expand 'collect conditions'
                String ruleText = ruleElement.getDefinition();
                if (ruleText != null && !ruleText.isBlank()) {
                    // Compute the BAL expression representing the text of the conditions
                    String conditionsText = ruleText.substring(conditions.getOffset(), conditions.getOffset() + conditions.getLength());
                    var balBuilder = new IlrBALBuilder(semanticContext.getDefinition(), vocabulary);
                    IlrSyntaxTree.Node usefulConditionsNode = balBuilder.buildExpression(balBuilder.stringValue(conditionsText)).getSuperNode();
                    // Replace the node in the syntax tree
                    for (UsefulAction usefulAction : usefulValuesActions) {
                        if (usefulAction.kind == UsefulAction.Kind.CONDITION) {
                            usefulAction.node.setProperty("ShadowNode", usefulConditionsNode);
                        }
                    }
                }
            }
        }
    }

    // Generate a syntax tree that represents the 'collect useful values' expansion using IlrBALBuilder
    private static IlrSyntaxTree.Node computeNodeReplacementForUsefulValuesActions(String ruleName, int row, List<UsefulValue> usefulValues,
                                                                                   IlrBRLDefinition definition, IlrVocabulary vocabulary) {
        IlrBALBuilder.Expression usefulValuesExpr = null;
        var balBuilder = new IlrBALBuilder(definition, vocabulary);
        for (UsefulValue value : usefulValues) {
            IlrBALBuilder.Expression valueExpr;
            var typeInfo = IlrBRL.getSyntaxNodeTypeInfo(value.node, vocabulary);
            if (typeInfo.getCardinality() == IlrCardinality.MULTIPLE_LITERAL) {
                if (vocabulary.isValueType(typeInfo.getConcept())) {
                    valueExpr = formatValues(balBuilder.expressionHandler(value.node), balBuilder);
                } else {
                    valueExpr = formatObjects(balBuilder.expressionHandler(value.node), balBuilder);
                }
            } else {
                if (vocabulary.isValueType(typeInfo.getConcept())) {
                    valueExpr = formatValue(balBuilder.expressionHandler(value.node), balBuilder);
                } else {
                    valueExpr = formatObject(balBuilder.expressionHandler(value.node), balBuilder);
                }
            }
            IlrBALBuilder.Expression usefulValueExpr = balBuilder.concat(balBuilder.concat(
                            balBuilder.concat(balBuilder.stringValue(value.text), balBuilder.stringValue(" = ")), valueExpr),
                    balBuilder.stringValue("\n"));
            if (usefulValuesExpr == null) {
                usefulValuesExpr = usefulValueExpr;
            } else {
                usefulValuesExpr = balBuilder.concat(usefulValuesExpr, usefulValueExpr);
            }
        }
        if (usefulValuesExpr != null) {
            var header = balBuilder.concat(balBuilder.stringValue("-- "),
                    balBuilder.stringValue(ruleName + (row < 0 ? "\n" : "")));
            if (row >= 0) {
                header = balBuilder.concat(header, balBuilder.stringValue("; row " + (row + 1) + "\n"));
            }
            usefulValuesExpr = balBuilder.concat(header, usefulValuesExpr);
            return balBuilder.buildExpression(usefulValuesExpr).getSuperNode();
        }
        return null;
    }

    // Collect the usage of the 'collect useful values' and 'collect conditions' sentences in the
    // action part of a rule or decision table
    private static List<UsefulAction> collectUsefulValuesActions(IlrSyntaxTree.Node actions, IlrVocabulary vocabulary) {
        if (actions == null) {
            return Collections.emptyList();
        }
        List<UsefulAction> usefulValuesActions = new ArrayList<>();
        Queue<IlrSyntaxTree.Node> queue = new LinkedList<>();
        queue.add(actions);
        while (!queue.isEmpty()) {
            IlrSyntaxTree.Node node = queue.poll();
            IlrSentence sentence = IlrBRL.getSyntaxNodeSentence(node, vocabulary);
            UsefulAction usefulAction = null;
            if (sentence != null && sentence.getCategory().is(IlrSentenceCategory.NAVIGATION_LITERAL)) {
                if ("com.ibm.rules.explanation.Explain/collectUsefulValues()".equals(sentence.getFactType().getIdentifier())) {
                    usefulAction = new UsefulAction(UsefulAction.Kind.VALUES, node);
                } else if ("com.ibm.rules.explanation.Explain/collectConditions()".equals(sentence.getFactType().getIdentifier())) {
                    usefulAction = new UsefulAction(UsefulAction.Kind.CONDITION, node);
                }
            }
            if (usefulAction != null) {
                usefulValuesActions.add(usefulAction);
            } else {
                for (IlrSyntaxTree.Iterator n = node.iterator(IlrSyntaxTree.SUBNODES); n.hasNext(); ) {
                    queue.add(n.nextNode());
                }
            }
        }
        return usefulValuesActions;
    }

    // Gets the path expressions used in the conditions of a given rule or decision table
    private static List<UsefulValue> collectUsefulValues(String text, IlrSyntaxTree.Node root, IlrBOMVocabulary vocabulary) {
        if (root == null) {
            return Collections.emptyList();
        }
        List<UsefulValue> usefulValues = new ArrayList<>();
        collectUsefulValues(text, root, vocabulary, usefulValues);
        return usefulValues;
    }

    private static void collectUsefulValues(String text, IlrSyntaxTree.Node root, IlrBOMVocabulary vocabulary, List<UsefulValue> usefulValues) {
        if (root != null) {
            Queue<IlrSyntaxTree.Node> queue = new LinkedList<>();
            queue.add(root);
            while (!queue.isEmpty()) {
                IlrSyntaxTree.Node node = queue.poll();
                boolean matched = false;
                IlrSentence sentence = IlrBRL.getSyntaxNodeSentence(node, vocabulary);
                if (sentence != null && sentence.getCategory().is(IlrSentenceCategory.GETTER_LITERAL)) {
                    IlrRole role = IlrVocabularyHelper.getSubjectRole(sentence);
                    if (role != null) {
                        IlrConcept concept = vocabulary.getConcept(role);
                        if (concept != null /* && vocabulary.isValueType(concept) */) {
                            matched = true;
                        }
                    }
                }
                if (matched) {
                    // Warning: some cleaning might be necessary for the text (formatting or escaping...)
                    String valueText = text.substring(node.getOffset(), node.getOffset() + node.getLength());
                    usefulValues.add(new UsefulValue(valueText, node));
                } else {
                    for (IlrSyntaxTree.Iterator n = node.iterator(IlrSyntaxTree.SUBNODES); n.hasNext(); ) {
                        queue.add(n.nextNode());
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------------
    // Utilities
    // ------------------------------------------------------------------------

    // Build a syntax tree node that invoke the formatting method for a given value.
    // This node will be part of the 'collect useful values' expansion
    private static IlrBRLBuilder.Expression formatValue(IlrBRLBuilder.Expression valueExpr, IlrBALBuilder balBuilder) {
        return new IlrBRLBuilder.SentenceExpression((IlrSentence)balBuilder.getVocabulary().getFactType("com.ibm.rules.explanation.Explain/formatValue(java.lang.Object)").getSentences().get(0),
                new IlrBRLBuilder.Expression[]{valueExpr});
    }

    // Build a syntax tree node that invoke the formatting method for a list of values.
    // This node will be part of the 'collect useful values' expansion
    private static IlrBRLBuilder.Expression formatValues(IlrBRLBuilder.Expression valueExpr, IlrBALBuilder balBuilder) {
        return new IlrBRLBuilder.SentenceExpression((IlrSentence)balBuilder.getVocabulary().getFactType("com.ibm.rules.explanation.Explain/formatValues(java.lang.Object[])").getSentences().get(0),
                new IlrBRLBuilder.Expression[]{valueExpr});
    }

    // Build a syntax tree node that invoke the formatting method for a given object.
    // This node will be part of the 'collect useful values' expansion
    private static IlrBRLBuilder.Expression formatObject(IlrBRLBuilder.Expression valueExpr, IlrBALBuilder balBuilder) {
        return new IlrBRLBuilder.SentenceExpression((IlrSentence)balBuilder.getVocabulary().getFactType("com.ibm.rules.explanation.Explain/formatObject(java.lang.Object)").getSentences().get(0),
                new IlrBRLBuilder.Expression[]{valueExpr});
    }

    // Build a syntax tree node that invoke the formatting method for a given list of objects.
    // This node will be part of the 'collect useful values' expansion
    private static IlrBRLBuilder.Expression formatObjects(IlrBRLBuilder.Expression valueExpr, IlrBALBuilder balBuilder) {
        return new IlrBRLBuilder.SentenceExpression((IlrSentence)balBuilder.getVocabulary().getFactType("com.ibm.rules.explanation.Explain/formatObjects(java.lang.Object[])").getSentences().get(0),
                new IlrBRLBuilder.Expression[]{valueExpr});
    }

    private static String getRuleText(IlrSyntaxTree syntaxTree) {
        IlrBRLElement brlElement = syntaxTree.getEditedElement();
        if (brlElement instanceof IlrBRLRuleElement ruleElement) {
            return ruleElement.getDefinition();
        }
        return null;
    }

    private static IlrSyntaxTree.Node getNodeFromPath(IlrSyntaxTree syntaxTree, String nodePath) {
        try {
            return syntaxTree.getNodeFromPath(nodePath);
        } catch (IlrNodePathError e) {
            return null;
        }
    }

    // Represents a useful value (text and node pair)
    static record UsefulValue(String text, IlrSyntaxTree.Node node) {
    }

    // Represents a useful action (condition or values)
    static final class UsefulAction {

        enum Kind {CONDITION, VALUES}

        final Kind kind;
        final IlrSyntaxTree.Node node;

        UsefulAction(Kind kind, IlrSyntaxTree.Node node) {
            this.kind = Objects.requireNonNull(kind);
            this.node = Objects.requireNonNull(node);
        }
    }

    // This class is used to maintain internal states while scanning a decision table
    static final class UsefulValuesStack {

        private final DtSyntaxTreeTransformerContext dtContext;
        private final List<UsefulValue> usefulValues = new ArrayList<>();
        private int preconditionsCount = 0;
        private int row;

        UsefulValuesStack(DtSyntaxTreeTransformerContext dtContext) {
            this.dtContext = dtContext;
        }

        void pushPreconditions(List<UsefulValue> usefulValues) {
            this.usefulValues.addAll(usefulValues);
            preconditionsCount = usefulValues.size();
        }

        private int conditionCount() {
            return this.usefulValues.size() - preconditionsCount;
        }

        private void pop(int count) {
            for (int i = 0; i < count; i++) {
                this.usefulValues.remove(this.usefulValues.size() - 1);
            }
        }

        private void push(int count) {
            for (int i = 0; i < count; i++) {
                this.usefulValues.add(null);
            }
        }

        void pushCondition(int row, int column, List<UsefulValue> usefulValues) {
            if (row > this.row) {
                if (conditionCount() > column) {
                    pop(conditionCount() - column);
                }
                this.row = row;
            }
            if (column > conditionCount()) {
                push(column - conditionCount());
            }
            this.usefulValues.addAll(usefulValues);
        }

        void fillMissingConditionsBeforeActions() {
            push(conditionCount() - dtContext.getDtModel().getConditionDefinitionList().size());
        }

        List<UsefulValue> getUsefulValues() {
            return usefulValues.stream().filter(v -> v != null).collect(Collectors.toList());
        }
    }
}
