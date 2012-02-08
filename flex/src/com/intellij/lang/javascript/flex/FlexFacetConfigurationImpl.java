package com.intellij.lang.javascript.flex;

import com.intellij.facet.Facet;
import com.intellij.facet.FacetManager;
import com.intellij.facet.ui.*;
import com.intellij.ide.ui.ListCellRendererWrapper;
import com.intellij.idea.LoggerFactory;
import com.intellij.lang.javascript.flex.build.FlexBuildConfiguration;
import com.intellij.lang.javascript.flex.sdk.FlexSdkUtils;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ui.configuration.ModuleEditor;
import com.intellij.openapi.roots.ui.configuration.ProjectStructureConfigurable;
import com.intellij.openapi.roots.ui.configuration.projectRoot.ModuleStructureConfigurable;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.ui.navigation.Place;
import com.intellij.util.ArrayUtil;
import com.intellij.util.PlatformIcons;
import org.jdom.Element;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static com.intellij.lang.javascript.flex.build.FlexBuildConfiguration.ConditionalCompilationDefinition;
import static com.intellij.lang.javascript.flex.build.FlexBuildConfiguration.NamespaceAndManifestFileInfo;

public class FlexFacetConfigurationImpl extends FlexFacetConfiguration {

  /**
   * May be empty. May be not valid, i.e. no Sdk with such name is configured in IDEA
   * (can happen when project is moved to another computer).
   * This field should be used only in UI as initial erroneous selection if {@link #myFlexSdk} is <code>null</code>
   */
  private @NotNull String myFlexSdkName = FLEX_SDK_NOT_YET_SELECTED_FOR_NEW_FACET;
  private @Nullable Sdk myFlexSdk;

  /**
   * Don't use this value in UI, change it to empty string instead.
   * If at least one Flex SDK is configured user is forced to select it.
   * If none - then commit empty String. So this placeholder means that Facet is created and not committed yet.
   */
  private static final String FLEX_SDK_NOT_YET_SELECTED_FOR_NEW_FACET = "Flex SDK not yet selected for Flex facet";

  private FlexBuildConfiguration myFlexBuildConfiguration;
  public static final @NonNls String FLEX_SDK_ATTR_NAME = "flex_sdk";
  private static final String NAMESPACE_AND_MANIFEST_FILE_INFO_LIST_ELEMENT_NAME = "NAMESPACE_AND_MANIFEST_FILE_INFO_LIST";
  private static final String CONDITIONAL_COMPILER_DEFINITION_LIST_ELEMENT_NAME = "CONDITIONAL_COMPILATION_DEFINITION_LIST";
  private static final String CSS_FILES_LIST_ELEMENT_NAME = "CSS_FILES_LIST";
  private static final String FILE_PATH_ELEMENT_NAME = "FILE_PATH";

  private static final Logger LOG = LoggerFactory.getInstance().getLoggerInstance(FlexFacetConfigurationImpl.class.getName());

  public FlexFacetConfigurationImpl() {
    AutogeneratedLibraryUtils.registerSdkTableListenerIfNeeded();
    myFlexBuildConfiguration = new FlexBuildConfiguration();
    myFlexBuildConfiguration.DO_BUILD = true;
    myFlexBuildConfiguration.OUTPUT_FILE_NAME = "flex.swf";
  }

  public FacetEditorTab[] createEditorTabs(final FacetEditorContext editorContext, final FacetValidatorsManager validatorsManager) {
    return new FacetEditorTab[]{new FlexFacetEditorTab(editorContext, validatorsManager)};
  }

  public void readExternal(final Element element) throws InvalidDataException {
    final String s = element.getAttributeValue(FLEX_SDK_ATTR_NAME);
    final Sdk sdk = ProjectJdkTable.getInstance().findJdk(s);
    if (sdk != null && sdk.getSdkType() instanceof IFlexSdkType) {
      myFlexSdk = sdk;
      AutogeneratedLibraryUtils.registerSdkRootsListenerIfNeeded(myFlexSdk);
      myFlexSdkName = sdk.getName();
    }
    else if (s != null) {
      myFlexSdkName = s;
    }

    final FlexBuildConfiguration tempConfig = new FlexBuildConfiguration();
    DefaultJDOMExternalizer.readExternal(tempConfig, element);
    myFlexBuildConfiguration.loadState(tempConfig);

    readNamespaceAndManifestInfoList(element, myFlexBuildConfiguration);
    readConditionalCompilerDefinitionList(element, myFlexBuildConfiguration);
    readCssFilesList(element, myFlexBuildConfiguration);
  }

  public static void readNamespaceAndManifestInfoList(final Element element,
                                                      final FlexBuildConfiguration oldConfig) throws InvalidDataException {
    final List<NamespaceAndManifestFileInfo> namespaceAndManifestFileInfoList = new ArrayList<NamespaceAndManifestFileInfo>();

    final Element namespaceAndManifestFileInfoListElement = element.getChild(NAMESPACE_AND_MANIFEST_FILE_INFO_LIST_ELEMENT_NAME);
    if (namespaceAndManifestFileInfoListElement != null) {
      for (final Object namespaceAndManifestFileInfoElement : namespaceAndManifestFileInfoListElement
        .getChildren(NamespaceAndManifestFileInfo.class.getSimpleName())) {
        final NamespaceAndManifestFileInfo namespaceAndManifestFileInfo = new NamespaceAndManifestFileInfo();
        DefaultJDOMExternalizer.readExternal(namespaceAndManifestFileInfo, (Element)namespaceAndManifestFileInfoElement);
        namespaceAndManifestFileInfoList.add(namespaceAndManifestFileInfo);
      }
    }
    oldConfig.NAMESPACE_AND_MANIFEST_FILE_INFO_LIST = namespaceAndManifestFileInfoList;
  }

  public static void readConditionalCompilerDefinitionList(final Element element,
                                                           final FlexBuildConfiguration oldConfig) throws InvalidDataException {
    final List<ConditionalCompilationDefinition> conditionalCompilationDefinitionList =
      new ArrayList<ConditionalCompilationDefinition>();

    final Element conditionalCompilerDefinitionListElement = element.getChild(CONDITIONAL_COMPILER_DEFINITION_LIST_ELEMENT_NAME);
    if (conditionalCompilerDefinitionListElement != null) {
      for (final Object conditionalCompilerDefinitionElement : conditionalCompilerDefinitionListElement
        .getChildren(ConditionalCompilationDefinition.class.getSimpleName())) {
        final ConditionalCompilationDefinition conditionalCompilationDefinition = new ConditionalCompilationDefinition();
        DefaultJDOMExternalizer.readExternal(conditionalCompilationDefinition, (Element)conditionalCompilerDefinitionElement);
        conditionalCompilationDefinitionList.add(conditionalCompilationDefinition);
      }
    }
    oldConfig.CONDITIONAL_COMPILATION_DEFINITION_LIST = conditionalCompilationDefinitionList;
  }

  public static void readCssFilesList(final Element element,
                                      final FlexBuildConfiguration oldConfig) throws InvalidDataException {
    final List<String> cssFilesList = new ArrayList<String>();

    final Element cssFilesListElement = element.getChild(CSS_FILES_LIST_ELEMENT_NAME);
    if (cssFilesListElement != null) {
      //noinspection unchecked
      for (Element conditionalCompilerDefinitionElement : (Iterable<Element>)cssFilesListElement.getChildren(FILE_PATH_ELEMENT_NAME)) {
        cssFilesList.add(conditionalCompilerDefinitionElement.getValue());
      }
    }
    oldConfig.CSS_FILES_LIST = cssFilesList;
  }

  public void writeExternal(final Element element) throws WriteExternalException {
    if (myFlexSdk != null) {
      element.setAttribute(FLEX_SDK_ATTR_NAME, myFlexSdk.getName());
    }
    DefaultJDOMExternalizer.writeExternal(myFlexBuildConfiguration.getState(), element);

    writeNamespaceAndManifestInfoList(element);
    writeConditionalCompilerDefinitionList(element);
    writeCssFilesList(element);
  }

  private void writeNamespaceAndManifestInfoList(final Element element) throws WriteExternalException {
    final Element namespaceAndManifestFileInfoListElement = new Element(NAMESPACE_AND_MANIFEST_FILE_INFO_LIST_ELEMENT_NAME);
    for (NamespaceAndManifestFileInfo namespaceAndManifestFileInfo : myFlexBuildConfiguration.NAMESPACE_AND_MANIFEST_FILE_INFO_LIST) {
      final Element namespaceAndManifestFileInfoElement = new Element(NamespaceAndManifestFileInfo.class.getSimpleName());
      DefaultJDOMExternalizer.writeExternal(namespaceAndManifestFileInfo, namespaceAndManifestFileInfoElement);
      namespaceAndManifestFileInfoListElement.addContent(namespaceAndManifestFileInfoElement);
    }
    element.addContent(namespaceAndManifestFileInfoListElement);
  }

  private void writeConditionalCompilerDefinitionList(final Element element) throws WriteExternalException {
    final Element conditionalCompilerDefinitionListElement = new Element(CONDITIONAL_COMPILER_DEFINITION_LIST_ELEMENT_NAME);
    for (ConditionalCompilationDefinition conditionalCompilationDefinition : myFlexBuildConfiguration.CONDITIONAL_COMPILATION_DEFINITION_LIST) {
      final Element conditionalCompilerDefinitionElement = new Element(ConditionalCompilationDefinition.class.getSimpleName());
      DefaultJDOMExternalizer.writeExternal(conditionalCompilationDefinition, conditionalCompilerDefinitionElement);
      conditionalCompilerDefinitionListElement.addContent(conditionalCompilerDefinitionElement);
    }
    element.addContent(conditionalCompilerDefinitionListElement);
  }

  private void writeCssFilesList(final Element element) throws WriteExternalException {
    final Element cssFilesListElement = new Element(CSS_FILES_LIST_ELEMENT_NAME);
    for (String cssFilePath : myFlexBuildConfiguration.CSS_FILES_LIST) {
      final Element pathElement = new Element(FILE_PATH_ELEMENT_NAME);
      pathElement.setText(cssFilePath);
      cssFilesListElement.addContent(pathElement);
    }
    element.addContent(cssFilesListElement);
  }

  @Nullable
  public Sdk getFlexSdk() {
    return myFlexSdk;
  }

  /**
   * {@inheritDoc}
   */
  public void setFlexSdk(final @Nullable Sdk flexSdk, final @NotNull ModifiableRootModel modifiableRootModel) {
    // this code can't be substituted by setFlexSdkForAllFacets(..) because this facet creation may be not completed,
    // so setFlexSdkForAllFacets(..) won't set sdk for this facet
    myFlexSdk = flexSdk;
    myFlexSdkName = flexSdk == null ? "" : flexSdk.getName();
    TargetPlayerUtils.updateTargetPlayerIfMajorOrMinorVersionDiffers(myFlexBuildConfiguration, flexSdk);

    AutogeneratedLibraryUtils.registerSdkRootsListenerIfNeeded(myFlexSdk);
    AutogeneratedLibraryUtils.configureAutogeneratedLibraryIfNeeded(modifiableRootModel, myFlexSdk);

    setFlexSdkForAllFacets(modifiableRootModel.getModule(), flexSdk);
  }

  private static void setFlexSdkForAllFacets(final Module module, final Sdk flexSdk) {
    for (final FlexFacet flexFacet : FacetManager.getInstance(module).getFacetsByType(FlexFacet.ID)) {
      final FlexFacetConfigurationImpl flexFacetConfig = (FlexFacetConfigurationImpl)flexFacet.getConfiguration();
      flexFacetConfig.myFlexSdk = flexSdk;
      flexFacetConfig.myFlexSdkName = flexSdk == null ? "" : flexSdk.getName();
      TargetPlayerUtils.updateTargetPlayerIfMajorOrMinorVersionDiffers(flexFacetConfig.myFlexBuildConfiguration, flexSdk);
    }
  }

  public FlexBuildConfiguration getFlexBuildConfiguration() {
    return myFlexBuildConfiguration;
  }

  @Nullable
  private static Sdk suggestFlexSdkForNewlyCreatedFacet(final Module module) {
    final Collection<FlexFacet> flexFacets = FacetManager.getInstance(module).getFacetsByType(FlexFacet.ID);
    // select Flex SDK like other facets of this module have
    for (final FlexFacet otherFlexFacet : flexFacets) {
      final Sdk otherFlexFacetSdk = otherFlexFacet.getConfiguration().getFlexSdk();
      if (otherFlexFacetSdk != null) {
        return otherFlexFacetSdk;
      }
    }
    // if Flex SDK not found yet then take any from configured in IDEA
    final List<Sdk> flexSdks = FlexSdkUtils.getFlexAndFlexmojosSdks();
    if (!flexSdks.isEmpty()) {
      return flexSdks.get(0);
    }

    return null;
  }

  private class FlexFacetEditorTab extends FacetEditorTab {
    private JPanel myMainPanel;
    private JPanel myExtPanel;
    private JComboBox myFlexSdkComboBox;
    private JButton myConfigureSdksButton;
    private Sdk myLastCommittedFlexSdk;
    private String myLastCommittedTargetPlayerVersion;
    //private FlexCompilerSettingsEditor myCompilerSettingsEditor;
    private final Module myModule;

    private FlexFacetEditorTab(final FacetEditorContext context, final FacetValidatorsManager validatorsManager) {
      myModule = context.getModule();

      if (myFlexSdkName.equals(FLEX_SDK_NOT_YET_SELECTED_FOR_NEW_FACET) && !context.isNewFacet()) {
        myFlexSdkName = "";
      }

      myFlexSdkComboBox.setRenderer(new ListCellRendererWrapper(myFlexSdkComboBox.getRenderer()) {
        @Override
        public void customize(JList list, Object value, int index, boolean selected, boolean hasFocus) {
          if (value instanceof Sdk) {
            setText(((Sdk)value).getName());
            setIcon(((Sdk)value).getSdkType().getIcon());
          }
          else {
            setIcon(PlatformIcons.ERROR_INTRODUCTION_ICON);
          }
        }
      });

      initConfigureSdksButton();

      validatorsManager.registerValidator(new FacetEditorValidator() {
        public ValidationResult check() {
          if (myFlexSdkComboBox.getSelectedItem() instanceof Sdk) {
            return ValidationResult.OK;
          }
          else if (myFlexSdkComboBox.getSelectedItem() instanceof String && myFlexSdkComboBox.getSelectedItem().toString().length() > 0) {
            return new ValidationResult(FlexBundle.message("flex.sdk.not.configured", myFlexSdkComboBox.getSelectedItem().toString()));
          }
          return new ValidationResult(FlexBundle.message("flex.sdk.not.specified"));
        }
      }, myFlexSdkComboBox);

      //myCompilerSettingsEditor = new FlexCompilerSettingsEditor(context.getModule(), (FlexFacet)context.getFacet(),
      //                                                          context.getModifiableRootModel().getModuleExtension(
      //                                                            CompilerModuleExtension.class));
      //myExtPanel.setLayout(new BorderLayout());
      //myExtPanel.add(myCompilerSettingsEditor.createComponent());
    }

    private void initConfigureSdksButton() {
      myConfigureSdksButton.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          final Object selectedItem = myFlexSdkComboBox.getSelectedItem();
          final ProjectStructureConfigurable projectStructureConfigurable = ProjectStructureConfigurable.getInstance(myModule.getProject());
          if (selectedItem instanceof Sdk) {
            projectStructureConfigurable.select((Sdk)selectedItem, true);
          }
          else {
            final Place place = new Place().putPath("category", projectStructureConfigurable.getJdkConfig());
            projectStructureConfigurable.navigateTo(place, true);
          }
        }
      });
    }

    @Nls
    public String getDisplayName() {
      return "Flex !";
    }

    public JComponent createComponent() {
      return myMainPanel;
    }

    public boolean isModified() {
      final Object selectedItem = myFlexSdkComboBox.getSelectedItem();
      if (selectedItem instanceof Sdk) {
        if (myFlexSdk == null || !((Sdk)selectedItem).getName().equals(myFlexSdk.getName())) {
          return true;
        }
      }
      else if (!myFlexSdkName.equals(selectedItem)) {
        return true;
      }

      return false; //myCompilerSettingsEditor.isModified();
    }

    public void apply() {
      //myCompilerSettingsEditor.apply();
      // Flex sdk will be set later in onFacetInitialized()
    }

    public void onFacetInitialized(final @NotNull Facet facet) {
      final Object selectedItem = myFlexSdkComboBox.getSelectedItem();
      // setFlexSdk(..) changes SDK for all facets of this module,
      // so we may call it only for those FlexFacetConfigurations that were changed.
      // It user has changed SDK for several facets, the latest apply wins.
      if (selectedItem != myLastCommittedFlexSdk) {
        if (selectedItem instanceof Sdk) {
          final ModifiableRootModel modifiableRootModel = ModuleRootManager.getInstance(myModule).getModifiableModel();
          setFlexSdk((Sdk)selectedItem, modifiableRootModel);
          modifiableRootModel.commit();

          applyAutogeneratedLibraryAndUpdateUI();
        }
        else {
          myFlexSdk = null;
          myFlexSdkName = selectedItem == null ? "" : selectedItem.toString();
        }
      }

      if (!myLastCommittedTargetPlayerVersion.equals(myFlexBuildConfiguration.TARGET_PLAYER_VERSION)) {
        if (TargetPlayerUtils.needToChangeSdk(myFlexSdk, myFlexBuildConfiguration.TARGET_PLAYER_VERSION)) {
          final Sdk newSdk =
            TargetPlayerUtils.findOrCreateProperSdk(myModule.getProject(), myFlexSdk, myFlexBuildConfiguration.TARGET_PLAYER_VERSION);
          if (newSdk != null) {
            final ModifiableRootModel modifiableRootModel = ModuleRootManager.getInstance(myModule).getModifiableModel();
            setFlexSdk(newSdk, modifiableRootModel);
            modifiableRootModel.commit();

            applyAutogeneratedLibraryAndUpdateUI();

            // the same target player settings for all Flex facets of the module
            for (final FlexBuildConfiguration config : FlexBuildConfiguration.getConfigForFlexModuleOrItsFlexFacets(myModule)) {
              config.TARGET_PLAYER_VERSION = myFlexBuildConfiguration.TARGET_PLAYER_VERSION;
            }
          }
        }
      }
    }

    private void applyAutogeneratedLibraryAndUpdateUI() {
      final ModuleStructureConfigurable moduleStructureConfigurable = ModuleStructureConfigurable.getInstance(myModule.getProject());
      final ModuleEditor moduleEditor = FlexUtils.getModuleEditor(myModule, moduleStructureConfigurable);
      if (moduleEditor != null) {
        try {
          moduleEditor.canApply();
          // apply autogenerated library in Dependencies tab (model)
          moduleEditor.apply();
        }
        catch (ConfigurationException e) {
          LOG.warn(e);
        }
        // Update UI of Dependencies tab (because autogenerated library might have been changed)
        moduleEditor.moduleCountChanged();

        // Update UI for other facets of this module as SDK was changed for all of them. Need to do it later when onFacetInitialized() is called for all facets
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          public void run() {
            moduleStructureConfigurable.getFacetConfigurator().resetEditors();
          }
        }, ModalityState.current(), new Condition() {
          public boolean value(Object o) {
            return myModule.isDisposed() ||
                   FlexUtils.getModuleEditor(myModule, ModuleStructureConfigurable.getInstance(myModule.getProject())) == null;
          }
        });
      }
    }

    public void reset() {
      resetFlexSdkComboBox();
      //myCompilerSettingsEditor.reset();
    }

    private void resetFlexSdkComboBox() {
      Object initiallySelectedItem = myFlexSdk != null ? myFlexSdk : myFlexSdkName;
      myLastCommittedFlexSdk = myFlexSdk;
      myLastCommittedTargetPlayerVersion = myFlexBuildConfiguration.TARGET_PLAYER_VERSION;
      final List<Sdk> flexSdks = FlexSdkUtils.getFlexAndFlexmojosSdks();
      final List<Object> sdksForCombo = new ArrayList<Object>(flexSdks);
      boolean containsSelectedFlexSdk = false;
      if (myFlexSdk != null) {
        for (final Sdk flexSdk : flexSdks) {
          if (flexSdk.getName().equals(myFlexSdk.getName())) {
            initiallySelectedItem = flexSdk;
            containsSelectedFlexSdk = true;
            break;
          }
        }
      }
      if (!containsSelectedFlexSdk) {
        if (myFlexSdkName.equals(FLEX_SDK_NOT_YET_SELECTED_FOR_NEW_FACET)) {
          final Sdk flexSdk = suggestFlexSdkForNewlyCreatedFacet(myModule);
          if (flexSdk != null) {
            initiallySelectedItem = flexSdk;
            if (TargetPlayerUtils.isTargetPlayerApplicable(flexSdk)) {
              myFlexBuildConfiguration.TARGET_PLAYER_VERSION = TargetPlayerUtils.getTargetPlayerVersion(flexSdk);
            }
          }
          else {
            sdksForCombo.add(0, "");
            initiallySelectedItem = "";
          }
        }
        else {
          sdksForCombo.add(0, myFlexSdkName);
        }
      }
      myFlexSdkComboBox.setModel(new DefaultComboBoxModel(ArrayUtil.toObjectArray(sdksForCombo)));
      myFlexSdkComboBox.setSelectedItem(initiallySelectedItem);
    }

    public String getHelpTopic() {
      return "reference.settings.modules.facet.flex.settings";
    }

    public void disposeUIResources() {
      myMainPanel = null;
      myFlexSdkComboBox = null;
      //if (myCompilerSettingsEditor != null) {
      //  myCompilerSettingsEditor.disposeUIResources();
      //  myCompilerSettingsEditor = null;
      //}
    }
  }

}
