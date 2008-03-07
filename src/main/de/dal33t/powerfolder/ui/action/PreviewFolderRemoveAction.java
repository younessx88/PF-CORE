package de.dal33t.powerfolder.ui.action;

import de.dal33t.powerfolder.Controller;
import de.dal33t.powerfolder.disk.Folder;
import de.dal33t.powerfolder.ui.dialog.PreviewFolderRemovePanel;
import de.dal33t.powerfolder.util.ui.SelectionChangeEvent;
import de.dal33t.powerfolder.util.ui.SelectionChangeListener;
import de.dal33t.powerfolder.util.ui.SelectionModel;

import java.awt.event.ActionEvent;

/**
 * Action which acts on selected preview folder. Removes selected folder from PF
 *
 * @author <a href="mailto:sprajc@riege.com">Christian Sprajc</a>
 * @version $Revision: 1.5 $
 */
public class PreviewFolderRemoveAction extends BaseAction {
    // new selection model
    private SelectionModel actionSelectionModel;

    public PreviewFolderRemoveAction(Controller controller, SelectionModel selectionModel) {
        super("preview_folder_remove", controller);
        actionSelectionModel = selectionModel;
        setEnabled(actionSelectionModel.getSelection() != null);

        // Add behavior on selection model
        selectionModel.addSelectionChangeListener(new SelectionChangeListener()
        {
            public void selectionChanged(SelectionChangeEvent event) {
                Object selection = event.getSelection();
                // enable button if there is something selected
                setEnabled(selection != null);
            }
        });
    }

    // Called if leave button clicked
    public void actionPerformed(ActionEvent e) {
        // selected folder
        Folder folder = (Folder) actionSelectionModel.getSelection();
        if (folder != null) {
            // show a confirm dialog
            PreviewFolderRemovePanel flp = new PreviewFolderRemovePanel(this,
                    getController(), folder);
            flp.open();
        }
    }

    /**
     * Called from FolderLeave Panel if the folder leave is confirmed.
     *
     * @param deleteSystemSubFolder whether to delete hte .PowerFolder directory
     */
    public void confirmedFolderLeave(boolean deleteSystemSubFolder) {
        Folder folder = (Folder) actionSelectionModel.getSelection();
        getController().getFolderRepository().removeFolder(folder, deleteSystemSubFolder);
    }
}