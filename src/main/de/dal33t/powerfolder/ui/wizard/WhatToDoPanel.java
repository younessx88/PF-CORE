/*
 * Copyright 2004 - 2008 Christian Sprajc. All rights reserved.
 *
 * This file is part of PowerFolder.
 *
 * PowerFolder is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation.
 *
 * PowerFolder is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with PowerFolder. If not, see <http://www.gnu.org/licenses/>.
 *
 * $Id$
 */
package de.dal33t.powerfolder.ui.wizard;

import com.jgoodies.binding.value.ValueHolder;
import com.jgoodies.binding.value.ValueModel;
import com.jgoodies.forms.builder.PanelBuilder;
import com.jgoodies.forms.layout.CellConstraints;
import com.jgoodies.forms.layout.FormLayout;
import de.dal33t.powerfolder.Controller;
import de.dal33t.powerfolder.disk.SyncProfile;
import de.dal33t.powerfolder.ui.Icons;
import de.dal33t.powerfolder.ui.widget.ActionLabel;
import de.dal33t.powerfolder.ui.widget.LinkLabel;
import static de.dal33t.powerfolder.ui.wizard.WizardContextAttributes.*;
import de.dal33t.powerfolder.util.Help;
import de.dal33t.powerfolder.util.Translation;
import de.dal33t.powerfolder.util.ui.SimpleComponentFactory;
import jwf.Wizard;
import jwf.WizardPanel;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.List;

/**
 * The start panel of the "what to do" wizard line
 * 
 * @author <a href="mailto:totmacher@powerfolder.com">Christian Sprajc </a>
 * @version $Revision: 1.13 $
 */
public class WhatToDoPanel extends PFWizardPanel {

    static final int PICTO_FONT_SIZE = 6;

    // The options of this screen
    private static final Object synchronizedOption = new Object();
    private static final Object backupOption = new Object();
    private static final Object hostOption = new Object();
    private static final Object customOption = new Object();
    private static final Object inviteOption = new Object();

    private ActionLabel synchronizedLink;
    private ActionLabel backupLink;
    private ActionLabel hostLink;
    private ActionLabel customLink;
    private ActionLabel inviteLink;
    private LinkLabel documentationLink;
    private ValueModel decision;

    public WhatToDoPanel(Controller controller) {
        super(controller);
    }

    public boolean hasNext() {
        return decision.getValue() != null;
    }

    public boolean validateNext(List<String> errors) {
        return true;
    }

    protected JPanel buildContent() {

        FormLayout layout = new FormLayout("pref",
            "pref, 6dlu, pref, 6dlu, pref, 6dlu, pref, 30dlu, pref, 6dlu, pref");

        PanelBuilder builder = new PanelBuilder(layout);
        CellConstraints cc = new CellConstraints();

        builder.add(synchronizedLink.getUIComponent(), cc.xy(1, 1));
        builder.add(backupLink.getUIComponent(), cc.xy(1, 3));
        builder.add(hostLink.getUIComponent(), cc.xy(1, 5));
        builder.add(customLink.getUIComponent(), cc.xy(1, 7));
        builder.add(inviteLink.getUIComponent(), cc.xy(1, 9));
        builder.add(documentationLink.getUiComponent(), cc.xy(1, 11));
        return builder.getPanel();
    }

    public WizardPanel next() {

        Object option = decision.getValue();

        if (option == synchronizedOption) {
            return doSyncOption();
        } else if (option == backupOption) {
            return doBackupOption();
        } else if (option == hostOption) {
            return doHostOption();
        } else if (option == customOption) {
            return doCustomAction();
        } else if (option == inviteOption) {
            return doInviteOption();
        }

        return null;
    }

    private WizardPanel doInviteOption() {
        getWizardContext().setAttribute(PFWizard.PICTO_ICON,
                Icons.FILE_SHARING_PICTO);

        // Reset folderinfo for disk location
        getWizardContext().setAttribute(FOLDERINFO_ATTRIBUTE, null);

        // Setup choose disk location panel
        getWizardContext().setAttribute(
                PROMPT_TEXT_ATTRIBUTE, Translation
                    .getTranslation("wizard.what_to_do.invite.select_local"));

        // Setup sucess panel of this wizard path
        TextPanelPanel successPanel = new TextPanelPanel(getController(),
            Translation.getTranslation("wizard.setup_success"), Translation
                .getTranslation("wizard.success_join"));
        getWizardContext().setAttribute(PFWizard.SUCCESS_PANEL,
                successPanel);
        return new LoadInvitationPanel(getController());
    }

    private WizardPanel doCustomAction() {
        getWizardContext().setAttribute(PFWizard.PICTO_ICON,
                Icons.PROJECT_WORK_PICTO);

        // Reset folderinfo for disk location
        getWizardContext().setAttribute(FOLDERINFO_ATTRIBUTE, null);

        // Setup choose disk location panel
        getWizardContext().setAttribute(PROMPT_TEXT_ATTRIBUTE,
                Translation.getTranslation("wizard.choose_multi_disk_location.select_additional"));

        // Prompt for send invitation afterwards
        getWizardContext().setAttribute(SEND_INVIATION_AFTER_ATTRIBUTE, true);

        // Select backup by OS
        getWizardContext().setAttribute(BACKUP_ONLINE_STOARGE, true);

        // Setup success panel of this wizard path
        TextPanelPanel successPanel = new TextPanelPanel(
            getController(),
            Translation.getTranslation("wizard.setup_success"),
            Translation
                .getTranslation("wizard.project_name.folder_project_success")
                + Translation.getTranslation("wizard.what_to_do.pcs_join"));
        getWizardContext().setAttribute(PFWizard.SUCCESS_PANEL,
                successPanel);

        MultiFolderSetupPanel setupPanel = new MultiFolderSetupPanel(getController());
        return new ChooseMultiDiskLocationPanel(getController(),
            setupPanel);
    }

    private WizardPanel doHostOption() {
        getWizardContext().setAttribute(PFWizard.PICTO_ICON,
                Icons.PROJECT_WORK_PICTO);

        // Reset folderinfo for disk location
        getWizardContext().setAttribute(FOLDERINFO_ATTRIBUTE, null);

        // This is hosting (manual download) profile!
        getWizardContext().setAttribute(SYNC_PROFILE_ATTRIBUTE,
                SyncProfile.HOST_FILES);

        // Setup choose disk location panel
        getWizardContext().setAttribute(PROMPT_TEXT_ATTRIBUTE,
                Translation.getTranslation("wizard.what_to_do.host_pcs.select"));

        // Prompt for send invitation afterwards
        getWizardContext().setAttribute(SEND_INVIATION_AFTER_ATTRIBUTE, true);

        // Select backup by OS
        getWizardContext().setAttribute(BACKUP_ONLINE_STOARGE, true);

        // Setup sucess panel of this wizard path
        TextPanelPanel successPanel = new TextPanelPanel(getController(),
            Translation.getTranslation("wizard.setup_success"), Translation
                .getTranslation("wizard.what_to_do.folder_host_success")
                + Translation.getTranslation("wizard.what_to_do.host_pcs_join"));
        getWizardContext().setAttribute(PFWizard.SUCCESS_PANEL,
                successPanel);

        FolderCreatePanel createPanel = new FolderCreatePanel(
            getController());

        getWizardContext().setAttribute(SAVE_INVITE_LOCALLY,
                Boolean.TRUE);

        return new ChooseMultiDiskLocationPanel(getController(),
            createPanel);
    }

    private WizardPanel doBackupOption() {
        getWizardContext().setAttribute(PFWizard.PICTO_ICON,
                Icons.SYNC_PCS_PICTO);

        // Reset folderinfo for disk location
        getWizardContext().setAttribute(FOLDERINFO_ATTRIBUTE, null);

        // This is backup (source) profile!
        getWizardContext().setAttribute(SYNC_PROFILE_ATTRIBUTE,
                SyncProfile.BACKUP_SOURCE);

        // Setup choose disk location panel
        getWizardContext().setAttribute(PROMPT_TEXT_ATTRIBUTE,
                Translation.getTranslation("wizard.what_to_do.backp.select"));

        // Prompt for send invitation afterwards
        getWizardContext().setAttribute(SEND_INVIATION_AFTER_ATTRIBUTE, true);

        // Select backup by OS
        getWizardContext().setAttribute(BACKUP_ONLINE_STOARGE,
                true);

        // Setup sucess panel of this wizard path
        TextPanelPanel successPanel = new TextPanelPanel(
            getController(),
            Translation.getTranslation("wizard.setup_success"),
            Translation
                .getTranslation("wizard.what_to_do.folder_backup_success")
                + Translation.getTranslation("wizard.what_to_do.pcs_join"));
        getWizardContext().setAttribute(PFWizard.SUCCESS_PANEL,
                successPanel);

        getWizardContext().setAttribute(SAVE_INVITE_LOCALLY, true);

        FolderCreatePanel createPanel = new FolderCreatePanel(
            getController());

        return new ChooseMultiDiskLocationPanel(getController(),
            createPanel);
    }

    private WizardPanel doSyncOption() {
        getWizardContext().setAttribute(PFWizard.PICTO_ICON,
                Icons.SYNC_PCS_PICTO);

        // Reset folderinfo for disk location
        getWizardContext().setAttribute(FOLDERINFO_ATTRIBUTE, null);

        // This is sync pcs (mirror) profile!
        getWizardContext().setAttribute(SYNC_PROFILE_ATTRIBUTE,
                SyncProfile.AUTOMATIC_SYNCHRONIZATION);

        // Prompt for send invitation afterwards
        getWizardContext().setAttribute(SEND_INVIATION_AFTER_ATTRIBUTE,
                true);

        // Select backup by OS
        getWizardContext().setAttribute(BACKUP_ONLINE_STOARGE, true);

        // Setup choose disk location panel
        getWizardContext().setAttribute(PROMPT_TEXT_ATTRIBUTE,
                Translation.getTranslation("wizard.what_to_do.sync_pcs.select"));

        // Setup sucess panel of this wizard path
        TextPanelPanel successPanel = new TextPanelPanel(getController(),
            Translation.getTranslation("wizard.setup_success"), Translation
                .getTranslation("wizard.what_to_do.sync_pcs.success")
                + Translation.getTranslation("wizard.what_to_do.sync_pcs.pcs_join"));
        getWizardContext().setAttribute(PFWizard.SUCCESS_PANEL, successPanel);

        FolderCreatePanel createPanel = new FolderCreatePanel(getController());

        getWizardContext().setAttribute(SAVE_INVITE_LOCALLY, true);

        return new ChooseMultiDiskLocationPanel(getController(),
            createPanel);
    }

    /**
     * Initalizes all nessesary components
     */
    public void initComponents() {
        decision = new ValueHolder();

        // Behavior
        decision.addValueChangeListener(new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent evt) {
                updateButtons();
            }
        });

        synchronizedLink = new ActionLabel(getController(), new WhatToDoAction(Translation
            .getTranslation("wizard.what_to_do.synchronized_folder"),
            synchronizedOption, decision));
        synchronizedLink.setToolTipText(Translation.getTranslation(
                "wizard.what_to_do.synchronized_folder.tip"));
        SimpleComponentFactory.setFontSize((JLabel) synchronizedLink.getUIComponent(),
            PFWizard.MED_FONT_SIZE);

        backupLink = new ActionLabel(getController(), new WhatToDoAction(Translation
            .getTranslation("wizard.what_to_do.backup_folder"), backupOption,
            decision));
        backupLink.setToolTipText(Translation.getTranslation(
                "wizard.what_to_do.backup_folder.tip"));
        SimpleComponentFactory.setFontSize((JLabel) backupLink.getUIComponent(),
                PFWizard.MED_FONT_SIZE);

        hostLink = new ActionLabel(getController(), new WhatToDoAction(Translation
            .getTranslation("wizard.what_to_do.host_work"), hostOption, decision));
        hostLink.setToolTipText(Translation.getTranslation(
                "wizard.what_to_do.host_work.tip"));
        SimpleComponentFactory.setFontSize((JLabel) hostLink.getUIComponent(),
                PFWizard.MED_FONT_SIZE);

        customLink = new ActionLabel(getController(), new WhatToDoAction(Translation
            .getTranslation("wizard.what_to_do.custom_sync"), customOption,
            decision));
        customLink.setToolTipText(Translation.getTranslation(
                "wizard.what_to_do.custom_sync.tip"));
        SimpleComponentFactory.setFontSize((JLabel) customLink.getUIComponent(),
                PFWizard.MED_FONT_SIZE);

        inviteLink = new ActionLabel(getController(), new WhatToDoAction(Translation
            .getTranslation("wizard.what_to_do.load_invite"), inviteOption,
            decision));
        inviteLink.setToolTipText(Translation.getTranslation(
                "wizard.what_to_do.load_invite.tip"));
        SimpleComponentFactory.setFontSize((JLabel) inviteLink.getUIComponent(),
                PFWizard.MED_FONT_SIZE);

        documentationLink = Help.createQuickstartGuideLabel(getController(),
            Translation.getTranslation("wizard.what_to_do.open_online_documentation"));
        documentationLink.setToolTipText(Translation.getTranslation(
                "wizard.what_to_do.open_online_documentation.tip"));
        SimpleComponentFactory.setFontSize((JLabel) documentationLink.getUiComponent(),
            PFWizard.MED_FONT_SIZE);
    }

    protected JComponent getPictoComponent() {
        return new JLabel(Icons.FILE_SHARING_PICTO);
    }

    protected String getTitle() {
        return Translation.getTranslation("wizard.what_to_do.title");
    }

    private class WhatToDoAction extends AbstractAction {

        private ValueModel model;
        private Object option;

        private WhatToDoAction(String name, Object option, ValueModel model) {
            this.model = model;
            this.option = option;
            putValue(NAME, name);
        }

        public void actionPerformed(ActionEvent e) {
            model.setValue(option);
            Wizard wizard = (Wizard) getWizardContext().getAttribute(
                Wizard.WIZARD_ATTRIBUTE);
            wizard.next();
        }
    }
}