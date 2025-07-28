package com.lhstack.data

import com.intellij.codeInsight.actions.ReformatCodeProcessor
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.ui.LanguageTextField
import com.lhstack.data.component.MultiLanguageTextField
import com.lhstack.tools.plugins.Helper
import com.lhstack.tools.plugins.IPlugin
import java.awt.Dimension
import java.util.*
import javax.swing.Icon
import javax.swing.JComponent


class PluginImpl : IPlugin {

    companion object {
        val CACHE = mutableMapOf<String, JComponent>()
        val DISPOSERS = mutableMapOf<String, Disposable>()
        val TAB_ACTIONS = mutableMapOf<String, MutableList<AnAction>>()
    }

    override fun pluginIcon(): Icon = Helper.findIcon("pane.svg", PluginImpl::class.java)

    override fun pluginTabIcon(): Icon = Helper.findIcon("tab.svg", PluginImpl::class.java)

    override fun createPanel(project: Project): JComponent {
        val id = UUID.randomUUID().toString()
        return CACHE.computeIfAbsent(id) {
            val disposable = Disposer.newDisposable()
            DISPOSERS[id] = disposable
            val languageTextField = MultiLanguageTextField.dynamic(project, "", disposable)
            val fileTypes = FileTypeManager.getInstance().registeredFileTypes.filterIsInstance<LanguageFileType>()
                .sortedBy { it.name }
            val comboBox = ComboBox(fileTypes.map { it.name }.toTypedArray())
            val json5 = fileTypes.first { it.name.lowercase() == "json5" }
            comboBox.selectedItem = json5.name
            comboBox.preferredSize = Dimension(100, 27)
            comboBox.toolTipText = json5.name
            comboBox.isSwingPopup = true
            comboBox.addItemListener {
                comboBox.toolTipText = it.item.toString()
                languageTextField.changeLanguageFieType(
                    FileTypeManager.getInstance().getStdFileType(it.item.toString()) as LanguageFileType
                )
                reformat(project,languageTextField)
            }
            TAB_ACTIONS[id] = mutableListOf(
                object : AnAction({ "aaa" }), CustomComponentAction {
                    override fun actionPerformed(e: AnActionEvent) {
                    }

                    override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
                        return comboBox
                    }
                },
                object : AnAction({"格式化"},Helper.findIcon("tab.svg",PluginImpl::class.java)) {
                    override fun actionPerformed(e: AnActionEvent) {
                        reformat(project,languageTextField)
                    }
                }
            )
            languageTextField.name = id
            languageTextField
        }
    }


    fun reformat(project:Project,languageTextField: LanguageTextField) {
        ApplicationManager.getApplication().runWriteAction {
            ApplicationManager.getApplication().runWriteAction {
                PsiDocumentManager.getInstance(project).let { psiDocumentManager ->
                    psiDocumentManager.getPsiFile(languageTextField.document)?.let { psiFile ->
                        ReformatCodeProcessor(psiFile,false).let { processor ->
                            processor.setPostRunnable {
                                psiDocumentManager.commitDocument(languageTextField.document)
                            }
                            processor.run()
                        }
                    }
                }
            }
        }
    }

    override fun tabPanelActions(project: Project, pluginPanel: JComponent): MutableList<AnAction>? {
        return TAB_ACTIONS[pluginPanel.name]
    }

    override fun closePanel(project: Project, pluginPanel: JComponent) {
        DISPOSERS.remove(pluginPanel.name)?.let {
            Disposer.dispose(it)
        }
        CACHE.remove(pluginPanel.name)
    }

    override fun support(jToolsVersion: Int): Boolean {
        return jToolsVersion >= 110
    }

    override fun supportMultiOpens(): Boolean {
        return true
    }

    override fun pluginName(): String = "数据格式化"

    override fun pluginDesc(): String = "数据格式化"

    override fun pluginVersion(): String = "0.0.1"
}