package com.intellij.flex.uiDesigner.libraries;

import com.intellij.flex.uiDesigner.DebugPathManager;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Pair;
import com.intellij.util.xml.NanoXmlUtil;
import gnu.trove.THashMap;

import java.io.*;
import java.util.Set;

import static com.intellij.flex.uiDesigner.libraries.FlexLibsNames.*;

class FlexDefinitionMapProcessor implements DefinitionMapProcessor {
  private final String version;
  private final Condition<String> globalContains;

  FlexDefinitionMapProcessor(String version, Condition<String> globalContains) {
    this.version = version;
    this.globalContains = globalContains;
  }

  @SuppressWarnings("unchecked")
  final static Pair<String, String>[] FLEX_LIBS_PATTERNS = new Pair[]{
    new Pair<String, String>(FRAMEWORK, "FrameworkClasses"),
    new Pair<String, String>(AIRFRAMEWORK, "AIRFrameworkClasses"),
    new Pair<String, String>(SPARK, "SparkClasses"),
    new Pair<String, String>(AIRSPARK, "AIRSparkClasses"),

    new Pair<String, String>(MX, "MxClasses"),
    new Pair<String, String>(RPC, "RPCClasses"),
    new Pair<String, String>(MOBILECOMPONENTS, "MobileComponentsClasses"),
    new Pair<String, String>("charts", "ChartsClasses"),
    new Pair<String, String>("sparkskins", "SparkSkinsClasses")};

  @Override
  public void process(THashMap<CharSequence, Definition> definitionMap, AbcMerger abcMerger) throws IOException {
    for (Pair<String, String> pair : FLEX_LIBS_PATTERNS) {
      definitionMap.remove(pair.second);
    }

    definitionMap.remove("SparkDmvClasses");

    abcMerger.setDefinitionProcessor(null);
    inject(definitionMap, abcMerger);
  }

  private static File createAbcFile(String directory, String flexVersion) {
    return new File(directory, generateInjectionName(flexVersion));
  }

  private static String generateInjectionName(String flexSdkVersion) {
    return "flex-injection-" + flexSdkVersion + ".swc";
  }

  private void inject(THashMap<CharSequence, Definition> definitionMap, AbcMerger abcMerger) throws IOException {
    final Pair<CharArrayReader, ByteArrayInputStream> data;
    if (DebugPathManager.IS_DEV) {
      data = LibraryUtil.openSwc(createAbcFile(DebugPathManager.getFudHome() + "/flex-injection/target", version));
    }
    else {
      data = LibraryUtil.openSwc(getClass().getClassLoader().getResource(generateInjectionName(version)).openStream());
    }

    final Set<CharSequence> ownDefinitions = LibraryUtil.getDefinitions(data.first);
    NanoXmlUtil.parse(data.first, new CatalogXmlBuilder(definitionMap, new Condition<String>() {
      @Override
      public boolean value(String name) {
        return globalContains.value(name) || (name.startsWith("com.intellij.") && !ownDefinitions.contains(name));
      }
    }, new Condition<String>() {
      @Override
      public boolean value(String name) {
        return globalContains.value(name) || name.equals("mx.styles:FtyleProtoChain") ||
               name.equals("spark.components.supportClasses:FkinnableComponent");
      }
    }
    ));
    abcMerger.process(data.second);
  }
}