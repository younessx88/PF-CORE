/* $Id: Folder.java,v 1.114 2006/04/30 14:17:45 totmacherr Exp $
 */
package de.dal33t.powerfolder.disk;

import java.io.*;
import java.util.*;

import javax.swing.tree.MutableTreeNode;

import de.dal33t.powerfolder.Controller;
import de.dal33t.powerfolder.Member;
import de.dal33t.powerfolder.PFComponent;
import de.dal33t.powerfolder.event.*;
import de.dal33t.powerfolder.light.*;
import de.dal33t.powerfolder.message.*;
import de.dal33t.powerfolder.transfer.Download;
import de.dal33t.powerfolder.transfer.TransferManager;
import de.dal33t.powerfolder.util.*;
import de.dal33t.powerfolder.util.ui.DialogFactory;
import de.dal33t.powerfolder.util.ui.TreeNodeList;

/**
 * The main class representing a folder. Scans for new files automatically.
 * 
 * @author <a href="mailto:totmacher@powerfolder.com">Christian Sprajc </a>
 * @version $Revision: 1.114 $
 */
public class Folder extends PFComponent {
    public static final String DB_FILENAME = ".PowerFolder.db";
    public static final String DB_BACKUP_FILENAME = ".PowerFolder.db.bak";
    private final static String PREF_FILE_NAME_CHECK = "folder.check_filenames";

    private File localBase;
    /**
     * The internal database of locally known files. Contains FileInfo ->
     * FileInfo
     */
    private Map<FileInfo, FileInfo> knownFiles;

    /** files that should not be downloaded in auto download */
    private Set<FileInfo> blacklist;

    /** Map containg the cached File objects */
    private Map diskFileCache;

    /** Lock for scan */
    private Object scanLock = new Object();

    /** All members of this folder */
    private Set<Member> members;
    /** The ui node */
    private TreeNodeList treeNode;

    /** cached Directory object */
    private Directory rootDirectory;

    /**
     * the folder info, contains important information about
     * id/hash/name/filescount
     */
    private FolderInfo currentInfo;

    /** Folders sync profile */
    private SyncProfile syncProfile;

    /**
     * Flag indicating that folder has a set of own know files will be true
     * after first scan ever
     */
    private boolean hasOwnDatabase;

    /** Flag indicating, that the filenames shoule be checked */
    private boolean checkFilenames;

    /** The number of syncs waited */
    private int syncsWaited;

    /** Flag indicating */
    private boolean shutdown;

    /** The statistic for this folder */
    private FolderStatistic statistic;

    private FolderListener folderListenerSupport;
    private FolderMembershipListener folderMembershipListenerSupport;

    /**
     * Constructor
     * 
     * @param controller
     * @param fInfo
     * @param localBase
     * @throws FolderException
     */
    Folder(Controller controller, FolderInfo fInfo, File localBase)
        throws FolderException
    {
        super(controller);

        if (fInfo == null) {
            throw new NullPointerException("FolderInfo is null");
        }
        if (controller == null) {
            throw new NullPointerException("Controller is null");
        }
        this.currentInfo = new FolderInfo(fInfo.name, fInfo.id, fInfo.secret);

        if (fInfo.name == null) {
            throw new NullPointerException("Folder name is null");
        }
        if (fInfo.id == null) {
            throw new NullPointerException("Folder id (" + fInfo.id
                + ") is null");
        }
        if (localBase == null) {
            throw new NullPointerException("Folder localdir is null");
        }

        // Not until first scan or db load
        this.hasOwnDatabase = false;
        // this.shutdown = false;
        this.localBase = localBase;
        // Should we check filenames?
        this.checkFilenames = getController().getPreferences().getBoolean(
            PREF_FILE_NAME_CHECK, true);

        // Create listener support
        this.folderListenerSupport = (FolderListener) ListenerSupportFactory
            .createListenerSupport(FolderListener.class);

        this.folderMembershipListenerSupport = (FolderMembershipListener) ListenerSupportFactory
            .createListenerSupport(FolderMembershipListener.class);

        // Check base dir
        checkBaseDir(localBase);

        // Load config
        Properties config = getController().getConfig();

        String syncProfId = config.getProperty("folder." + getName()
            + ".syncprofile");
        syncProfile = SyncProfile.getSyncProfileById(syncProfId);

        if (syncProfile == null) {
            log().warn("Using fallback properties for sync profile");

            // Try to read only config style
            // Read auto dl config
            boolean autoDownload = Util.getBooleanProperty(config, "folder."
                + getName() + ".autodownload", false);

            // Check dl all
            boolean downloadAll = Util.getBooleanProperty(config, "folder."
                + getName() + ".downloadall", false);

            // read syncdelete config
            boolean syncDelete = Util.getBooleanProperty(config, "folder."
                + getName() + ".syncdelete", false);

            // Load sync profile
            // Take the best matching sync profile
            if (autoDownload) {
                if (syncDelete) {
                    this.syncProfile = SyncProfile.SYNCHRONIZE_PCS;
                } else if (downloadAll) {
                    this.syncProfile = SyncProfile.AUTO_DOWNLOAD_FROM_ALL;
                } else {
                    this.syncProfile = SyncProfile.AUTO_DOWNLOAD_FROM_FRIENDS;
                }
            }
        }

        if (syncProfile == null) {
            // Still no sync profile ? take the most passive...
            this.syncProfile = SyncProfile.MANUAL_DOWNLOAD;
        }

        // Definitvly set new sync profile id
        config.put("folder." + getName() + ".syncprofile", syncProfile.getId());

        statistic = new FolderStatistic(this);
        knownFiles = Collections
            .synchronizedMap(new HashMap<FileInfo, FileInfo>());
        members = Collections.synchronizedSet(new HashSet());
        diskFileCache = new SoftHashMap();
        blacklist = Collections.synchronizedSet(new HashSet());

        // put myself in membership
        join(controller.getMySelf());

        log().debug(
            "Opening " + this.toString() + " at '"
                + localBase.getAbsolutePath() + "'");

        // Load folder database
        loadFolderDB();

        if (localBase.list().length == 0) {
            // Empty folder... no scan required for database
            hasOwnDatabase = true;
        }

        // Force the next time scan if autodetect is set
        if (syncProfile.isAutoDetectLocalChanges()) {
            forceNextScan();
            // Trigger scan
            getController().getFolderRepository().triggerScan();
        }

        // maintain desktop shortcut if wanted
        maintainDesktopShortcut();

        log().verbose("Has own database ? " + hasOwnDatabase);

        if (hasOwnDatabase) {
            // Write filelist
            if (Logger.isLogToFileEnabled()) {
                // Write filelist to disk
                File debugFile = new File("debug/" + getName() + "/"
                    + getController().getMySelf().getNick() + ".list.txt");
                Debug.writeFileList(knownFiles.keySet(), "FileList of folder "
                    + getName() + ", member " + this + ":", debugFile);
            }
        }
    }

    public void addToBlacklist(FileInfo fileInfo) {
        blacklist.add(fileInfo);
    }

    public void removeFromBlacklist(FileInfo fileInfo) {
        blacklist.remove(fileInfo);
    }

    public void addAllToBlacklist(List<FileInfo> fileInfos) {
        blacklist.addAll(fileInfos);
    }

    public void removeAllFromBlacklist(List<FileInfo> fileInfos) {
        blacklist.removeAll(fileInfos);
    }

    public boolean isInBlacklist(FileInfo fileInfo) {
        return blacklist.contains(fileInfo);
    }

    public boolean isInBlacklist(List<FileInfo> fileInfos) {
        return blacklist.containsAll(fileInfos);
    }

    /**
     * Checks the basedir is valid
     * 
     * @param baseDir
     *            the base dir to test
     * @return if base dir ok, otherwise false
     * @throws FolderException
     */
    private void checkBaseDir(File baseDir) throws FolderException {
        // Basic checks
        if (!localBase.exists()) {
            if (!localBase.mkdirs()) {
                throw new FolderException(getInfo(), Translation
                    .getTranslation("foldercreate.error.unable_tocreate",
                        localBase.getAbsolutePath()));
            }
        } else if (!localBase.isDirectory()) {
            throw new FolderException(getInfo(), Translation.getTranslation(
                "foldercreate.error.unable_to_open", localBase
                    .getAbsolutePath()));
        }

        // Complex checks
        FolderRepository repo = getController().getFolderRepository();
        if (new File(repo.getFoldersBasedir()).equals(baseDir)) {
            throw new FolderException(getInfo(), Translation.getTranslation(
                "foldercreate.error.it_is_base_dir", baseDir.getAbsolutePath()));
        }
        // FIXME This one does not happen here (Jan)
        // I can choose a base dir that allready has a powerfolder in it
        FolderInfo[] folderInfos = repo.getJoinedFolderInfos();
        for (FolderInfo folderInfo : folderInfos) {
            Folder folder = repo.getFolder(folderInfo);
            if (folder != null) {
                if (folder.getLocalBase().equals(baseDir)) {
                    throw new FolderException(getInfo(), Translation
                        .getTranslation("foldercreate.error.already_taken",
                            folder.getName(), baseDir.getAbsolutePath()));
                }
            }
        }
    }

    /*
     * Local disk/folder management *******************************************
     */

    /**
     * Scans the local directory for new files
     */
    private void scanLocalFiles() {
        synchronized (this) {
            log().verbose("Begin scanning");
            Set<FileInfo> remaining = new HashSet<FileInfo>(knownFiles.keySet());
            int totalFiles = knownFiles.size();
            // Scan files
            int changedFiles = scanLocalFile(localBase, remaining);
            int removedFiles = 0;

            if (!remaining.isEmpty()) {
                for (Iterator<FileInfo> it = remaining.iterator(); it.hasNext();)
                {
                    FileInfo fInfo = it.next();
                    if (!fInfo.isDeleted()) {
                        fInfo.setDeleted(true);
                        fInfo.setModifiedInfo(getController().getMySelf()
                            .getInfo(), fInfo.getModifiedDate());
                        log()
                            .verbose("File removed: " + fInfo.toDetailString());

                        // Increase fileversion
                        fInfo.increaseVersion();

                        removedFiles++;
                    } else {
                        // File state correct in database, remove from remaining
                        it.remove();
                    }
                }

                log().verbose(
                    this.toString()
                        + "These files were deleted from local disk: "
                        + remaining);
            }

            // Now check for deleted files in shares
            // FileInfo[] deletedFoundInShares = tryToFindDeletedInShares();
            // log().warn(
            // "Found " + deletedFoundInShares.length + " files in shares");
            // remaining.removeAll(Arrays.asList(deletedFoundInShares));

            if (changedFiles > 0 || removedFiles > 0) {

                // broadcast new files on folder
                // TODO: Broadcast only changes !! FolderFilesChanged
                broadcastFileList();
                folderChanged();
            }

            log().debug(
                this.toString() + ": -> Scanned folder <- " + totalFiles
                    + " files total, " + changedFiles + " new/changed, "
                    + remaining.size() + " removed");
        }
    }

    /**
     * Scans just one file/directory and checks if its included in internal
     * database
     * 
     * @param file
     * @return the count of new/changed files
     */
    private int scanLocalFile(File file, Set remaining) {
        synchronized (scanLock) {
            if (file.isDirectory()
                && !getController().getRecycleBin().isRecycleBin(this, file))
            {
                if (logVerbose) {
                    log().verbose(
                        "Scanning directory: " + file.getAbsolutePath());
                }
                File[] subFiles = file.listFiles();
                int changedFiles = 0;
                if (subFiles.length > 0) {
                    for (File subFile : subFiles) {
                        changedFiles += scanLocalFile(subFile, remaining);
                    }
                } else {
                    // directory empty, remove it
                    if (!file.equals(localBase)) {
                        file.delete();
                    }
                }
                // try {
                // // sleep 2 ms to give cpu time
                // Thread.sleep(5);
                // } catch (InterruptedException e) {
                // log().verbose(e);
                // }
                return changedFiles;
            } else if (file.isFile()) {
                FileInfo fInfo = new FileInfo(this, file);
                // file is not longer remaining
                remaining.remove(fInfo);
                int nfiles = scanFile(fInfo, false) ? 1 : 0;
                // try {
                // // sleep 2 ms to give cpu time
                // Thread.sleep(2);
                // } catch (InterruptedException e) {
                // log().verbose(e);
                // }
                return nfiles;
            } else if (getController().getRecycleBin().isRecycleBin(this, file))
            {
                return 0;
            } else {
                log().warn("Unkown file found: " + file.getAbsolutePath());
                return 0;
            }
        }
    }

    /**
     * Scans one file
     * <p>
     * Package protected because used by Recylcebin to tell, that file was
     * restored
     * 
     * @param fInfo
     *            the file to be scanned
     * @return true if the file was successfully scanned
     */
    boolean scanFile(FileInfo fInfo, boolean restored) {
        synchronized (scanLock) {
            if (fInfo == null) {
                throw new NullPointerException("File is null");
            }
            // First relink modified by memberinfo to
            // actual instance if available on nodemanager
            Member from = getController().getNodeManager().getNode(
                fInfo.getModifiedBy());
            if (from != null) {
                fInfo.setModifiedInfo(from.getInfo(), fInfo.getModifiedDate());
            }

            if (logVerbose) {
                log().verbose(
                    "Scanning file: " + fInfo + ", folderId: " + fInfo);
            }
            File file = getDiskFile(fInfo);
            if (restored && file.exists()) {
                fInfo.setDeleted(false);
            }
            // file has been removed
            if (fInfo.isDeleted()) {
                log().verbose(
                    "File does not exists on local disk, flagging as deleted: "
                        + file);
                removeFileLocal(fInfo, false);
            } else if (!file.canRead()) {
                // ignore not readable
                log().verbose("File not readable: " + file);
                return false;
            }

            // ignore our database file
            if (file.getName().equals(DB_FILENAME)
                || file.getName().equals(DB_BACKUP_FILENAME))
            {
                if (!file.isHidden()) {
                    Util.makeHiddenOnWindows(file);
                }
                log().verbose("Ignoring folder database file: " + file);
                return false;
            }

            // check for incomplete download files and delete them, if
            // the real file exists
            if (Util.isTempDownloadFile(file)) {
                if (Util.isCompletedTempDownloadFile(file) || fInfo.isDeleted())
                {
                    log().verbose("Removing temp download file: " + file);
                    file.delete();
                } else {
                    log().verbose("Ignoring incomplete download file: " + file);
                }
                return false;
            }

            // ignore placeholderfiles or remove them if file exists
            if (Util.isPlaceHolderFile(file)) {
                if (!syncProfile.isCreatePlaceHolderFiles()) {
                    log().verbose("Removing placeholder file: " + file);
                    file.delete();
                } else {
                    log().verbose("Ignoring placeholder file: " + file);
                }
                return false;
            }

            // ignore a copy backup tempfile
            // created on copy process if overwriting file
            if (FileCopier.isTempBackup(file)) {
                return false;
            }

            // remove placeholder file if exist for this file
            // Commented, no useful until full implementation of placeholder
            // file
            /*
             * if (file.exists()) { File placeHolderFile =
             * Util.getPlaceHolderFile(file); if (placeHolderFile.exists()) {
             * log().verbose("Removing placeholder file for: " + file);
             * placeHolderFile.delete(); } }
             */
            if (checkFilenames) {
                checkFileName(fInfo);
            }

            synchronized (knownFiles) {
                // link new file to our folder
                fInfo.setFolder(this);
                if (!isKnown(fInfo)) {
                    log().verbose(
                        fInfo + ", modified by: " + fInfo.getModifiedBy());
                    // Update last - modified data
                    MemberInfo modifiedBy = fInfo.getModifiedBy();
                    if (modifiedBy == null) {
                        modifiedBy = getController().getMySelf().getInfo();
                    }

                    if (file.exists()) {
                        fInfo.setModifiedInfo(modifiedBy, new Date(file
                            .lastModified()));
                        fInfo.setSize(file.length());
                    }

                    // add file to folder
                    FileInfo converted = convertToMetaInfoFileInfo(fInfo);
                    addFile(converted);

                    // update directory
                    // don't do this in the server version
                    if (rootDirectory != null) {
                        getDirectory().add(getController().getMySelf(),
                            converted);
                    }
                    // get folder icon info and set it
                    if (Util.isDesktopIni(file)) {
                        makeFolderIcon(file);
                    }

                    // Fire folder change event
                    // fireEvent(new FolderChanged());

                    if (logVerbose) {
                        log().verbose(
                            this.toString() + ": Local file scanned: "
                                + fInfo.toDetailString());
                    }
                    return true;
                }

                // Now process/check existing files
                FileInfo dbFile = getFile(fInfo);

                boolean fileChanged = dbFile.syncFromDiskIfRequired(
                    getController(), file);

                if (fileChanged) {
                    // Increase fileversion
                    dbFile.increaseVersion();
                }

                // Convert to meta info loaded fileinfo if nessesary
                FileInfo convertedFile = convertToMetaInfoFileInfo(dbFile);
                if (convertedFile != dbFile) {
                    // DO A IDENTITY MATCH, THIS HERE MEANS, A META INFO
                    // FILEINFO WAS CREATED
                    addFile(convertedFile);
                    fileChanged = true;
                }

                if (logVerbose) {
                    log().verbose("File already known: " + fInfo);
                }

                return fileChanged;
            }
        }
    }

    /**
     * Checks a single filename if there are problems with the name
     * 
     * @param fileInfo
     */
    private void checkFileName(FileInfo fileInfo) {
        if (!checkFilenames) {
            return;
        }
        String totalName = localBase.getName() + fileInfo.getName();

        if (totalName.length() >= 255) {
            log().warn("path maybe to long: " + fileInfo);
            // path may become to long
            if (Util.isAWTAvailable() && !getController().isConsoleMode()) {
                String title = Translation
                    .getTranslation("folder.check_path_length.title");
                String message = Translation.getTranslation(
                    "folder.check_path_length.text", getName(), fileInfo
                        .getName());
                String neverShowAgainText = Translation
                    .getTranslation("folder.check_path_length.never_show_again");
                boolean showAgain = DialogFactory
                    .showNeverAskAgainMessageDialog(getController()
                        .getUIController().getMainFrame().getUIComponent(),
                        title, message, neverShowAgainText);
                if (!showAgain) {
                    getController().getPreferences().putBoolean(
                        PREF_FILE_NAME_CHECK, false);
                    log().warn("store do not show this dialog again");
                }
            } else {
                log().warn(
                    "Path maybe to long for folder: " + getName() + " File:"
                        + fileInfo.getName());
            }
        }
        // check length of path elements and filename
        // TODO: The check if 30 chars or more cuurrently disabled
        // Should only check if on mac system
        // String[] parts = totalName.split("\\/");
        // for (String pathPart : parts) {
        // if (pathPart.length() > 30) {
        // log().warn("filename or dir maybe to long: " + fileInfo);
        // // (sub)directory name or filename to long
        // if (Util.isAWTAvailable() && !getController().isConsoleMode()) {
        // String title = Translation
        // .getTranslation("folder.check_subdirectory_and_filename_length.title");
        // String message = Translation.getTranslation(
        // "folder.check_subdirectory_and_filename_length.text",
        // getName(), fileInfo.getName());
        // String neverShowAgainText = Translation
        // .getTranslation("folder.check_subdirectory_and_filename_length.never_show_again");
        // boolean showAgain = DialogFactory
        // .showNeverAskAgainMessageDialog(getController()
        // .getUIController().getMainFrame().getUIComponent(),
        // title, message, neverShowAgainText);
        // if (!showAgain) {
        // getController().getPreferences().putBoolean(
        // PREF_FILE_NAME_CHECK, false);
        // log().warn("store do not show this dialog again");
        // }
        // } else {
        // log().warn(
        // "Filename or subdirectory name maybe to long for folder: "
        // + getName() + " File:" + fileInfo.getName());
        //
        // }
        // }
        // }

    }

    /**
     * Converts a fileinfo into a more detailed meta info loaded fileinfo. e.g.
     * with mp3 tags
     * 
     * @param fInfo
     * @return
     */
    private FileInfo convertToMetaInfoFileInfo(FileInfo fInfo) {
        if (!(fInfo instanceof MP3FileInfo)
            && fInfo.getFilenameOnly().toUpperCase().endsWith(".MP3"))
        {
            log().verbose("Converting to MP3 TAG: " + fInfo);
            // Not an mp3 fileinfo ? convert !
            File diskFile = fInfo.getDiskFile(getController()
                .getFolderRepository());
            // Create mp3 fileinfo
            MP3FileInfo mp3FileInfo = new MP3FileInfo(this, diskFile);
            mp3FileInfo.copyFrom(fInfo);
            return mp3FileInfo;
        }

        if (!(fInfo instanceof ImageFileInfo) && Util.isAWTAvailable()
            && ImageSupport.isReadSupportedImage(fInfo.getFilenameOnly()))
        {
            log().verbose("Converting to Image: " + fInfo);
            File diskFile = fInfo.getDiskFile(getController()
                .getFolderRepository());
            // Create image fileinfo
            ImageFileInfo imageFileInfo = new ImageFileInfo(this, diskFile);
            imageFileInfo.copyFrom(fInfo);
            return imageFileInfo;
        }
        // Otherwise file is correct
        return fInfo;
    }

    /**
     * Adds a file to the internal database, does NOT store the DB
     * 
     * @param fInfo
     */
    private void addFile(FileInfo fInfo) {
        if (fInfo == null) {
            throw new NullPointerException("File is null");
        }
        // Add to this folder
        fInfo.setFolder(this);

        FileInfo old = knownFiles.put(fInfo, fInfo);

        if (old != null) {
            // Remove old file from info
            currentInfo.removeFile(old);
        }

        // log().warn("Adding " + fInfo.getClass() + ", removing " + old);

        // Add file to folder
        currentInfo.addFile(fInfo);

    }

    /**
     * Scans a new File, eg from (drag and) drop.
     * 
     * @param fileInfo
     *            the file to scan
     */
    public void scanNewFile(FileInfo fileInfo) {
        if (scanFile(fileInfo, false)) {
            folderChanged();
            statistic.calculate();
        }
    }

    /**
     * Scans a downloaded file, renames tempfile to real name
     * 
     * @param fInfo
     */
    public void scanDownloadFile(FileInfo fInfo, File tempFile) {
        synchronized (scanLock) {
            // rename file
            File targetFile = fInfo.getDiskFile(getController()
                .getFolderRepository());

            if (!tempFile.renameTo(targetFile)) {
                log().warn(
                    "Was not able to rename tempfile, copiing "
                        + tempFile.getAbsolutePath());
                try {
                    Util.copyFile(tempFile, targetFile);
                } catch (IOException e) {
                    // TODO give a diskfull warning?
                    log().verbose(e);
                    log().error(
                        "Unable to store completed download "
                            + targetFile.getAbsolutePath() + ". "
                            + e.getMessage());
                }
            }

            if (tempFile.exists() && !tempFile.delete()) {
                log().error("Unable to remove temp file: " + tempFile);
            }

            // Set modified date of remote
            targetFile.setLastModified(fInfo.getModifiedDate().getTime());

            // Update internal database
            FileInfo dbFile = getFile(fInfo);
            if (dbFile != null) {
                // Update database
                dbFile.setModifiedInfo(fInfo.getModifiedBy(), fInfo
                    .getModifiedDate());
                dbFile.setVersion(fInfo.getVersion());
                dbFile.setSize(fInfo.getSize());
            } else {
                // File new, scan
                scanFile(fInfo, false);
            }

            // Folder has changed
            folderChanged();
            // Fire just change, store comes later
            // fireFolderChanged();
        }

        // re-calculate statistics
        statistic.calculate();
    }

    /**
     * Creates placeholder files for expected files on disk FIXME: Check
     * filename length, may cause problems if too long
     */
    private void maintainPlaceHolderFiles() {
        if (!getSyncProfile().isCreatePlaceHolderFiles()) {
            return;
        }
        FileInfo[] expectedFiles = getExpecedFiles(getSyncProfile()
            .isAutoDownloadFromOthers());
        for (FileInfo expectedFile : expectedFiles) {
            File diskFile = expectedFile.getDiskFile(getController()
                .getFolderRepository());
            if (diskFile == null) {
                // try next one
                continue;
            }
            TransferManager tm = getController().getTransferManager();

            // create placeholderfile
            File placeHolderFile = new File(diskFile.getParentFile(),
                expectedFile.getFilenameOnly() + ".pf");
            if (!tm.isDownloadingActive(expectedFile)
                && (placeHolderFile.getParentFile().exists() || placeHolderFile
                    .getParentFile().mkdirs()))
            {
                try {
                    placeHolderFile.createNewFile();
                } catch (IOException e) {
                    log().verbose(e);
                }
            }
        }
    }

    /**
     * Answers if this file is known to the internal db
     * 
     * @param fi
     * @return
     */
    public boolean isKnown(FileInfo fi) {
        return knownFiles.containsKey(fi);
    }

    /**
     * Re-downloads a file. Basically drops a file from the database.
     * 
     * @param file
     *            the file to request
     */
    public void reDownloadFile(FileInfo file) {
        synchronized (scanLock) {
            if (isKnown(file)) {
                log().verbose("File re-requested: " + file);
                currentInfo.removeFile(file);
                knownFiles.remove(file);
            } else {
                throw new IllegalArgumentException(file + " not on " + this);
            }
        }
    }

    /**
     * not used Re-downloads files. Basically drops files from the database.
     * 
     * @param files
     *            the file to request
     */
    /*
     * public void reDownloadFiles(FileInfo[] files) { if (files == null) {
     * throw new NullPointerException("Files to redownload are null"); }
     * synchronized (scanLock) { for (int i = 0; i < files.length; i++) {
     * FileInfo file = files[i]; if (isKnown(file)) { log().verbose("File
     * re-requested: " + file); currentInfo.removeFile(file);
     * knownFiles.remove(file); } else { throw new IllegalArgumentException(file + "
     * not on " + this); } } } folderChanged(); }
     */

    /**
     * Removes a file on local folder, diskfile will be removed and file tagged
     * as deleted
     * 
     * @param fInfo
     * @return true if the folder was changed
     */
    private boolean removeFileLocal(FileInfo fInfo, boolean broadcastDirectly) {
        log().verbose(
            "Remove file local: " + fInfo + ", Folder equal ? "
                + Util.equals(fInfo.getFolderInfo(), getInfo()));
        boolean folderChanged = false;

        synchronized (scanLock) {
            if (isKnown(fInfo)) {
                File diskFile = getDiskFile(fInfo);
                if (diskFile != null && diskFile.exists()) {
                    RecycleBin recycleBin = getController().getRecycleBin();
                    if (!recycleBin.moveToRecycleBin(fInfo, diskFile)) {
                        log().error("Unable to move to recycle bin" + fInfo);

                        if (!diskFile.delete()) {
                            log().error("Unable to remove file" + fInfo);
                        }
                    }
                    FileInfo localFile = getFile(fInfo);

                    // update database
                    localFile.setDeleted(true);
                    // update modified info, but not date
                    localFile.setModifiedInfo(getController().getMySelf()
                        .getInfo(), localFile.getModifiedDate());

                    // Increase version
                    localFile.increaseVersion();

                    // folder changed
                    folderChanged = true;
                }
            } else {
                // Add file as deleted
                fInfo.setDeleted(true);
                // Inrease version
                // FIXME deletion is not changing the file
                // if i want to calculate the availability of a file
                // the version number of a deleted file should not have changed
                // further i have version numbers of 56! with files that have
                // not changed, only deleted
                fInfo.increaseVersion();
                addFile(fInfo);
                folderChanged = true;
            }

            // Abort downloads of files
            Download dl = getController().getTransferManager()
                .getActiveDownload(fInfo);
            if (dl != null) {
                dl.abortAndCleanup();
            }
        }

        if (folderChanged && broadcastDirectly) {
            folderChanged();

            // Broadcast to members
            FolderFilesChanged changes = new FolderFilesChanged(getInfo());
            changes.removed = new FileInfo[]{fInfo};
            broadcastMessage(changes);
        }

        return folderChanged;
    }

    /**
     * Removes files from the local disk
     * 
     * @param fis
     */
    public void removeFilesLocal(FileInfo[] fis) {
        if (fis == null || fis.length <= 0) {
            throw new IllegalArgumentException("Files to delete are empty");
        }

        List removedFiles = new ArrayList();
        synchronized (scanLock) {
            for (FileInfo fileInfo : fis) {
                if (removeFileLocal(fileInfo, false)) {
                    removedFiles.add(fileInfo);
                }
            }
        }
        if (!removedFiles.isEmpty()) {
            folderChanged();

            // Broadcast to members
            FolderFilesChanged changes = new FolderFilesChanged(getInfo());
            changes.removed = new FileInfo[removedFiles.size()];
            removedFiles.toArray(changes.removed);
            broadcastMessage(changes);
        }
    }

    /**
     * Loads the folder database from disk
     * 
     * @param dbFile
     *            the file to load as db file
     * @return true if succeeded
     */
    private boolean loadFolderDB(File dbFile) {
        synchronized (scanLock) {
            if (!dbFile.exists()) {
                log().debug(
                    this + ": Database file not found: "
                        + dbFile.getAbsolutePath());
                return false;
            }
            try {
                // load files and scan in
                InputStream fIn = new BufferedInputStream(new FileInputStream(
                    dbFile));
                ObjectInputStream in = new ObjectInputStream(fIn);
                FileInfo[] files = (FileInfo[]) in.readObject();
                for (FileInfo fileInfo : files) {
                    // scanFile(fileInfo);
                    addFile(fileInfo);
                }

                // read them always ..
                MemberInfo[] members1 = (MemberInfo[]) in.readObject();
                // Do not load members
                if (getController().isBackupServer()) {
                    log().verbose("Loading " + members1.length + " members");
                    for (MemberInfo memberInfo : members1) {
                        Member member = memberInfo.getNode(getController(),
                            true);
                        join(member);
                    }
                }

                try {
                    blacklist = (Set<FileInfo>) in.readObject();
                    log().debug("doNotAutoDownload: " + blacklist.size());
                } catch (java.io.EOFException e) {
                    // ignore nothing available for doNotAutoDownload
                    log().debug("doNotAutoDownload nothing for " + this);
                } catch (Exception e) {
                    log()
                        .error("doNotAutoDownload " + this + e.getMessage(), e);
                }

                in.close();
                fIn.close();
            } catch (IOException e) {
                log().warn(
                    this + ": Unable to read database file: "
                        + dbFile.getAbsolutePath());
                log().verbose(e);
                return false;
            } catch (ClassNotFoundException e) {
                log().warn(
                    this + ": Unable to read database file: "
                        + dbFile.getAbsolutePath());
                log().verbose(e);
                return false;
            }

            // Ok has own database
            hasOwnDatabase = true;
        }

        // Calculate statistic
        statistic.calculate();

        return true;
    }

    /**
     * Loads the folder db from disk
     */
    private void loadFolderDB() {
        if (!loadFolderDB(new File(localBase, DB_FILENAME))) {
            log().warn(
                "Unable to find db file: " + DB_FILENAME + ", trying backup");
            if (!loadFolderDB(new File(localBase, DB_BACKUP_FILENAME))) {
                log().warn("Unable to read folder db, even from backup");
            }
        }
    }

    /**
     * Shuts down the folder
     */
    public void shutdown() {
        log().debug("shutting down folder " + this);

        // Remove listeners, not bothering them by boring shutdown events
        // disabled this so the ui is updated (more or less) that the folders
        // are disabled from debug panel
        // ListenerSupportFactory.removeAllListeners(folderListenerSupport);

        shutdown = true;
        storeFolderDB();

        removeAllListener();
    }

    /**
     * Stores the current file-database to disk
     */
    private void storeFolderDB() {
        if (!shutdown) {
            if (!getController().isStarted()) {
                // Not storing
                return;
            }
        }
        synchronized (scanLock) {
            File dbFile = new File(localBase, DB_FILENAME);
            File dbFileBackup = new File(localBase, DB_BACKUP_FILENAME);
//            boolean dbExisted = dbFile.exists();
//            boolean dbBackupExisted = dbFileBackup.exists();
            try {
                //log().debug(dbExisted +" <-Does DBFile ("+ dbFile.getAbsolutePath() +") exists?" );
                FileInfo[] files = getFiles();
                if (dbFile.exists()) {
                    dbFile.delete();                                        
                } 
                dbFile.createNewFile() ;
                OutputStream fOut = new BufferedOutputStream(
                    new FileOutputStream(dbFile));
                ObjectOutputStream oOut = new ObjectOutputStream(fOut);
                // Store files
                oOut.writeObject(files);
                // Store members
                oOut.writeObject(Util.asMemberInfos(getMembers()));
                // Store doNotAutoDownloadFileList
                if (blacklist != null) {
                    log().debug("write do not auto download");
                    oOut.writeObject(blacklist);
                }
                oOut.close();
                fOut.close();
                log().debug("Successfully wrote folder database file");

                // make file hidden now
                //if (!dbExisted) {
                    //Util.setAttributesOnWindows(dbFile, true, true);
                //} else {
                //    System.err
                //        .println("Not setting attributes on already existing database file: "
                //            + dbFile.getAbsolutePath());
               // }

                // Make backup
                Util.copyFile(dbFile, dbFileBackup);
                //if (!dbBackupExisted) {
                    //Util.setAttributesOnWindows(dbFileBackup, true, true);
                //} else {
                //    System.err
                //        .println("Not setting attributes on already existing database backup file: "
                //            + dbFileBackup.getAbsolutePath());
               // }
            } catch (IOException e) {
                // TODO: if something failed shoudn't we try to restore the
                // backup (if backup exists and bd file not after this?
                log().error(
                    this + ": Unable to write database file "
                        + dbFile.getAbsolutePath(), e);
                log().verbose(e);
            }
        }
    }

    /**
     * Set the needed folder/file attributes on windows systems, if we have a
     * desktop.ini
     * 
     * @param dektopIni
     */
    private void makeFolderIcon(File desktopIni) {
        if (desktopIni == null) {
            throw new NullPointerException("File (desktop.ini) is null");
        }
        if (!Util.isWindowsSystem()) {
            log().verbose(
                "Not a windows system, ignoring folder icon. "
                    + desktopIni.getAbsolutePath());
            return;
        }

        log().verbose(
            "Setting icon of " + desktopIni.getParentFile().getAbsolutePath());

        Util.setAttributesOnWindows(desktopIni, true, true);
        Util.setAttributesOnWindows(desktopIni.getParentFile(), false, true);
    }

    /**
     * Maintains a desktop shortcut for this folder. currently only available on
     * windows systems.
     * 
     * @return true if succeeded
     */
    private boolean maintainDesktopShortcut() {
        boolean createRequested = getController().getPreferences().getBoolean(
            "createdesktopshortcuts", true);

        String shortCutName = getName();
        if (getController().isVerbose()) {
            shortCutName = "[" + getController().getMySelf().getNick() + "] "
                + shortCutName;
        }

        if (createRequested) {
            return Util.createDesktopShortcut(shortCutName, localBase
                .getAbsoluteFile());
        }

        // Remove shortcuts to folder if not wanted
        Util.removeDesktopShortcut(shortCutName);
        return false;
    }

    /**
     * Deletes the desktop shortcut of the folder
     */
    public void removeDesktopShortcut() {
        String shortCutName = getName();
        if (getController().isVerbose()) {
            shortCutName = "[" + getController().getMySelf().getNick() + "] "
                + shortCutName;
        }

        // Remove shortcuts to folder
        Util.removeDesktopShortcut(shortCutName);
    }

    /**
     * Returns the syncprofile of this folder
     * 
     * @return
     */
    public SyncProfile getSyncProfile() {
        return syncProfile;
    }

    /**
     * Sets the synchronisation profile for this folder
     * 
     * @param syncProfile
     */
    public void setSyncProfile(SyncProfile aSyncProfile) {
        if (syncProfile == null) {
            throw new NullPointerException("Unable to set null sync profile");
        }
        SyncProfile oldProfile = syncProfile;
        if (oldProfile == aSyncProfile) {
            // Omitt set
            return;
        }

        log().debug("Setting " + aSyncProfile);
        this.syncProfile = aSyncProfile;

        // Store on disk
        String syncProfKey = "folder." + getName() + ".syncprofile";
        getController().getConfig().put(syncProfKey, syncProfile.getId());
        getController().saveConfig();

        if (oldProfile.isAutodownload() && !syncProfile.isAutodownload()) {
            // Changed from autodownload to manual, we need to abort all
            // Automatic download
            getController().getTransferManager().abortAllAutodownloads(this);
        }
        if (syncProfile.isAutodownload()) {
            // Trigger request files
            getController().getFolderRepository().getFileRequestor()
                .triggerFileRequesting();
        }

        if (syncProfile.isSyncDeletionWithFriends()
            || syncProfile.isSyncDeletionWithOthers())
        {
            handleRemoteDeletedFiles(false);
        }

        firePropertyChange("syncProfile", oldProfile, syncProfile);
        fireSyncProfileChanged();
    }

    /**
     * Next scan will surely be scanned
     */
    public void forceNextScan() {
        log().verbose("Scan forced");
        syncsWaited = syncProfile.getMinutesBetweenScans();
    }

    /**
     * Scan method, executes all things, that should be done regulary
     * 
     * @return true if the folder was scanned, false if omitted
     */
    public boolean scan() {
        if (syncsWaited < syncProfile.getMinutesBetweenScans()) {
            syncsWaited++;
            log().debug(
                "NOT Scanning '" + getName() + "', waited " + syncsWaited + "/"
                    + syncProfile.getMinutesBetweenScans() + " minutes");
            return false;
        }

        log().info("Scanning '" + getName() + "'");
        syncsWaited = 0;

        // Maintain the desktop shortcut
        maintainDesktopShortcut();

        synchronized (this) {
            // Handle deletions
            handleRemoteDeletedFiles(false);

            // local files
            scanLocalFiles();
        }

        // create placeholder files for expeceted files
        maintainPlaceHolderFiles();

        // Now has own database
        hasOwnDatabase = true;

        // re-calculate statistics
        statistic.calculate();

        return true;
    }

    /**
     * Tries to find deleted files on folder in shares. TODO: INCOMPLETE not
     * used
     * 
     * @return the files which have been found in the shares
     */
    /*
     * private FileInfo[] tryToFindDeletedInShares() { FileInfo[] files =
     * getFiles(); List found = new ArrayList(); for (int i = 0; i <
     * files.length; i++) { FileInfo fInfo = files[i]; File diskFile =
     * getDiskFile(fInfo); if (fInfo.isDeleted()) { // Deleted file, look in
     * shares diskFile = getController().getFolderRepository().getShares()
     * .findFile(fInfo); if (diskFile != null && diskFile.exists()) { // Update
     * diskfile cache fInfo.setDeleted(false); diskFileCache.put(fInfo,
     * diskFile); found.add(fInfo); } } } FileInfo[] foundFiles = new
     * FileInfo[found.size()]; found.toArray(foundFiles); return foundFiles; }
     */
    /*
     * Member managing methods ************************************************
     */

    /**
     * Joins a member to the folder,
     * 
     * @param member
     */
    public void join(Member member) {
        if (member == null) {
            throw new NullPointerException("Member is null, unable to join");
        }

        // member will be joined, here on local
        synchronized (members) {
            if (!getController().getNodeManager().knowsNode(member)) {
                log().warn(
                    "Unable to join " + member + " to " + this
                        + ": Not a known node");
            }
            if (members.contains(member)) {
                members.remove(member);
            }
            members.add(member);
        }
        log().verbose("Member joined " + member);

        // send him our list of files
        if (member.isConnected()) {
            member.sendMessagesAsynchron(FileList.createFileListMessages(this));
        }

        // for UI
        /*
         * if (treeNode != null && !treeNode.contains(member)) {
         * treeNode.addChild(member); treeNode.sort(); }
         */

        // Fire event
        fireMemberJoined(member);
    }

    /**
     * Removes a member from this folder
     * 
     * @param member
     */
    public void remove(Member member) {
        if (!members.contains(member)) {
            return;
        }
        synchronized (members) {
            members.remove(member);
        }

        log().debug("Member left " + member);

        // remove files of this member in our datastructure
        // don't do this in the server version
        if (rootDirectory != null) {
            getDirectory().removeFilesOfMember(member);
        }

        // Fire event
        fireMemberLeft(member);
    }

    /**
     * Requests new filelists from all members Normally this should not be
     * nessesary
     */
    public void requestNewFileLists() {
        broadcastMessage(new RequestFileList(this.getInfo()));
    }

    /**
     * Requests missing files for autodownload. May not request any files if
     * folder is not in auto download sync profile
     */
    public void requestMissingFilesForAutodownload() {
        if (!syncProfile.isAutodownload()) {
            return;
        }
        log().verbose("Requesting files (autodownload)");
        requestMissingFiles(syncProfile.isAutoDownloadFromFriends(),
            syncProfile.isAutoDownloadFromOthers(), false);
    }

    /**
     * Answers if the folder
     * 
     * @return
     */
    public boolean isSynchronizing() {
        if (getController().getTransferManager().countNumberOfDownloads(this) > 0)
        {
            return true;
        }
        if (!syncProfile.isAutodownload()) {
            return false;
        }
        // ok we have an autodownload profile so now check remote files against
        // ours
        Member[] conMembers = getConnectedMembers();
        for (Member member : conMembers) {
            if (!member.isConnected()) {
                // Disconnected in the meantime
                // go to next member
                continue;
            }
            FileInfo[] remoteFiles = getFiles(member);
            if (remoteFiles == null) {
                // no filelist
                // go to next member
                continue;
            }
            for (FileInfo remoteFile : remoteFiles) {
                // check if we need this file
                if (needFile(remoteFile, member, syncProfile
                    .isAutoDownloadFromFriends(), syncProfile
                    .isAutoDownloadFromOthers()))
                {
                    return true;
                }
            }
            // no file needed at this member continue with the next member
        }
        return false;
    }

    /**
     * Checks the file version against to local file version and the syncOptions
     * choosen for this folder and checks if not in "do not auto download" ->
     * determaines if we "need" this file.
     * 
     * @return true if this file should be downloaded from this sourceMember
     */
    private boolean needFile(FileInfo remoteFileInfo, Member sourceMember,
        boolean requestFromFriends, boolean requestFromOthers)
    {
        if (remoteFileInfo.isDeleted()) {
            return false;
        }

        if (blacklist != null && blacklist.contains(remoteFileInfo)) {
            // skip file if marked as Do Not Auto Download
            return false;
        }

        FileInfo localFile = getFile(remoteFileInfo);

        boolean fileFromFriend = sourceMember.isFriend()
            && remoteFileInfo.isModifiedByFriend(getController());

        if (fileFromFriend && !requestFromFriends) {
            // Skip file from friend if not auto-dl from friends not wanted
            return false;
        }

        if (!fileFromFriend && !requestFromOthers) {
            // Skip file from foreigner if auto-dl from other is disabled
            return false;
        }

        // local file present, but remote newer?
        if (localFile != null && remoteFileInfo.isNewerThan(localFile)) {
            // FIXME: Maybe this causes trouble between file systems
            // FAT<->NTFS
            boolean fileDatesSame = localFile.getModifiedDate().equals(
                remoteFileInfo.getModifiedDate());

            if (localFile.getSize() == remoteFileInfo.getSize()
                && fileDatesSame)
            {
                // OK This might be the same file, older pf version
                // Did not make the lastmodified of file correctly
                // Takeover modified info from remote file
                log().warn(
                    "Using modified-workaround for file " + localFile
                        + " adapting modified info from remote");
                localFile.setModifiedInfo(remoteFileInfo.getModifiedBy(),
                    remoteFileInfo.getModifiedDate());
                File diskFile = localFile.getDiskFile(getController()
                    .getFolderRepository());
                diskFile.setLastModified(remoteFileInfo.getModifiedDate()
                    .getTime());
                localFile.setVersion(remoteFileInfo.getVersion());
                // NOT download file, remove this in next version
                return false;
            }
            log().verbose(
                "Remote file is newer than local, local: "
                    + localFile.toDetailString() + ", remote: "
                    + remoteFileInfo.toDetailString());
            return true;
        }

        if (Util.isPlaceHolderFile(remoteFileInfo)) {
            return false;
        }

        return localFile == null;
    }

    /**
     * Checks all new received filelists from member and downloads unknown/new
     * files TODO: Move this method into <code>FileRequestor</code>
     * <p>
     * FIXME: Does requestFromFriends work?
     * 
     * @param all
     *            true if to request all files at once, false to smoothly
     *            request and lesser number
     */
    public void requestMissingFiles(boolean requestFromFriends,
        boolean requestFromOthers, boolean allAtOnce)
    {
        // Dont request files until has own database
        if (!hasOwnDatabase) {
            return;
        }

        FileInfo[] expectedFiles = getExpecedFiles(requestFromOthers);
        TransferManager tm = getController().getTransferManager();
        for (FileInfo fInfo : expectedFiles) {
            if (tm.isDownloadingActive(fInfo) || tm.isDownloadingPending(fInfo))
            {
                // Already downloading
                continue;
            }
            boolean download = requestFromOthers
                || (requestFromFriends && fInfo.getModifiedBy().getNode(
                    getController()).isFriend());

            if (download) {
                tm.downloadNewestVersion(fInfo, true);
            }
        }
        //
        // Member[] conMembers = getConnectedMembers();
        // log().verbose(
        // "Requesting missing files, " + conMembers.length + " member(s)");
        //
        // for (Member member : conMembers) {
        // if (!member.isConnected()) {
        // // Disconnected in the meantime
        // // go to next member
        // continue;
        // }
        //
        //            
        // FileInfo[] remoteFiles = getFiles(member);
        // if (remoteFiles == null) {
        // continue;
        // }
        //
        // // Sort filelist to download newest first
        // Arrays.sort(remoteFiles, new FileInfoComparator(
        // FileInfoComparator.BY_MODIFIED_DATE));
        //
        // TransferManager tm = getController().getTransferManager();
        //
        // int nDLsFromMember = tm.getNumberOfDownloadsFrom(member);
        // int nFilesNeeded = 0;
        //
        // for (FileInfo remoteFile : remoteFiles) {
        //
        // boolean fileNeeded = needFile(remoteFile, member,
        // requestFromFriends, requestFromOthers);
        //
        // // in sync if we have all files
        // if (fileNeeded) {
        // nFilesNeeded++;
        // }
        //
        // // check
        // boolean enoughRequestedFromMember;
        // if (allAtOnce) {
        // // Give it all to me
        // enoughRequestedFromMember = false;
        // } else {
        // enoughRequestedFromMember = member.isOnLAN()
        // ? nDLsFromMember >= Constants.MAX_DLS_FROM_LAN_MEMBER
        // : nDLsFromMember >= Constants.MAX_DLS_FROM_INET_MEMBER;
        // }
        //
        // if (fileNeeded && !enoughRequestedFromMember) {
        //
        // boolean downloading = tm.isDownloading(remoteFile);
        // // Need to download file, we do not have it here, and we
        // // are not downloading it at the moment and member is a
        // // friend of ours
        // if (!downloading) {
        // // getController().getTransferManager()
        // // .downloadNewestVersion(remoteFile, true);
        // // FIXME: This makes not sure that older files are
        // // omitted
        // getController().getTransferManager().requestDownload(
        // remoteFile, member, true);
        // nDLsFromMember++;
        // } else {
        // log()
        // .verbose("Already loading file down " + remoteFile);
        // }
        // }
        // }
        //
        // if (nFilesNeeded == 0) {
        // log().debug(
        // "No files needed from '" + member.getNick() + "', has "
        // + remoteFiles.length + " files total");
        // } else {
        // log().debug(
        // nFilesNeeded + " file(s) needed from '" + member.getNick()
        // + "', has " + remoteFiles.length + " files total");
        // }
        // }
    }

    /**
     * Synchronizes the deleted files with local folder
     * 
     * @param force
     *            forces to sync deltions even if syncprofile has no deltion
     *            sync option
     */
    public boolean handleRemoteDeletedFiles(boolean force) {
        if (!force) {
            // Check if allowed on folder
            if (!syncProfile.isSyncDeletionWithFriends()
                && !syncProfile.isSyncDeletionWithOthers())
            {
                // No sync wanted
                return false;
            }
        }

        Member[] conMembers = getConnectedMembers();
        log().debug(
            "Deleting files, which are deleted by friends. con-members: "
                + Arrays.asList(conMembers));

        List removedFiles = new ArrayList();

        for (Member member : conMembers) {
            if (!member.isConnected()) {
                // disconected in the meantime
                // go to next member
                continue;
            }

            FileInfo[] fileList = member.getLastFileList(this.getInfo());
            if (fileList == null) {
                continue;
            }

            log().verbose(
                "RemoteFileDeletion sync. Member '" + member.getNick()
                    + "' has " + fileList.length + " possible files");

            for (FileInfo remoteFile : fileList) {
                boolean fileFromFriend = remoteFile
                    .isModifiedByFriend(getController());
                if (!fileFromFriend) {
                    // Not modified by friend, skip file
                    continue;
                }

                FileInfo localFile = getFile(remoteFile);
                boolean remoteFileNewer = true;
                if (localFile != null) {
                    remoteFileNewer = remoteFile.isNewerThan(localFile);
                }

                if (!remoteFileNewer) {
                    // Not newer, skip file
                    // log().warn(
                    // "Ingoring file (not newer): " + remoteFile.getName()
                    // + ". local-ver: " + localFile.getVersion()
                    // + ", remote-ver: " + remoteFile.getVersion());
                    continue;
                }

                // log().warn("Remote file has a newer file :" +
                // remoteFile.toDetailString());

                // Okay the remote file is newer

                // Add to local file to database if was deleted on remote
                if (localFile == null && remoteFile.isDeleted()) {
                    addFile(remoteFile);
                    localFile = getFile(remoteFile);
                    // File has been marked as removed at our side
                    removedFiles.add(localFile);
                }

                if (localFile != null) {
                    // log().warn("Okay we have local file :" + localFile);
                    if (remoteFile.isDeleted() && !localFile.isDeleted()) {
                        File localCopy = localFile.getDiskFile(getController()
                            .getFolderRepository());
                        log().warn(
                            "File was deleted by " + member
                                + ", deleting local: " + localCopy);
                        RecycleBin recycleBin = getController().getRecycleBin();
                        if (!recycleBin.moveToRecycleBin(localFile, localCopy))
                        {
                            log().error(
                                "Unable to move file to recycle bin"
                                    + localCopy);
                            if (!localCopy.delete()) {
                                log().error(
                                    "Unable to delete file " + localCopy);
                            }
                        }
                        // }
                        localFile.setDeleted(true);
                        localFile.setModifiedInfo(remoteFile.getModifiedBy(),
                            remoteFile.getModifiedDate());
                        localFile.setVersion(remoteFile.getVersion());

                        // File has been removed
                        removedFiles.add(localFile);

                        // Abort dl if one is active
                        Download dl = getController().getTransferManager()
                            .getActiveDownload(localFile);
                        if (dl != null) {
                            dl.abortAndCleanup();
                        }
                    } else if (localFile.isDeleted() && !remoteFile.isDeleted())
                    {
                        // Local file is deleted, check if version on remote is
                        // higher
                        log().warn("File restored on remote: " + remoteFile);
                        reDownloadFile(remoteFile);
                    }
                }
            }
        }

        // Broadcast folder change if changes happend
        if (!removedFiles.isEmpty()) {
            folderChanged();

            // Broadcast to memebers
            FolderFilesChanged changes = new FolderFilesChanged(getInfo());
            changes.removed = new FileInfo[removedFiles.size()];
            removedFiles.toArray(changes.removed);
            broadcastMessage(changes);
        }

        return true;
    }

    /**
     * Broadcasts a message through the folder
     * 
     * @param message
     */
    public void broadcastMessage(Message message) {
        for (Member member : getConnectedMembers()) {
            // still connected?
            if (member.isConnected()) {
                // sending all nodes my knows nodes
                member.sendMessageAsynchron(message, null);
            }
        }
    }

    /**
     * Broadcasts the filelist
     */
    private void broadcastFileList() {
        log().verbose("Broadcasting filelist");
        Message[] fileListMessages = FileList.createFileListMessages(this);
        for (Message message : fileListMessages) {
            broadcastMessage(message);
        }
    }

    /**
     * Callback method from member. Called when a new filelist was send
     * 
     * @param from
     * @param newList
     */
    public void fileListChanged(Member from, FileList newList) {
        log().debug("New Filelist received from " + from);
        // don't do this in the server version
        if (rootDirectory != null) {
            getDirectory().addAll(from, newList.files);
        }

        if (getSyncProfile().isAutodownload()) {
            // Trigger file requestor
            log().verbose(
                "Triggering file requestor because of new remote file list from "
                    + from);
            getController().getFolderRepository().getFileRequestor()
                .triggerFileRequesting();
        }

        // Handle remote deleted files
        handleRemoteDeletedFiles(false);

        // TODO should be done by Directory that has actualy changed?
        fireRemoteContentsChanged();
    }

    /**
     * Callback method from member. Called when a filelist delta received
     * 
     * @param from
     * @param changes
     */
    public void fileListChanged(Member from, FolderFilesChanged changes) {
        log().debug("File changes received from " + from);

        // TODO this should be done in differnt thread:
        // don't do this in the server version
        if (rootDirectory != null) {
            if (changes.added != null) {
                getDirectory().addAll(from, changes.added);
            }
            if (changes.modified != null) {
                getDirectory().addAll(from, changes.modified);
            }
            if (changes.removed != null) {
                getDirectory().addAll(from, changes.removed);
            }
            // /TODO
        }
        if (getSyncProfile().isAutodownload()) {
            // Trigger file requestor
            log().verbose(
                "Triggering file requestor because of remote file list change from "
                    + from);
            getController().getFolderRepository().getFileRequestor()
                .triggerFileRequesting();
        }

        // Handle remote deleted files
        handleRemoteDeletedFiles(false);

        // Fire event
        fireRemoteContentsChanged();
    }

    /**
     * Methods which updates all nessesary components if the folder changed
     */
    private void folderChanged() {
        storeFolderDB();

        // Write filelist
        if (Logger.isLogToFileEnabled()) {
            // Write filelist to disk
            File debugFile = new File("debug/" + getName() + "/"
                + getController().getMySelf().getNick() + ".list.txt");
            Debug.writeFileList(knownFiles.keySet(), "FileList of folder "
                + getName() + ", member " + this + ":", debugFile);
        }

        // Fire general folder change event
        fireFolderChanged();

    }

    /*
     * Simple getters/exposing methods ****************************************
     */

    /**
     * Answers the local base directory
     * 
     * @return
     */
    public File getLocalBase() {
        return localBase;
    }

    public String getName() {
        return currentInfo.name;
    }

    public boolean isSecret() {
        return currentInfo.secret;
    }

    public long getHash() {
        return currentInfo.hash;
    }

    public int getFilesCount() {
        return knownFiles.size();
    }

    /**
     * Returns the list of all files in local folder database
     * 
     * @return
     */
    public FileInfo[] getFiles() {
        synchronized (knownFiles) {
            FileInfo[] files = new FileInfo[knownFiles.size()];
            knownFiles.values().toArray(files);
            return files;
        }
    }

    /**
     * get the Directories in this folder (including the subs and files)
     * 
     * @return Directory with all sub dirs and treeNodes set
     */
    public Directory getDirectory() {
        if (rootDirectory == null) {
            return getDirectory0(false);
        }
        return rootDirectory;
    }

    /**
     * Initernal method //TODO: add a task to read this in the background?
     * 
     * @param initalizeCall
     * @return
     */
    private Directory getDirectory0(boolean initalizeCall) {
        FileInfo[] knownFilesArray;

        synchronized (knownFiles) {
            knownFilesArray = new FileInfo[knownFiles.size()];
            Set<FileInfo> knownFilesSet = knownFiles.keySet();
            knownFilesArray = knownFilesSet.toArray(knownFilesArray);
        }

        Directory directory = Directory.buildDirsRecursive(getController()
            .getNodeManager().getMySelf(), knownFilesArray, this);

        if (!initalizeCall && treeNode.getChildCount() > 0) {
            treeNode.remove(0);
        }
        List<Directory> subs = directory.listSubDirectories();
        for (int i = 0; i < subs.size(); i++) {
            treeNode.insert(subs.get(i), i);
        }

        return directory;
    }

    /**
     * Answers all the expeced files
     * <p>
     * FIXME: Does not work, only returns files of friends and ignores file
     * version !!
     * 
     * @param includeNonFriendFiles
     *            if files should be included, that are modified by non-friends
     * @return
     */
    public FileInfo[] getExpecedFiles(boolean includeNonFriendFiles) {
        // build a temp list
        Map temp = new HashMap();
        // add expeced files
        Member[] conMembers = getConnectedMembers();
        for (Member member : conMembers) {
            if (!member.isConnected()) {
                // disconnected in the meantime
                continue;
            }

            FileInfo[] memberFiles = getFiles(member);
            if (memberFiles != null) {
                for (FileInfo fInfo : memberFiles) {
                    boolean modificatorOk = includeNonFriendFiles
                        || fInfo.isModifiedByFriend(getController());
                    if (!modificatorOk) {
                        continue;
                    }
                    if (fInfo.isDeleted()) {
                        continue;
                    }
                    if (hasFile(fInfo)) {
                        // Check if remote file is newer
                        FileInfo localFile = getFile(fInfo);
                        if (!fInfo.isNewerThan(localFile)) {
                            // Remote file not newer, skip
                            continue;
                        }
                    }

                    // Okay this one is expected
                    temp.put(fInfo, fInfo);
                }
            }
        }

        FileInfo[] files = new FileInfo[temp.size()];
        temp.values().toArray(files);
        log().verbose("Expected files " + files.length);
        return files;
    }

    /**
     * Returns the list of files from a member
     * 
     * @param member
     * @return
     */
    public FileInfo[] getFiles(Member member) {
        if (member == null) {
            throw new NullPointerException("Member is null");
        }
        if (member.isMySelf()) {
            return getFiles();
        }
        FileInfo[] list = member.getLastFileList(getInfo());
        if (list == null) {
            return null;
        }
        return list;
    }
    
    
    
    /**
     * Answers all files, those on local disk and expected files.
     * 
     * @param includeOfflineUsers
     *            true if also files of offline user should be included
     * @return
     */
    public FileInfo[] getAllFiles(boolean includeOfflineUsers) {
        // build a temp list
        Map<FileInfo, FileInfo> filesMap = new HashMap<FileInfo, FileInfo>(
            knownFiles.size());

        Member[] members;
        if (includeOfflineUsers) {
            members = getMembers();
        } else {
            members = getConnectedMembers();
        }
        for (Member member : members) {
            if (member.isMySelf()) {
                continue;
            }

            FileInfo[] memberFiles = getFiles(member);
            if (memberFiles == null) {
                continue;
            }
            for (FileInfo remoteFile : memberFiles) {
                // Add only if not already added or file is not deleted on
                // remote side
                FileInfo inMap = filesMap.get(remoteFile);
                if (inMap == null || inMap.isDeleted()) {
                    filesMap.put(remoteFile, remoteFile);
                }
            }
        }

        // Put own files over filelist till now
        synchronized (knownFiles) {
            filesMap.putAll(knownFiles);
        }

        FileInfo[] allFiles = new FileInfo[filesMap.size()];
        // Has to be the values, keys dont get overwritten on putAll !
        filesMap.values().toArray(allFiles);

        // log().warn(
        // this + ": Has " + allFiles.length + " total files, size: "
        // + Util.formatBytes(Util.calculateSize(allFiles, true))
        // + ", local size: "
        // + Util.formatBytes(Util.calculateSize(files, true)));

        return allFiles;
    }

    /**
     * Returns all members
     * 
     * @return
     */
    public Member[] getMembers() {
        synchronized (members) {
            Member[] membersArr = new Member[members.size()];
            members.toArray(membersArr);
            return membersArr;
        }
    }

    public Member[] getConnectedMembers() {
        List<Member> connected = new ArrayList<Member>(members.size());
        synchronized (members) {
            for (Member member : members) {
                if (member.isConnected()) {
                    if (member.isMySelf()) {
                        continue;
                    }
                    connected.add(member);
                }
            }
        }
        Member[] asArray = new Member[connected.size()];
        return connected.toArray(asArray);
    }

    /**
     * Answers if that member is on this folder
     * 
     * @param member
     * @return
     */
    public boolean hasMember(Member member) {
        if (members == null) { // FIX a rare NPE at startup
            return false;
        }
        if (member == null) {
            return false;
        }
        return members.contains(member);
    }

    /**
     * Answers the number of members
     * 
     * @return
     */
    public int getMembersCount() {
        return members.size();
    }

    /**
     * Answers if folder has this file
     * 
     * @param fInfo
     * @return
     */
    public boolean hasFile(FileInfo fInfo) {
        return knownFiles.containsKey(fInfo);
    }

    /**
     * Gets a file by fileinfo
     * 
     * @param fInfo
     * @return
     */
    public FileInfo getFile(FileInfo fInfo) {
        return knownFiles.get(fInfo);
    }

    /**
     * Returns the local file from a file info Never returns null, file MAY NOT
     * exist!! check before use
     * 
     * @param fInfo
     * @return
     */
    public File getDiskFile(FileInfo fInfo) {
        Object object = diskFileCache.get(fInfo);
        if (object != null) {
            return (File) object;
        }
        File diskFile = new File(localBase, fInfo.getName());
        diskFileCache.put(fInfo, diskFile);
        return diskFile;
    }

    /**
     * Returns the globally unique folder ID, generate once at folder creation
     * 
     * @return
     */
    public String getId() {
        return currentInfo.id;
    }

    /**
     * Returns a folder info object
     * 
     * @return
     */
    public FolderInfo getInfo() {
        return currentInfo;
    }

    /**
     * Returns the statistic for this folder
     * 
     * @return
     */
    public FolderStatistic getStatistic() {
        return statistic;
    }

    /** returns an Invitation to this folder */
    public Invitation getInvitation() {
        return new Invitation(getInfo(), getController().getMySelf().getInfo());
    }

    public String toString() {
        return getInfo().toString();
    }

    // Logger methods *********************************************************

    public String getLoggerName() {
        return "Folder '" + getName() + "'";
    }

    // UI-Swing methods *******************************************************

    /**
     * Returns the treenode representation of this object
     * 
     * @return
     */
    public MutableTreeNode getTreeNode() {
        if (treeNode == null) {
            // Initalize treenode now lazily
            treeNode = new TreeNodeList(this, getController()
                .getFolderRepository().getJoinedFoldersTreeNode());
            // treeNode.sortBy(MemberComparator.IN_GUI);

            // first make sure we have a fresh copy
            rootDirectory = getDirectory0(true);// 

            // Now sort
            treeNode.sort();
        } // else {
        // getDirectory();
        // }

        return treeNode;
    }

    /**
     * Sets the parent of folders treenode
     * 
     * @param parent
     */
    public void setTreeNodeParent(MutableTreeNode parent) {
        treeNode.setParent(parent);
    }

    // *************** Event support
    public void addMembershipListener(FolderMembershipListener listener) {
        ListenerSupportFactory.addListener(folderMembershipListenerSupport,
            listener);
    }

    public void removeMembershipListener(FolderMembershipListener listener) {
        ListenerSupportFactory.removeListener(folderMembershipListenerSupport,
            listener);
    }

    public void addFolderListener(FolderListener listener) {
        ListenerSupportFactory.addListener(folderListenerSupport, listener);
    }

    public void removeFolderListener(FolderListener listener) {
        ListenerSupportFactory.removeListener(folderListenerSupport, listener);
    }

    private void fireMemberJoined(Member member) {
        FolderMembershipEvent folderMembershipEvent = new FolderMembershipEvent(
            this, member);
        folderMembershipListenerSupport.memberJoined(folderMembershipEvent);
    }

    private void fireMemberLeft(Member member) {
        FolderMembershipEvent folderMembershipEvent = new FolderMembershipEvent(
            this, member);
        folderMembershipListenerSupport.memberLeft(folderMembershipEvent);
    }

    private void fireFolderChanged() {
        // log().debug("fireChanged: " + this);
        FolderEvent folderEvent = new FolderEvent(this);
        folderListenerSupport.folderChanged(folderEvent);
    }

    private void fireSyncProfileChanged() {
        FolderEvent folderEvent = new FolderEvent(this);
        folderListenerSupport.syncProfileChanged(folderEvent);
    }

    private void fireRemoteContentsChanged() {
        // log().debug("fireRemoteContentsChanged: " + this);
        FolderEvent folderEvent = new FolderEvent(this);
        folderListenerSupport.remoteContentsChanged(folderEvent);
    }

    /** package protected because fired by FolderStatistics */
    void fireStatisticsCalculated() {
        FolderEvent folderEvent = new FolderEvent(this);
        folderListenerSupport.statisticsCalculated(folderEvent);
    }
}