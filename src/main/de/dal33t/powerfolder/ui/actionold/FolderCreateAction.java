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
package de.dal33t.powerfolder.ui.actionold;

import java.awt.event.ActionEvent;

import de.dal33t.powerfolder.Controller;
import de.dal33t.powerfolder.ui.wizard.ChooseDiskLocationPanel;
import de.dal33t.powerfolder.ui.wizard.FolderSetupPanel;
import de.dal33t.powerfolder.ui.wizard.PFWizard;
import de.dal33t.powerfolder.ui.Icons;
import de.dal33t.powerfolder.ui.action.BaseAction;

/**
 * Shares a folder action
 * 
 * @author <a href="mailto:totmacher@powerfolder.com">Christian Sprajc </a>
 * @version $Revision: 1.6 $
 */
public class FolderCreateAction extends BaseAction {
    public FolderCreateAction(Controller controller) {
        super("folder_create", controller);
    }

    public void actionPerformed(ActionEvent e) {
        FolderSetupPanel setupPanel = new FolderSetupPanel(getController());
        ChooseDiskLocationPanel panel = new ChooseDiskLocationPanel(
            getController(), null, setupPanel);
        PFWizard wizard = new PFWizard(getController());
        wizard.getWizardContext().setAttribute(PFWizard.PICTO_ICON,
            Icons.FILESHARING_PICTO);
        wizard.open(panel);
    }
}