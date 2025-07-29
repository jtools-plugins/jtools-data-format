package com.lhstack.data

import com.intellij.codeInsight.actions.ReformatCodeProcessor
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ToggleAction
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
import com.intellij.ui.LanguageTextField
import com.lhstack.data.component.MultiLanguageTextField
import com.lhstack.tools.plugins.Helper
import com.lhstack.tools.plugins.IPlugin
import org.ehcache.PersistentCacheManager
import org.ehcache.config.builders.CacheConfigurationBuilder
import org.ehcache.config.builders.CacheManagerBuilder
import org.ehcache.config.builders.ResourcePoolsBuilder
import org.ehcache.config.units.EntryUnit
import org.ehcache.config.units.MemoryUnit
import org.jdesktop.swingx.VerticalLayout
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.util.*
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel


class PluginImpl : IPlugin {

    companion object {
        val CACHE = mutableMapOf<String, JComponent>()
        val DISPOSERS = mutableMapOf<String, Disposable>()
        val TAB_ACTIONS = mutableMapOf<String, MutableList<AnAction>>()
        val CARD_LAYOUTS = mutableMapOf<String, CardLayout>()
        val CARD_LAYOUT_VALUES = mutableMapOf<String, String>()
        const val LAYOUT_MAIN = "MAIN"
        const val LAYOUT_SETTING =  "SETTING"
        var persistentCacheManager: PersistentCacheManager = CacheManagerBuilder.newCacheManagerBuilder()
            .with(CacheManagerBuilder.persistence("${System.getProperty("user.home")}/.jtools/jtools-data-format/data"))
            .withCache(
                "global",
                CacheConfigurationBuilder.newCacheConfigurationBuilder(
                    String::class.java, String::class.java,
                    ResourcePoolsBuilder.newResourcePoolsBuilder()
                        .heap(10, EntryUnit.ENTRIES)
                        .offheap(1, MemoryUnit.MB)
                        .disk(20, MemoryUnit.MB, true)
                )
            ).build(true)
        var cache = persistentCacheManager.getCache("global",String::class.java,String::class.java)
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
            languageTextField.addDocumentListener(object:DocumentListener{
                override fun documentChanged(event: DocumentEvent) {
                    if(cache.get("auto") == "true"){
                        reformat(project,languageTextField)
                    }
                }
            })
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
                object : ToggleAction({"设置"},Helper.findIcon("setting.svg",PluginImpl::class.java)) {

                    override fun isSelected(e: AnActionEvent): Boolean = CARD_LAYOUT_VALUES[id] == LAYOUT_SETTING

                    override fun setSelected(e: AnActionEvent, state: Boolean) {
                        if(CARD_LAYOUT_VALUES[id] != LAYOUT_SETTING) {
                            CARD_LAYOUT_VALUES[id] = LAYOUT_SETTING
                            CARD_LAYOUTS[id]?.show(CACHE[id], LAYOUT_SETTING)
                        }else {
                            CARD_LAYOUT_VALUES[id] = LAYOUT_MAIN
                            CARD_LAYOUTS[id]?.show(CACHE[id], LAYOUT_MAIN)
                        }
                    }

                },
                object : AnAction({ "aaa" }), CustomComponentAction {
                    override fun update(e: AnActionEvent) {
                        super.update(e)
                        e.presentation.isEnabled = CARD_LAYOUT_VALUES[id] != LAYOUT_SETTING
                    }
                    override fun actionPerformed(e: AnActionEvent) {
                    }

                    override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
                        return comboBox
                    }

                    override fun updateCustomComponent(component: JComponent, presentation: Presentation) {
                        component.isEnabled = presentation.isEnabled
                    }
                },
                object : AnAction({"格式化"},Helper.findIcon("tab.svg",PluginImpl::class.java)) {
                    override fun update(e: AnActionEvent) {
                        super.update(e)
                        e.presentation.isEnabled = CARD_LAYOUT_VALUES[id] != LAYOUT_SETTING
                    }
                    override fun actionPerformed(e: AnActionEvent) {
                        reformat(project,languageTextField)
                    }
                },
            )
            val cardLayout = CardLayout()
            CARD_LAYOUTS[id] = cardLayout
            CARD_LAYOUT_VALUES[id] = LAYOUT_MAIN
            JPanel(cardLayout).also {
                it.name = id
                it.add(languageTextField, LAYOUT_MAIN)
                it.add(JPanel().also { panel ->
                    panel.layout = VerticalLayout()
                    panel.add(JPanel(BorderLayout()).also { p ->
                        p.add(JLabel("自动格式化: ",JLabel.RIGHT),BorderLayout.WEST)
                        ActionManager.getInstance().createActionToolbar("jtools-data-format@autoFormat",DefaultActionGroup().also { group ->
                            group.add(object:ToggleAction({"自动格式化"},Helper.findIcon("setting.svg",PluginImpl::class.java)){
                                override fun isSelected(e: AnActionEvent): Boolean = cache.get("auto") == "true"

                                override fun setSelected(e: AnActionEvent, state: Boolean) {
                                    if(cache.get("auto") == "true"){
                                        cache.put("auto", "false")
                                    }else {
                                        cache.put("auto", "true")
                                    }
                                }

                            })
                        },true).also { toolbar ->
                            toolbar.targetComponent = p
                            p.add(toolbar.component)
                        }
                    },BorderLayout.CENTER)
                }, LAYOUT_SETTING)
                cardLayout.show(it,LAYOUT_MAIN)
            }
        }
    }


    override fun appClose() {
        persistentCacheManager.close()
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
        CARD_LAYOUTS.remove(pluginPanel.name)
        CARD_LAYOUT_VALUES.remove(pluginPanel.name)
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