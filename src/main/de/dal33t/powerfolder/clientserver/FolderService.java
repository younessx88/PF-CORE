package de.dal33t.powerfolder.clientserver;

import de.dal33t.powerfolder.disk.FolderException;
import de.dal33t.powerfolder.disk.SyncProfile;
import de.dal33t.powerfolder.light.FolderInfo;

/**
 * Access/Control over folders of a server.
 * 
 * @author <a href="mailto:sprajc@riege.com">Christian Sprajc</a>
 * @version $Revision: 1.5 $
 */
public interface FolderService {
    static final String SERVICE_ID = "folderservice.id";

    /**
     * Creates a new folder to be mirrored by the server. Default Sync
     * 
     * @param foInfo
     * @param profile
     * @throws FolderException
     */
    void createFolder(FolderInfo foInfo, SyncProfile profile)
        throws FolderException;

    /**
     * Removes a folder from the server. Required admin permission on the
     * folder.
     * 
     * @param foInfo
     * @param deleteFiles
     *            true to delete all file contained in the folder.
     */
    void removeFolder(FolderInfo foInfo, boolean deleteFiles);

    /**
     * Changes the sync profile on the remote server for this folder.
     * 
     * @param foInfo
     * @param profile
     */
    void setSyncProfile(FolderInfo foInfo, SyncProfile profile);

    /**
     * Grants the currently logged in user access to folder. the folder is NOT
     * setup on the remote server.
     * 
     * @param foInfos
     * @see #createFolder(FolderInfo, SyncProfile)
     */
    void grantAdmin(FolderInfo... foInfos);
}
