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
 * $Id: FoldersTab.java 5495 2008-10-24 04:59:13Z harry $
 */
package de.dal33t.powerfolder.ui.folders;

import com.jgoodies.forms.builder.PanelBuilder;
import com.jgoodies.forms.factories.Borders;
import com.jgoodies.forms.layout.CellConstraints;
import com.jgoodies.forms.layout.FormLayout;
import de.dal33t.powerfolder.Controller;
import de.dal33t.powerfolder.PFUIComponent;
import de.dal33t.powerfolder.PreferencesEntry;
import de.dal33t.powerfolder.ConfigurationEntry;
import de.dal33t.powerfolder.clientserver.ServerClient;
import de.dal33t.powerfolder.clientserver.ServerClientListener;
import de.dal33t.powerfolder.clientserver.ServerClientEvent;
import de.dal33t.powerfolder.ui.FileDropTransferHandler;
import de.dal33t.powerfolder.ui.wizard.PFWizard;
import de.dal33t.powerfolder.ui.widget.ActionLabel;
import de.dal33t.powerfolder.ui.widget.GradientPanel;
import de.dal33t.powerfolder.util.Translation;
import de.dal33t.powerfolder.util.ui.SimpleComponentFactory;
import de.dal33t.powerfolder.util.ui.UIUtil;

import javax.swing.*;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;

/**
 * Class to display the forders tab.
 */
public class FoldersTab extends PFUIComponent {

    private JPanel uiComponent;
    private FoldersList foldersList;
    private JScrollPane scrollPane;
    private JCheckBox autoAcceptCB;
    private JLabel connectingLabel;
    private JLabel notLoggedInLabel;
    private ActionLabel loginActionLabel;
    private JLabel noFoldersFoundLabel;
    private ActionLabel folderWizardActionLabel;
    private ActionLabel newFolderActionLabel;
    private JPanel emptyPanelOuter;
    private ServerClient client;

    /**
     * Constructor
     * 
     * @param controller
     */
    public FoldersTab(Controller controller) {
        super(controller);
        connectingLabel = new JLabel(Translation.getTranslation(
                "folders_tab.connecting"));
        notLoggedInLabel = new JLabel(Translation.getTranslation(
                "folders_tab.not_logged_in"));
        loginActionLabel = new ActionLabel(getController(), new MyLoginAction());
        loginActionLabel.setText(Translation.getTranslation("folders_tab.login"));
        noFoldersFoundLabel = new JLabel(Translation.getTranslation(
                "folders_tab.no_folders_found"));
        foldersList = new FoldersList(getController(), this);
        folderWizardActionLabel = new ActionLabel(getController(), getApplicationModel()
            .getActionModel().getFolderWizardAction());
        folderWizardActionLabel.setText(Translation.getTranslation("folders_tab.folder_wizard"));
        newFolderActionLabel = new ActionLabel(getController(), getApplicationModel()
            .getActionModel().getNewFolderAction());
        newFolderActionLabel.setText(Translation.getTranslation("folders_tab.new_folder"));
        client = getApplicationModel().getServerClientModel().getClient();
        client.addListener(new MyServerClientListener());
    }

    /**
     * Returns the ui component.
     * 
     * @return
     */
    public JPanel getUIComponent() {
        if (uiComponent == null) {
            buildUI();
        }
        uiComponent.setTransferHandler(new FileDropTransferHandler(
            getController()));
        return uiComponent;
    }

    /**
     * Builds the ui component.
     */
    private void buildUI() {

        ActionLabel tellFriendLabel = SimpleComponentFactory
                .createTellAFriendLabel(getController());
        tellFriendLabel.getUIComponent().setOpaque(false);
        tellFriendLabel.getUIComponent().setBorder(
            Borders.createEmptyBorder("3dlu, 6px, 4px, 3dlu"));

        autoAcceptCB = new JCheckBox(Translation.getTranslation(
                "folders_tab.auto_accept.text"));
        autoAcceptCB.setToolTipText(Translation.getTranslation(
                "folders_tab.auto_accept.tip"));
        autoAcceptCB.addActionListener(new MyActionListener());
        autoAcceptCB.setSelected(ConfigurationEntry.AUTO_ACCEPT_INVITE.
                getValueBoolean(getController()));


        // Build ui
        FormLayout layout = new FormLayout("pref:grow",
            "3dlu, pref, 3dlu, pref, 3dlu, fill:0:grow, pref");
        PanelBuilder builder = new PanelBuilder(layout);
        CellConstraints cc = new CellConstraints();

        JPanel toolbar = createToolBar();
        builder.add(toolbar, cc.xy(1, 2));
        builder.addSeparator(null, cc.xy(1, 4));
        scrollPane = new JScrollPane(foldersList.getUIComponent());
        scrollPane.getVerticalScrollBar().setUnitIncrement(10);
        foldersList.setScroller(scrollPane);
        UIUtil.removeBorder(scrollPane);

        // emptyLabel and scrollPane occupy the same slot.
        buildEmptyPanel();
        builder.add(emptyPanelOuter, cc.xywh(1, 6, 1, 1));
        builder.add(scrollPane, cc.xywh(1, 6, 1, 1));

        if (PreferencesEntry.SHOW_TELL_A_FRIEND
            .getValueBoolean(getController()))
        {
            builder.add(tellFriendLabel.getUIComponent(), cc.xy(1, 7));
        }

        uiComponent = GradientPanel.create(builder.getPanel());

        updateEmptyLabel();

    }

    private void buildEmptyPanel() {
        FormLayout layoutOuter = new FormLayout("center:pref:grow", "center:pref:grow");
        PanelBuilder builderOuter = new PanelBuilder(layoutOuter);
        FormLayout layoutInner = new FormLayout("fill:pref:grow, 3dlu, fill:pref:grow, 3dlu, fill:pref:grow",
                "pref");
        PanelBuilder builderInner = new PanelBuilder(layoutInner);
        CellConstraints cc = new CellConstraints();
        builderInner.add(connectingLabel, cc.xy(1, 1));
        builderInner.add(notLoggedInLabel, cc.xy(1, 1));
        builderInner.add(loginActionLabel.getUIComponent(), cc.xy(3, 1));
        builderInner.add(noFoldersFoundLabel, cc.xy(1, 1));
        builderInner.add(folderWizardActionLabel.getUIComponent(), cc.xy(3, 1));
        builderInner.add(newFolderActionLabel.getUIComponent(), cc.xy(5, 1));
        JPanel emptyPanelInner = builderInner.getPanel();
        builderOuter.add(emptyPanelInner, cc.xy(1, 1));
        emptyPanelOuter = builderOuter.getPanel();
    }

    public void updateEmptyLabel() {
        if (foldersList != null) {
            if (emptyPanelOuter != null) {
                if (foldersList.isEmpty()) {
                    String username = client.getUsername();
                    if (!client.isConnected()) {
                        connectingLabel.setVisible(true);
                        notLoggedInLabel.setVisible(false);
                        loginActionLabel.setVisible(false);
                        noFoldersFoundLabel.setVisible(false);
                        folderWizardActionLabel.setVisible(false);
                        newFolderActionLabel.setVisible(false);
                    } else if (username == null ||
                            username.trim().length() == 0 ||
                            client.isPasswordEmpty() ||
                            !client.isLoggedIn()) {
                        connectingLabel.setVisible(false);
                        notLoggedInLabel.setVisible(true);
                        loginActionLabel.setVisible(true);
                        noFoldersFoundLabel.setVisible(false);
                        folderWizardActionLabel.setVisible(false);
                        newFolderActionLabel.setVisible(false);
                    } else {
                        connectingLabel.setVisible(false);
                        notLoggedInLabel.setVisible(false);
                        loginActionLabel.setVisible(false);
                        noFoldersFoundLabel.setVisible(true);
                        folderWizardActionLabel.setVisible(true);
                        newFolderActionLabel.setVisible(true);
                    }
                }
                emptyPanelOuter.setVisible(foldersList.isEmpty());
            }
            if (scrollPane != null) {
                scrollPane.setVisible(!foldersList.isEmpty());
            }
        }
    }

    /**
     * @return the toolbar
     */
    private JPanel createToolBar() {
        JButton folderWizardButton = new JButton(getApplicationModel()
            .getActionModel().getFolderWizardAction());
        JButton newFolderButton = new JButton(getApplicationModel()
            .getActionModel().getNewFolderAction());

        // Same width of the buttons please
        JButton searchComputerButton = new JButton(getApplicationModel()
            .getActionModel().getFindComputersAction());
        folderWizardButton.setMinimumSize(searchComputerButton.getMinimumSize());
        folderWizardButton.setMaximumSize(searchComputerButton.getMaximumSize());
        folderWizardButton.setPreferredSize(searchComputerButton
            .getPreferredSize());
        newFolderButton.setMinimumSize(searchComputerButton.getMinimumSize());
        newFolderButton.setMaximumSize(searchComputerButton.getMaximumSize());
        newFolderButton.setPreferredSize(searchComputerButton
            .getPreferredSize());
        searchComputerButton.setVisible(false);

        FormLayout layout = new FormLayout("3dlu, pref, 3dlu, pref, 3dlu:grow, pref, 3dlu",
            "pref");
        PanelBuilder builder = new PanelBuilder(layout);
        CellConstraints cc = new CellConstraints();

        builder.add(folderWizardButton, cc.xy(2, 1));
        builder.add(newFolderButton, cc.xy(4, 1));
        builder.add(autoAcceptCB, cc.xy(6, 1));
        return builder.getPanel();
    }

    /**
     * Populates the folders in the list.
     */
    public void populate() {
        foldersList.populate();
    }

    private void configureAutoAccept() {
        ConfigurationEntry.AUTO_ACCEPT_INVITE.setValue(getController(),
                autoAcceptCB.isSelected());
        getController().saveConfig();
    }

    // ////////////////
    // Inner classes //
    // ////////////////

    /**
     * Action listener for type list.
     */
    private class MyActionListener implements ActionListener {
        public void actionPerformed(ActionEvent e) {
            if (e.getSource().equals(autoAcceptCB)) {
                configureAutoAccept();
            }
        }
    }

    private class MyLoginAction extends AbstractAction {
        public void actionPerformed(ActionEvent e) {
            PFWizard.openLoginWizard(getController(), getController()
                .getOSClient());
        }
    }

    private class MyServerClientListener implements ServerClientListener {
        public void accountUpdated(ServerClientEvent event) {
            updateEmptyLabel();
        }

        public boolean fireInEventDispatchThread() {
            return true;
        }

        public void login(ServerClientEvent event) {
            updateEmptyLabel();
        }

        public void nodeServerStatusChanged(ServerClientEvent event) {
            updateEmptyLabel();
        }

        public void serverConnected(ServerClientEvent event) {
            updateEmptyLabel();
        }

        public void serverDisconnected(ServerClientEvent event) {
            updateEmptyLabel();
        }
    }

}
