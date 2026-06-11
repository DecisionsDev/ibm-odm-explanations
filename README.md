# odm-explanations

The primary objective of this asset is to help business rule authors understand and explain why a particular rule was executed. It provides an explanation of rule execution by listing the conditions and values involved in the condition part of the rule at the time it was evaluated.

This explanation information is generated through an action defined in the rule. It can then be printed to a log or stored as part of a custom execution trace.

The asset provides the following benefits:

* A generic action can be added to each rule that requires an explanation. This generic action is automatically replaced at compile time with an expanded action that lists the entities and values involved in the condition part of the rule.

* The generic action used to add an explanation for an executed rule can be fully customized.

The sample included in this asset illustrates how to implement a custom explanation action.

## Design principles

This feature enables users to perform compile-time macro expansion within the Business Action Language (BAL). The macro expansion process involves three steps.

### Object Model declaration

Define and verbalize a method within the Business Object Model (BOM). In the `SampleRuleset` example, the methods are those defined in the [`Explain`](SampleRuleset/bom/model.bom) class such as `collectConditions()` and `collectUsefulValues()`. Those methods don't need a Java implementation. Indeed, they represent a macro and will be expanded at compile time. 

### Global semantic action registration

To implement the macro expansion, you must configure both the [vocabulary](SampleRuleset/bom/model_en_US.voc#L2) and the plugin metadata.

1. Declare a GlobalSemanticAction within your vocabulary file. This property specifies the identifier for the expansion logic. Example: `GlobalSemanticAction = usefulValues.semanticAction`

1. In the [`plugin.xml`](com.ibm.rules.sample.explanation.usefulvalues/plugin.xml) file, map the GlobalSemanticAction identifier to the specific Java class responsible for the expansion logic.

### Macro expansion implementation

During compilation, the semantic action identifies usages of the verbalized method and replaces them with the designated logic or expression. In other words, the semantic action replaces high-level BAL method calls with specific code expansions at the compilation stage.

You can see an example in [UsefulValuesAction.java](com.ibm.rules.sample.explanation.usefulvalues/src/com/ibm/rules/sample/explanation/usefulvalues/UsefulValuesAction.java)

## How to use this contribution

The contribution requires ODM 9.5.0.1 or higher.

1. Import the 3 folders in Rule Designer
2. Take a look at the Java code in the class `UsefulValuesAction.java`
3. Take a look at the usage in the rules and decision tables of the sample project `SampleRuleset`

## How to install in ODM

https://www.ibm.com/docs/en/odm/9.5.0?topic=in-deploying-plug

# License
The Dockerfiles and associated scripts found in this project are licensed under the [Apache License 2.0](LICENSE).

# Notice
© Copyright IBM Corporation 2026.
