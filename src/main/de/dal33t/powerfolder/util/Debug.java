/* $Id: Debug.java,v 1.30 2006/04/23 18:14:41 bytekeeper Exp $
 */
package de.dal33t.powerfolder.util;

import static de.dal33t.powerfolder.disk.FolderSettings.FOLDER_SETTINGS_ID;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

import de.dal33t.powerfolder.Controller;
import de.dal33t.powerfolder.Member;
import de.dal33t.powerfolder.disk.Folder;
import de.dal33t.powerfolder.light.FileInfo;
import de.dal33t.powerfolder.light.MemberInfo;
import de.dal33t.powerfolder.message.Identity;
import de.dal33t.powerfolder.message.NodeInformation;
import de.dal33t.powerfolder.transfer.Download;
import de.dal33t.powerfolder.transfer.DownloadManager;
import de.dal33t.powerfolder.transfer.TransferManager;
import de.dal33t.powerfolder.transfer.Upload;
import de.dal33t.powerfolder.util.compare.DiskItemComparator;
import de.dal33t.powerfolder.util.compare.MemberComparator;

/**
 * Utility class with methods for debugging
 * 
 * @author <a href="mailto:totmacher@powerfolder.com">Christian Sprajc </a>
 * @version $Revision: 1.30 $
 */
public class Debug {
    private static final Logger LOG = Logger.getLogger(Debug.class);
    private static final DateFormat MODIFIED_DATE_FORMAT = new SynchronizedDateFormat(
        new SimpleDateFormat("dd-MM-yyyy HH:mm"));

    private Debug() {
        // No instance allowed
    }

    /**
     * Writes a list of files to disk for debuging info. Sorted
     * 
     * @deprecated use {@link #writeFileListCSV(Collection, String, File)}
     *             instead.
     * @param fileInfos
     * @param header
     * @param logFile
     * @return
     */
    public static boolean writeFileList(Collection<FileInfo> fileInfos,
        String header, File logFile)
    {
        if (logFile == null) {
            throw new NullPointerException("Logfile is null");
        }
        if (fileInfos == null) {
            throw new NullPointerException("Files are null");
        }
        if (!logFile.exists()) {
            try {
                logFile.getParentFile().mkdirs();
                logFile.createNewFile();
            } catch (IOException e) {
                LOG.error("Unable to write filelist to "
                    + logFile.getAbsolutePath());
                LOG.verbose(e);
                return false;
            }
        }
        if (!logFile.canWrite()) {
            LOG.error("Unable to write filelist to "
                + logFile.getAbsolutePath());
            return false;
        }

        // Copy & Sort
        FileInfo[] list = new FileInfo[fileInfos.size()];
        fileInfos.toArray(list);
        Arrays.sort(list, new DiskItemComparator(DiskItemComparator.BY_NAME));

        try {
            LOG.verbose("Writing log list debug file: "
                + logFile.getAbsolutePath());
            OutputStream fOut = new BufferedOutputStream(new FileOutputStream(
                logFile));
            fOut.write(header.getBytes());
            fOut.write("\r\n".getBytes());

            for (int i = 0; i < list.length; i++) {
                fOut.write((list[i].toDetailString() + "\r\n").getBytes());
            }

            fOut.flush();
            fOut.close();
            return true;
        } catch (Exception e) {
            LOG.error("Unable to write filelist to "
                + logFile.getAbsolutePath());
            LOG.verbose(e);
        }
        return false;
    }

    /**
     * Writes a list of files to disk as CSV file.
     * 
     * @param fileInfos
     * @param header
     * @param logFile
     * @return true if the write succeeded
     */
    public static boolean writeFileListCSV(Collection<FileInfo> fileInfos,
        String header, File logFile)
    {
        if (logFile == null) {
            throw new NullPointerException("Logfile is null");
        }
        if (fileInfos == null) {
            throw new NullPointerException("Files are null");
        }
        if (!logFile.exists()) {
            try {
                logFile.getParentFile().mkdirs();
                logFile.createNewFile();
            } catch (IOException e) {
                LOG.error("Unable to write filelist to "
                    + logFile.getAbsolutePath());
                LOG.verbose(e);
                return false;
            }
        }
        if (!logFile.canWrite()) {
            LOG.error("Unable to write filelist to "
                + logFile.getAbsolutePath());
            return false;
        }

        // Copy & Sort
        FileInfo[] list = fileInfos.toArray(new FileInfo[0]);
        Arrays.sort(list, new DiskItemComparator(
            DiskItemComparator.BY_MODIFIED_DATE));

        try {
            OutputStream fOut = new BufferedOutputStream(new FileOutputStream(
                logFile));
            fOut.write(("# " + header + "\n\n").getBytes("UTF-8"));
            fOut.write("Change time      ;Filename;Changer;Size;Version\n\n"
                .getBytes());
            for (int i = 0; i < list.length; i++) {
                fOut.write(toCSVLine(list[i]).getBytes("UTF-8"));
            }
            fOut.close();
        } catch (IOException e) {
            LOG.warn("Unable to write nodelist to '"
                + logFile.getAbsolutePath() + "'");
            LOG.verbose(e);
        }

        return false;
    }

    /**
     * Details infos about the fileinfo to a comma separated line.
     * 
     * @param b
     * @param m
     */
    private static String toCSVLine(FileInfo f) {
        Reject.ifNull(f, "FileInfo is null");
        StringBuffer b = new StringBuffer();

        synchronized (MODIFIED_DATE_FORMAT) {
            b.append(f.getModifiedDate() != null ? MODIFIED_DATE_FORMAT
                .format(f.getModifiedDate()) : "-");
        }
        b.append(" ;");

        if (f.isDeleted()) {
            b.append("(del) ");
        }
        b.append(f.getName());
        b.append(";");

        b.append(f.getModifiedBy().nick);
        b.append(";");

        b.append(Format.formatBytes(f.getSize()));
        b.append(";");

        b.append(f.getVersion());
        b.append("\n");

        return b.toString();
    }

    /**
     * Builds a debug report for remote analyse
     * 
     * @param controller
     * @return
     */
    public static String buildDebugReport(Controller c) {
        if (c == null) {
            throw new NullPointerException("Controller is null");
        }
        synchronized (c) {
            StringBuffer b = new StringBuffer();
            b.append("PowerFolder debug report\n");
            b.append("------------------------");
            // information about myself and local port binding

            long uptimeMinutes = c.getUptime() / 1000 / 60;

            b.append("\nVersion: " + Controller.PROGRAM_VERSION + " ("
                + c.getBuildTime() + ")");
            b.append("\nConfig: " + c.getConfigName());
            b.append("\nCurrent time: " + new Date());
            b.append("\nLocale: " + Locale.getDefault() + " ("
                + Locale.getDefault().getDisplayCountry() + ")");
            b.append("\nUptime: " + uptimeMinutes + " minutes");
            b.append("\nOS: " + System.getProperty("os.name"));
            b.append("\nJava: " + System.getProperty("java.version") + " ("
                + System.getProperty("java.runtime.version") + ", "
                + System.getProperty("java.vendor") + ")");

            b.append("\nNetworking mode: ");
            b.append(c.getNetworkingMode().name());

            double upKBS = c.getTransferManager()
                .getTotalUploadTrafficCounter().calculateCurrentKBS();
            double downKBS = c.getTransferManager()
                .getTotalDownloadTrafficCounter().calculateCurrentKBS();
            long upBytes = c.getTransferManager()
                .getTotalUploadTrafficCounter().getBytesTransferred();
            long downBytes = c.getTransferManager()
                .getTotalDownloadTrafficCounter().getBytesTransferred();
            b.append("\nTotal traffic: down ("
                + Format.NUMBER_FORMATS.format(downKBS) + " Kbytes/s, "
                + Format.formatBytes(downBytes) + " bytes total), up "
                + Format.NUMBER_FORMATS.format(upKBS) + " Kbytes/s, "
                + Format.formatBytes(upBytes) + " bytes total)");

            if (c.isLimitedConnectivity()) {
                b.append("\nWARNING: Has limited connectivity");
            }

            b.append("\n\nListener status: ");
            if (c.hasConnectionListener()) {
                b.append("Listening on ");
                b.append(c.getConnectionListener().getAddress());
                if (c.getMySelf() != null
                    && c.getMySelf().getInfo().isSupernode)
                {
                    b.append(", acting as supernode");
                } else {
                    b.append(", acting as standardnode");
                }
                b.append("\n");
            } else {
                b.append("Not listening on a local port\n");
            }
            b.append("MySelf: ");
            addDetailInfo(b, c.getMySelf());

            b.append("\n");

            if (c.isStarted()) {
                // All folders
                Folder[] folders = c.getFolderRepository().getFolders();

                b.append("\nFolders (" + folders.length + " joined)");
                for (int i = 0; i < folders.length; i++) {
                    b.append("\n ");
                    Folder folder = folders[i];
                    addDetailInfo(b, folder);
                }
                b.append("\n");
                if (folders.length == 0) {
                    b.append(" (none)\n");
                }

                TransferManager tm = c.getTransferManager();
                // dump transfers
                Collection<DownloadManager> downloads = c.getTransferManager()
                    .getActiveDownloads();
                b.append("\nDownloads ("
                    + downloads.size()
                    + " total, "
                    + Format.NUMBER_FORMATS.format(tm.getDownloadCounter()
                        .calculateCurrentKBS())
                    + " Kbytes/s, "
                    + Format.formatBytes(tm.getDownloadCounter()
                        .getBytesTransferred()) + " bytes total):");
                for (DownloadManager man : downloads) {
                    for (Download dl: man.getSources()) {
                        b.append("\n ");
                        b.append(dl.isStarted() ? "(active)" : (dl.isQueued()
                            ? "(queued)"
                            : "(requested)"));
                        b.append(" " + dl);
                    }
                }
                b.append("\n");
                if (downloads.size() == 0) {
                    b.append(" (none)\n");
                }

                Upload[] actULs = tm.getActiveUploads();
                Upload[] quedULs = tm.getQueuedUploads();
                b.append("\nUploads ("
                    + tm.getAllowedUploads()
                    + " allowed, "
                    + actULs.length
                    + " active, "
                    + quedULs.length
                    + " queued, "
                    + Format.NUMBER_FORMATS.format(tm.getUploadCounter()
                        .calculateCurrentKBS())
                    + " Kbytes/s, "
                    + Format.NUMBER_FORMATS.format(tm
                        .getAllowedUploadCPSForWAN() / 1024)
                    + " Kbyte/s allowed, "
                    + Format.formatBytes(tm.getUploadCounter()
                        .getBytesTransferred()) + " bytes total):");

                List<Upload> uploads = new ArrayList<Upload>(actULs.length
                    + quedULs.length);
                uploads.addAll(Arrays.asList(actULs));
                uploads.addAll(Arrays.asList(quedULs));
                for (Iterator it = uploads.iterator(); it.hasNext();) {
                    Upload upload = (Upload) it.next();
                    b.append("\n ");
                    b.append(upload.isStarted() ? "(active)" : "(queued)");
                    b.append(" " + upload);
                }
                b.append("\n");
                if (actULs.length == 0 && quedULs.length == 0) {
                    b.append(" (none)\n");
                }

                // all members
                Member[] knownMembers = c.getNodeManager()
                    .getNodesAsCollection().toArray(new Member[0]);
                // Sort
                Arrays.sort(knownMembers, MemberComparator.IN_GUI);
                b.append("\nAll online nodes ("
                    + c.getNodeManager().countConnectedNodes() + " connected, "
                    + c.getNodeManager().countOnlineNodes() + " online, "
                    + c.getNodeManager().getNodesAsCollection().size()
                    + " known, " + c.getNodeManager().countSupernodes()
                    + " supernodes, " + c.getNodeManager().countFriends()
                    + " friend(s)):");
                for (int i = 0; i < knownMembers.length; i++) {
                    if (knownMembers[i].isConnectedToNetwork()) {
                        b.append("\n ");
                        addDetailInfo(b, knownMembers[i]);
                    }
                }
                b.append("\n");
                if (knownMembers.length == 0) {
                    b.append(" (none)\n");
                }
            } else {
                b.append("Controller NOT started yet\n");
            }

            // config dump, even if controller is not started yet
            b.append("\nConfig:");
            Properties config = (Properties) c.getConfig().clone();
            // Sort config by name
            List sortedConfigKeys = new ArrayList(config.keySet());
            Collections.sort(sortedConfigKeys);
            for (Iterator it = sortedConfigKeys.iterator(); it.hasNext();) {
                String key = (String) it.next();
                String value = config.getProperty(key);
                // Erase folder ids, keep it secret!
                if (key.indexOf(FOLDER_SETTINGS_ID) >= 5) {
                    value = "XXX-erased-XXX";
                }
                // Erase all passwords
                if (key.toLowerCase().indexOf("password") != -1) {
                    value = "XXX-erased-XXX";
                }
                if (key.toLowerCase().indexOf("license") != -1) {
                    value = "XXX-erased-XXX";
                }
                b.append("\n   " + key + " = " + value);
            }
            b.append("\n");

            /*
             * b.append("\nFolder details:"); for (int i = 0; i <
             * folders.length; i++) { b.append("\n "); addFullInfo(b,
             * c.getFolderRepository().getFolder(folders[i])); } b.append("\n");
             */

            return b.toString();
        }
    }

    /**
     * Adds a detailed info about the member to the buffer
     * 
     * @param b
     * @param m
     */
    private static void addDetailInfo(StringBuffer b, Member m) {
        if (b == null || m == null) {
            return;
        }
        b.append(toDetailInfo(m));
    }

    /**
     * Details infos about the member.
     * 
     * @param b
     * @param m
     */
    private static String toDetailInfo(Member m) {
        Reject.ifNull(m, "Member is null");
        StringBuffer b = new StringBuffer();
        if (m.isMySelf()) {
            b.append("(myself) ");
        } else if (m.isConnected()) {
            if (m.isOnLAN()) {
                b.append("(local)  ");
            } else {
                b.append("(i-net)  ");
            }
        } else if (m.isConnectedToNetwork()) {
            b.append("(online) ");
        } else {
            b.append("(offl.)  ");
        }
        if (m.getInfo().isSupernode) {
            b.append("(s) ");
        }
        b.append(m);
        Identity id = m.getIdentity();
        b.append(", ver. " + (id != null ? id.getProgramVersion() : "-")
            + ", ID: " + m.getId());
        b.append(", reconnect address " + m.getReconnectAddress());
        return b.toString();
    }

    /**
     * Details infos about the member ad a comma separated line.
     * 
     * @param b
     * @param m
     */
    private static String toCSVLine(Member m) {
        Reject.ifNull(m, "Member is null");
        StringBuffer b = new StringBuffer();

        if (m.isMySelf()) {
            b.append("myself");
        } else if (m.isConnected()) {
            if (m.isOnLAN()) {
                b.append("connected (local)");
            } else {
                b.append("connected (i-net)");
            }
        } else if (m.isConnectedToNetwork()) {
            b.append("online");
        } else {
            b.append("offline");
        }

        b.append(";");
        if (m.getInfo().isSupernode) {
            b.append("s");
        } else {
            b.append("n");
        }

        b.append(";");
        b.append(m.getNick());

        b.append(";" + m.getId());

        b.append(";");
        Identity id = m.getIdentity();
        b.append((id != null ? id.getProgramVersion() : "-"));

        b.append(";" + m.getReconnectAddress());
        b.append(";" + m.getLastConnectTime());
        b.append(";" + m.getLastNetworkConnectTime());
        return b.toString();
    }

    /**
     * Adds detailed info about the folder to buffer
     * 
     * @param b
     * @param f
     */
    private static void addDetailInfo(StringBuffer b, Folder f) {
        if (b == null || f == null) {
            return;
        }
        b.append(f);
        if (f.isSecret()) {
            b.append(", ID: XXX-erased-XXX");
        } else {
            b.append(", ID: " + f.getId());
        }
        b.append(", files: " + f.getKnownFilesCount() + ", size: "
            + Format.formatBytes(f.getInfo().bytesTotal) + ", members: "
            + f.getMembers().length + ", sync: " + f.getSyncProfile().getProfileName());
    }

    /**
     * Writes debug report to disk.
     * 
     * @see #loadDebugReport(MemberInfo)
     * @param nodeInfo
     */
    public static boolean writeNodeInformation(NodeInformation nodeInfo) {
        if (nodeInfo == null) {
            throw new NullPointerException("NodeInfo is null");
        }

        String fileName;
        if (nodeInfo.node != null) {
            fileName = "Node."
                + Util.removeInvalidFilenameChars(nodeInfo.node.nick)
                + ".report.txt";
        } else {
            fileName = "Node.-unknown-.report.txt";
        }
        try {
            // Create in debug directory
            // Create dir
            File dir = new File(Logger.getDebugDir(), "nodeinfos");
            dir.mkdirs();
            OutputStream fOut = new BufferedOutputStream(new FileOutputStream(
                new File(dir, fileName)));
            fOut.write(nodeInfo.debugReport.getBytes());
            fOut.close();
            return true;
        } catch (IOException e) {
            LOG.error(e);
        }
        return false;
    }

    /**
     * Loads a stored debug report from disk for that node
     * 
     * @see #writeNodeInformation(NodeInformation)
     * @param node
     * @return
     */
    public static String loadDebugReport(MemberInfo node) {
        Reject.ifNull(node, "Node is null");
        String fileName = "Node." + node.nick + ".report.txt";
        try {
            File file = new File(Logger.getDebugDir(), "nodeinfos/" + fileName);
            InputStream fIn = new BufferedInputStream(new FileInputStream(file));

            byte[] buffer = new byte[(int) file.length()];
            fIn.read(buffer);
            return new String(buffer);
        } catch (IOException e) {
            LOG.warn("Debug report for " + node.nick + " not found ("
                + fileName + ")");
            // LOG.verbose(e);
        }
        return null;
    }

    /**
     * Writes a list of nodes to a debut output file.
     * 
     * @param nodes
     *            the list of nodes
     * @param fileName
     *            the filename to write to
     */
    public static void writeNodeList(Collection<Member> nodes, String fileName)
    {
        Reject.ifNull(nodes, "Nodelist is null");
        try {
            OutputStream fOut = new BufferedOutputStream(new FileOutputStream(
                new File(Logger.getDebugDir(), fileName)));
            for (Member node : nodes) {
                fOut.write(Debug.toDetailInfo(node).getBytes());
                fOut.write("\n".getBytes());
            }
            fOut.close();
        } catch (IOException e) {
            LOG.warn("Unable to write nodelist to '" + fileName + "'");
            LOG.verbose(e);
        }
    }

    /**
     * Writes a list of nodes to a debut output file in csv format.
     * 
     * @param nodes
     *            the list of nodes
     * @param fileName
     *            the filename to write to
     */
    public static void writeNodeListCSV(Collection<Member> nodes,
        String fileName)
    {
        Reject.ifNull(nodes, "Nodelist is null");
        try {
            OutputStream fOut = new BufferedOutputStream(new FileOutputStream(
                new File(Logger.getDebugDir(), fileName)));
            fOut
                .write("connect;supernode;nick;id;version;address;last connect time;last online time\n"
                    .getBytes());
            synchronized (nodes) {
                for (Member node : nodes) {
                    fOut.write(toCSVLine(node).getBytes());
                    fOut.write("\n".getBytes());
                }
            }
            fOut.close();
        } catch (IOException e) {
            LOG.warn("Unable to write nodelist to '" + fileName + "'");
            LOG.verbose(e);
        }
    }

    /**
     * Writes statistics to disk
     * 
     * @param c
     */
    public static void writeStatistics(Controller c) {
        OutputStream fOut = null;
        try {
            fOut = new BufferedOutputStream(new FileOutputStream(c
                .getConfigName()
                + ".netstat.csv", true));
            Date now = new Date();
            String statLine = Format.getFullDateFormat().format(now) + ";"
                + now.getTime() + ";"
                + c.getNodeManager().countConnectedNodes() + ";"
                + c.getNodeManager().countOnlineNodes() + ";"
                + c.getNodeManager().getNodesAsCollection().size() + "\n";
            fOut.write(statLine.getBytes());
        } catch (IOException e) {
            LOG.warn("Unable to write network statistics file", e);
            // Ignore
        } finally {
            try {
                fOut.close();
            } catch (Exception e) {
                // ignore
            }
        }
    }

    public static void dumpThreadStacks() {
        ThreadGroup top = Thread.currentThread().getThreadGroup();
        while (top.getParent() != null) {
            top = top.getParent();
        }
        showGroupInfo(top);
    }
    
    private static String detailedObjectState0(Class<?> c, Object o) {
        if (c == Object.class) {
            return "";
        }
        
        StringBuffer buffer = new StringBuffer();
        buffer.append(detailedObjectState0(c.getSuperclass(), o));
        
        Field[] fields = c.getDeclaredFields();
        for (Field fld: fields) {
            fld.setAccessible(true);
            buffer.append("; [").append("Field: ").append(fld.getName());
            buffer.append(", toString: ");
            try {
                Object value = fld.get(o);
                buffer.append('(').append(value).append(')');
            } catch (IllegalArgumentException e) {
                buffer.append(e);
            } catch (IllegalAccessException e) {
                buffer.append(e);
            }
            buffer.append("]");
            fld.setAccessible(false);
        }
        return buffer.toString();
        
    }
    
    public static String detailedObjectState(Object o) {
        StringBuffer buffer = new StringBuffer();
        buffer.append("Class: ").append(o.getClass().getName());
        buffer.append(detailedObjectState0(o.getClass(), o));
        return buffer.toString();
    }

    private static void showGroupInfo(ThreadGroup group) {
        Thread threads[] = new Thread[group.activeCount()];
        group.enumerate(threads, false);
        LOG.debug("");
        LOG.debug(group + " ########################");

        for (int i = 0; i < threads.length; i++) {
            if (threads[i] != null) {
                LOG.debug(" " + threads[i]
                    + " --------------------------------------");
                dumpStackTrace(threads[i]);
                LOG.debug("");
            }
        }
        ThreadGroup activeGroup[] = new ThreadGroup[group.activeGroupCount()];
        group.enumerate(activeGroup, false);

        for (int i = 0; i < activeGroup.length; i++) {
            showGroupInfo(activeGroup[i]);
        }
    }

    private static final void dumpStackTrace(Thread t) {
        for (StackTraceElement te : t.getStackTrace()) {
            LOG.debug("  " + te);
        }
    }
}
