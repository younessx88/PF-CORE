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
package de.dal33t.powerfolder.ui.homeold;

import com.jgoodies.binding.value.ValueModel;
import de.dal33t.powerfolder.Controller;
import de.dal33t.powerfolder.ui.Icons;
import de.dal33t.powerfolder.ui.navigation.RootNode;
import de.dal33t.powerfolder.ui.render.UnsortedTableHeaderRenderer;
import de.dal33t.powerfolder.util.Translation;
import de.dal33t.powerfolder.util.ui.UIUtil;

import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableModel;
import javax.swing.tree.TreeNode;
import java.awt.Component;
import java.awt.Dimension;

/**
 * Maps the root items to a table.
 * 
 * @author <A HREF="mailto:schaatser@powerfolder.com">Jan van Oosterom</A>
 * @version $Revision: 1.4 $
 */
public class RootTable extends JTable {

    private final Controller controller;
    private final ValueModel allUploadsCountVM;
    private final ValueModel activeUploadsCountVM;
    private final ValueModel allDownloadsCountVM;
    private final ValueModel activeDownloadsCountVM;

    public RootTable(TableModel tableModel, Controller controller) {
        super(tableModel);
        this.controller = controller;
        setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        setRowHeight(Icons.NODE_FRIEND_CONNECTED.getIconHeight() + 10);
        setShowGrid(false);
        setupColumns();
        setDefaultRenderer(Object.class, new RootTableRenderer());

        // Associate a header renderer with all columns.
        UnsortedTableHeaderRenderer.associateHeaderRenderer(getColumnModel());

        allUploadsCountVM = controller.getUIController().getApplicationModel()
                .getTransferManagerModel().getAllUploadsCountVM();
        activeUploadsCountVM = controller.getUIController().getApplicationModel()
                .getTransferManagerModel().getActiveUploadsCountVM();
        allDownloadsCountVM = controller.getUIController().getApplicationModel()
                .getTransferManagerModel().getAllDownloadsCountVM();
        activeDownloadsCountVM = controller.getUIController().getApplicationModel()
                .getTransferManagerModel().getActiveDownloadsCountVM();
    }

    private void setupColumns() {
        int totalWidth = getWidth();
        // otherwise the table header may not be visible:
        getTableHeader().setPreferredSize(new Dimension(totalWidth, 20));
        getTableHeader().setReorderingAllowed(true);
        TableColumn column = getColumn(getColumnName(0));
        column.setPreferredWidth(400);
        column = getColumn(getColumnName(1));
        column.setPreferredWidth(40);
    }

    private class RootTableRenderer extends DefaultTableCellRenderer {

        public Component getTableCellRendererComponent(JTable table,
            Object value, boolean isSelected, boolean hasFocus, int row,
            int column)
        {
            int columnInModel = UIUtil.toModel(table, column);
            String newValue = "";
            TreeNode node = (TreeNode) value;
            Object userObject = UIUtil.getUserObject(value);
            if (columnInModel == 0) { // name
                if (value == controller.getUIController().getApplicationModel()
                    .getFolderRepositoryModel().getMyFoldersTreeNode())
                {
                    setIcon(Icons.FOLDERS);
                    newValue = Translation.getTranslation("title.my.folders");
                } else if (userObject == RootNode.DOWNLOADS_NODE_LABEL) {
                    newValue = Translation.getTranslation("general.downloads");
                    if (activeDownloadsCountVM.getValue() != null &&
                            (Integer) activeDownloadsCountVM.getValue() > 0) {
                        setIcon(Icons.DOWNLOAD_ACTIVE);
                    } else {
                        setIcon(Icons.DOWNLOAD);
                    }
                } else if (userObject == RootNode.UPLOADS_NODE_LABEL) {
                    newValue = Translation.getTranslation("general.uploads");
                    if (activeUploadsCountVM.getValue() != null &&
                            (Integer) activeUploadsCountVM.getValue() > 0) {
                        setIcon(Icons.UPLOAD_ACTIVE);
                    } else {
                        setIcon(Icons.UPLOAD);
                    }
                } else if (userObject == RootNode.RECYCLEBIN_NODE_LABEL) {
                    newValue = Translation.getTranslation("general.recycle_bin");
                    setIcon(Icons.RECYCLE_BIN);
                } else if (userObject == RootNode.WEBSERVICE_NODE_LABEL) {
                    newValue = controller.getOSClient().getServer().getNick();
                    //newValue = Translation.getTranslation("general.webservice");
                    setIcon(Icons.WEBSERVICE);
                } else if (userObject == RootNode.DIALOG_TESTING_NODE_LABEL) {
                    newValue = "Dialog Testing";
                    setIcon(Icons.DIALOG_TESTING);
                } else if (userObject == RootNode.DEBUG_NODE_LABEL) {
                    newValue = "Debug";
                    setIcon(Icons.DEBUG);
                } else if (controller.isVerbose()
                    && value == controller.getUIController().getApplicationModel()
                        .getNodeManagerModel().getConnectedTreeNode())
                {
                    newValue = Translation
                        .getTranslation("root_panel.connected_users");
                    setIcon(Icons.KNOWN_NODES);
                } else if (value == controller.getUIController().getApplicationModel()
                    .getNodeManagerModel().getFriendsTreeNode())
                {
                    newValue = Translation.getTranslation("root_panel.friends");
                    setIcon(Icons.NODE_FRIEND_CONNECTED);
                }
            } else {// size
                setIcon(null);
                if (userObject == RootNode.DOWNLOADS_NODE_LABEL) {
                    newValue = activeDownloadsCountVM.getValue() == null ?
                            "0" : String.valueOf(allDownloadsCountVM.getValue());
                } else if (userObject == RootNode.UPLOADS_NODE_LABEL) {
                    newValue = activeUploadsCountVM.getValue() == null ?
                            "0" : String.valueOf(allUploadsCountVM.getValue());
                } else if (userObject == RootNode.RECYCLEBIN_NODE_LABEL) {
                    newValue = String.valueOf(controller.getRecycleBin()
                            .countAllRecycledFiles());
                } else {
                    newValue = String.valueOf(node.getChildCount());
                }
            }
            return super.getTableCellRendererComponent(table, newValue,
                isSelected, hasFocus, row, column);
        }
    }
}
