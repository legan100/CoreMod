package de.niconagel.coremod.utils;

import de.niconagel.coremod.utils.filemanager.DirectoryHandler;
import de.niconagel.coremod.utils.filemanager.FileManager;
import lombok.Getter;

public class SetupManager {

    private DirectoryHandler directoryHandler;
    private FileManager restFileManager;
    private FileManager serverFileManager;

    @Getter private static SetupManager instance;
    private static int numbersSetupManager = 0;

    public SetupManager() {
        if (numbersSetupManager >= 1) return;
        numbersSetupManager++;
        instance = this;
    }

    public void onSetup() {
        directoryHandler = new DirectoryHandler();
        directoryHandler.generateDirectory("CoreMod");

        restFileManager = new FileManager("CoreMod/api.yml");

        if (!restFileManager.exists()) {
            restFileManager.createFile();
            restFileManager.writeInNextFreeLine("apikey: null");
            restFileManager.writeInNextFreeLine("host: https://tfs-in-der-blockwelt.themineattack.de");
            restFileManager.writeInNextFreeLine("port: 443");
            restFileManager.writeInNextFreeLine("client-public-key: null");
        }

        serverFileManager = new FileManager("CoreMod/server.yml");

        if (!serverFileManager.exists()) {
            serverFileManager.createFile();
            serverFileManager.writeInNextFreeLine("tablistheader: null");
            serverFileManager.writeInNextFreeLine("tablistfooter: null");
        }
    }

    public FileManager getRestFileManager() {
        return restFileManager;
    }

    public FileManager getServerFileManager() {
        return serverFileManager;
    }

    public DirectoryHandler getDirectoryHandler() {
        return directoryHandler;
    }
}
