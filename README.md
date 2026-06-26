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

There are two ways to build and install the plugin.

### Option 1 — Maven/Tycho build (produces a p2 update site)

The plugin can be built from source using [Tycho](https://github.com/eclipse-tycho/tycho) (Maven plugin for Eclipse plugins), or used as-is from the pre-built artifact already present in [`distrib/updatesite/target/`](distrib/updatesite/target/).

#### Prerequisites

* JDK 21+
* Maven 3.9+
* A local IBM ODM Rule Designer installation (version 9.5.0.1 or higher)

#### Build

Run the following command from the [`distrib/`](distrib/) directory, pointing `odm.home` at your Rule Designer installation:

```bash
cd distrib
mvn clean package -Dodm.home=/path/to/your/RuleDesigner
```

On Windows:

```powershell
cd distrib
mvn clean package "-Dodm.home=C:\IBM\ODM9501\RuleDesigner"
```

This produces a compressed p2 update site archive at:

```
distrib/updatesite/target/com.ibm.odm.explanation.updatesite-1.0.0.zip
```

The uncompressed p2 repository (usable directly as a local update site) is also available at:

```
distrib/updatesite/target/repository/
```

#### Install via the update site

1. In Rule Designer (Eclipse), open **Help → Install New Software…**
2. Click **Add…** and then choose one of:
   - **Archive…** — point to the `.zip` file produced by the build (or the pre-built one in `distrib/updatesite/target/`)
   - **Local…** — point to the `distrib/updatesite/target/repository/` directory if you built from source
3. Select the **ODM Explanations** feature and click **Next → Finish**.
4. Restart Rule Designer when prompted.

### Option 2 — Eclipse PDE build and manual deployment

If you prefer to build and deploy the plugin directly inside Eclipse without Maven:

1. Import the [`com.ibm.rules.sample.explanation.usefulvalues`](com.ibm.rules.sample.explanation.usefulvalues/) project into an Eclipse workspace that has the ODM Rule Designer plugins on its target platform.
2. Build the plugin JAR using **File → Export… → Plug-in Development → Deployable plug-ins and fragments**. Export the `com.ibm.rules.sample.explanation.usefulvalues` plugin to a local directory.
3. Follow the IBM ODM documentation to deploy the resulting JAR to your ODM installation:

   https://www.ibm.com/docs/en/odm/9.5.0?topic=in-deploying-plug

# License
The plugin and sample found in this project are licensed under the [Apache License 2.0](LICENSE).

# Notice
© Copyright IBM Corporation 2026.
