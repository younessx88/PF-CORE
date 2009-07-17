/*
 * Copyright 2004 - 2009 Christian Sprajc. All rights reserved.
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
 * $Id: FileInfo.java 8381 2009-06-24 01:39:20Z tot $
 */
package de.dal33t.powerfolder.light;

import java.io.Serializable;

import de.dal33t.powerfolder.security.Account;

/**
 * Leightweight refernce/info object to an {@link Account}
 * 
 * @author sprajc
 */
public class AccountInfo implements Serializable {

    private static final long serialVersionUID = 100L;

    private String oid;
    private String username;

    public AccountInfo(String oid, String username) {
        super();
        this.oid = oid;
        this.username = username;
    }

    public String getOID() {
        return oid;
    }

    public String getUsername() {
        return username;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((oid == null) ? 0 : oid.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        AccountInfo other = (AccountInfo) obj;
        if (oid == null) {
            if (other.oid != null)
                return false;
        } else if (!oid.equals(other.oid))
            return false;
        return true;
    }

    @Override
    public String toString() {
        return username + " (" + oid + ')';
    }
}
