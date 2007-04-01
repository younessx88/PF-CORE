package de.dal33t.powerfolder.message;

import de.dal33t.powerfolder.light.FileInfo;

/**
 * Message to indicate that the upload was aborted. The remote side should stop
 * the download.
 * 
 * @author <a href="mailto:totmacher@powerfolder.com">Christian Sprajc </a>
 * @version $Revision: 1.2 $
 */
public class AbortUpload extends Message {
    private static final long serialVersionUID = 100L;

    public FileInfo file;

    public AbortUpload(FileInfo file) {
        this.file = file;
    }

    public String toString() {
        return "Abort download of: " + file;
    }
}