package org.ice1000.tt.gradle

import org.ice1000.tt.gradle.json.LangData
import org.intellij.lang.annotations.Language
import java.io.File

fun LangData.service(configName: String, nickname: String, outDir: File) {
	@Language("kotlin")
val service = """
@file:JvmMultifileClass
@file:JvmName("ProjectGenerated")
package $basePackage.project

import com.intellij.openapi.project.Project

${if (generateSettings) if (hasVersion) """
data class ${languageName}Settings(
	override var exePath: String = "$exeName",
	override var version: String = "Unknown"
) : VersionedExecutableSettings
""" else """
data class ${languageName}Settings(var exePath: String = "$exeName")
""" else ""}
val ${configName}Path by lazyExePath("$exeName")

val Project.${configName}Settings: ${languageName}ProjectSettingsService
	get() = ${configName}SettingsNullable ?: ${languageName}ProjectSettingsService()

/**
 * When building plugin searchable options,
 * `ServiceManager.getService(this, ${languageName}ProjectSettingsService::class.java)
 * may return null.
 */
val Project.${configName}SettingsNullable: ${languageName}ProjectSettingsService?
	get() = getComponent(${languageName}ProjectSettingsService::class.java)
"""
	val outProjectDir = outDir.resolve("project")
	outProjectDir.mkdirs()
	outProjectDir.resolve("$nickname-generated.kt").writeText(service)

	val serviceClassName = "${languageName}ProjectSettingsService"
	@Language("JAVA")
	val serviceClassContent = """
package $basePackage.project;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@State(
	name = "${languageName}ProjectSettings",
	storages = @Storage("${languageName.decapitalize()}Config.xml"))
public final class $serviceClassName implements PersistentStateComponent<${languageName}Settings> {
	private final @NotNull ${languageName}Settings settings = new ${languageName}Settings();
	public @NotNull ${languageName}Settings getSettings() { return settings; }

	@Override
	public @Nullable ${languageName}Settings getState() { return XmlSerializerUtil.createCopy(settings); }

	@Override
	public void loadState(@NotNull ${languageName}Settings state) {
		XmlSerializerUtil.copyBean(state, settings);
	}
}"""
	outProjectDir.resolve("$serviceClassName.java").writeText(serviceClassContent)

	if (generateSettings) {
		val settingsClassName = "${languageName}ProjectConfigurable"
		val capitalizedConfigName = configName.capitalize()

		val configureSettings = """
initWebsiteLabel(this);
${if (website.isNotBlank()) """
getWebsiteLabel().setText("$website");
getWebsiteLabel().setIcon(TTIcons.$constantPrefix);
""" else "getWebsiteLabel().setVisible(false);"}
getExePathField().addBrowseFolderListener(
	TTBundle.message("tt.ui.project.select-compiler", ${constantPrefix}_LANGUAGE_NAME),
	TTBundle.message("tt.ui.project.select-compiler.description", ${constantPrefix}_LANGUAGE_NAME),
	project,
	FileChooserDescriptorFactory.createSingleFileOrExecutableAppDescriptor());
getGuessExeButton().addActionListener(e -> {
	String path = get${capitalizedConfigName}Path();
	if (path != null) getExePathField().setText(path);
});
"""

		@Language("JAVA")
		val settingsClassContent = if (hasVersion) """
package $basePackage.project;

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.project.Project;
import icons.TTIcons;
import $basePackage.TTBundle;
import $basePackage.project.ui.VersionedExecutableProjectConfigurableImpl;
import org.jetbrains.annotations.NotNull;

import static $basePackage.ConstantsKt.${constantPrefix}_LANGUAGE_NAME;
import static $basePackage.project.ProjectGenerated.*;
import static $basePackage.project.ui.Ui_implKt.initWebsiteLabel;

public final class $settingsClassName extends VersionedExecutableProjectConfigurableImpl {
	private final ${languageName}Settings settings;

	@Override
	protected @NotNull VersionedExecutableSettings getSettings() {
		return settings;
	}

	@Override
	protected @NotNull String trimVersion(@NotNull String version) { return $trimVersion; }

	public $settingsClassName(@NotNull Project project) {
		settings = get${capitalizedConfigName}Settings(project).getSettings();
		init();
		$configureSettings
	}

	@Override
	public @NotNull String getDisplayName() { return ${constantPrefix}_LANGUAGE_NAME; }
}
""" else """
package $basePackage.project;

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.project.Project;
import icons.TTIcons;
import $basePackage.TTBundle;
import $basePackage.project.ui.OnlyExecutableProjectConfigurable;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

import static $basePackage.ConstantsKt.${constantPrefix}_LANGUAGE_NAME;
import static $basePackage.project.ProjectGenerated.*;
import static $basePackage.project.ui.Ui_implKt.initWebsiteLabel;

public final class $settingsClassName extends OnlyExecutableProjectConfigurable {
	private final ${languageName}Settings settings;

	public $settingsClassName(@NotNull Project project) {
		settings = get${capitalizedConfigName}Settings(project).getSettings();
		getExePathField().setText(settings.getExePath());
		$configureSettings
	}

	@Override
	public boolean isModified() {
		return Objects.equals(getExePathField().getText(), settings.getExePath());
	}

	@Override
	public @NotNull String getDisplayName() { return ${constantPrefix}_LANGUAGE_NAME; }

	@Override
	public void apply() { settings.setExePath(getExePathField().getText()); }
}
"""
		outProjectDir.resolve("$settingsClassName.java").writeText(settingsClassContent)
	}
}
