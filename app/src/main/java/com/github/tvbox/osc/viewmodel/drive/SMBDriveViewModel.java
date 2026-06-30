package com.github.tvbox.osc.viewmodel.drive;

import com.github.tvbox.osc.bean.DriveFolderFile;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import jcifs.smb.NtlmPasswordAuthentication;
import jcifs.smb.SmbFile;

public class SMBDriveViewModel extends AbstractDriveViewModel {

    @Override
    public String loadData(LoadDataCallback callback) {
        JsonObject config = currentDrive.getConfig();
        if (currentDriveNote == null) {
            currentDriveNote = new DriveFolderFile(null,
                    config.has("initPath") ? config.get("initPath").getAsString() : "", 0, false, null, null);
        }
        String targetPath = currentDriveNote.getAccessingPathStr() + currentDriveNote.name;
        if (currentDriveNote.getChildren() == null) {
            new Thread() {
                public void run() {
                    try {
                        String baseUrl = config.get("url").getAsString();
                        String fullUrl = baseUrl + targetPath;
                        String username = config.has("username") ? config.get("username").getAsString() : "";
                        String password = config.has("password") ? config.get("password").getAsString() : "";

                        NtlmPasswordAuthentication auth = new NtlmPasswordAuthentication(null, username, password);
                        SmbFile smbFile = new SmbFile(fullUrl, auth);
                        SmbFile[] files = smbFile.listFiles();

                        List<DriveFolderFile> items = new ArrayList<>();
                        if (files != null) {
                            for (SmbFile file : files) {
                                String name = file.getName();
                                // Skip . and .. entries
                                if (".".equals(name) || "..".equals(name))
                                    continue;
                                boolean isDir = file.isDirectory();
                                int extNameStartIndex = name.lastIndexOf(".");
                                items.add(new DriveFolderFile(currentDriveNote, name, 0, !isDir,
                                        !isDir && extNameStartIndex >= 0 && extNameStartIndex < name.length() ?
                                                name.substring(extNameStartIndex + 1) : null,
                                        file.lastModified()));
                            }
                        }
                        sortData(items);
                        DriveFolderFile backItem = new DriveFolderFile(null, null, 0, false, null, null);
                        backItem.parentFolder = backItem;
                        items.add(0, backItem);
                        currentDriveNote.setChildren(items);
                        if (callback != null)
                            callback.callback(currentDriveNote.getChildren(), false);

                    } catch (Exception ex) {
                        if (callback != null)
                            callback.fail("无法访问SMB共享: " + ex.getMessage());
                    }
                }
            }.start();
            return targetPath;
        } else {
            sortData(currentDriveNote.getChildren());
            if (callback != null)
                callback.callback(currentDriveNote.getChildren(), true);
        }
        return targetPath;
    }
}
