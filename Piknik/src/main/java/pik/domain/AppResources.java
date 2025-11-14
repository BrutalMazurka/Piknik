package pik.domain;

import jCommons.config.AppConfig;
import jCommons.utils.StringUtils;

import java.nio.file.Path;
import java.nio.file.Paths;

public class AppResources {
    public static final String DIR_NAME_EPIS_MIRROR = "EpisMirror";
    public static final String DIR_NAME_ETHERNET = "Ethernet";
    public static final String DIR_NAME_NEW_VERSION = "NewVersion";
    public static final String DIR_NAME_SCRIPTS = "Scripts";
    public static final String DIR_NAME_APP_SCRIPTS = "ScriptsApp";
    public static final String DIR_NAME_TEMP = "Temp";
    public static final String DIR_NAME_UPDATE = "Update";

    public static final String DIR_NAME_APP = "App";
    public static final String DIR_NAME_CONFIG = "Config";
    public static final String DIR_NAME_DATA = "Data";
    public static final String DIR_NAME_DB = "DB";
    public static final String DIR_NAME_DZC = "DZC";
    public static final String DIR_NAME_DZC_CACHE = "Cache";
    public static final String DIR_NAME_DZC_DATA = "Data";
    public static final String DIR_NAME_DZC_DB = "DB";
    public static final String DIR_NAME_RESOURCES = "Resources";
    public static final String DIR_NAME_INSPECTION = "Inspection";
    public static final String DIR_NAME_FIRMWARE = "Firmware";
    public static final String DIR_NAME_GENERAL = "General";
    public static final String DIR_NAME_POS = "POS";
    public static final String DIR_NAME_PRINTING = "Printing";
    public static final String DIR_NAME_I18N = "I18n";
    public static final String DIR_NAME_PRINTER = "Printer";

    public static final String DIR_NAME_AUDIT = "Audit";
    public static final String DIR_NAME_APP_LOGS = "Logs";

    public static final String DIR_NAME_USB_EPIS_AUDIT = "EpisAudit";
    public static final String DIR_NAME_TIME_TABLES_DEFAULT = "DEFAULT";

    public static final String DB_FILE_NAME = "evk.sqlite";
    public static final String DZC_DB_FILE_NAME = "dzc.sqlite";

    private static final String evkRootDirPath = AppConfig.get("Dir_Root");
    private static final String userHomeDirPath = AppConfig.get("Dir_Home");
    private static final String systemTmpDirPath = AppConfig.get("Dir_tmp", "/tmp/");

    private AppResources() {
    }

    //***********************************************************
    //****************** USER - HOME ****************************
    //***********************************************************

    public static Path getSystemScriptsDirPath() {
        return Paths.get(userHomeDirPath, DIR_NAME_SCRIPTS);
    }

    public static Path getScriptsAppDirPath() {
        return Paths.get(userHomeDirPath, DIR_NAME_APP_SCRIPTS);
    }


    public static Path getTempDirPath() {
        return Paths.get(userHomeDirPath, DIR_NAME_TEMP);
    }

    public static Path getNewVersionDirPath() {
        return Paths.get(userHomeDirPath, DIR_NAME_NEW_VERSION);
    }

    public static Path getUpdateDirPath() {
        return Paths.get(userHomeDirPath, DIR_NAME_UPDATE);
    }

    public static Path getEpisMirrorDirPath() {
        return Paths.get(userHomeDirPath, DIR_NAME_EPIS_MIRROR);
    }

    public static Path getEthernetDirPath() {
        return Paths.get(userHomeDirPath, DIR_NAME_ETHERNET);
    }

    //***********************************************************
    //****************** EVK ************************************
    //***********************************************************

    public static String getEvkRootDirPath() {
        return evkRootDirPath;
    }

    public static Path getAppDirPath() {
        return Paths.get(evkRootDirPath, DIR_NAME_APP);
    }

    public static Path getConfigDirPath() {
        return Paths.get(evkRootDirPath, DIR_NAME_CONFIG);
    }

    public static Path getDataDirPath() {
        return Paths.get(evkRootDirPath, DIR_NAME_DATA);
    }

    public static Path getEvkDbFilePath() {
        return Paths.get(AppResources.getDbDirPath().toString(), AppResources.DB_FILE_NAME);
    }

    public static Path getDbDirPath() {
        return Paths.get(evkRootDirPath, DIR_NAME_DB);
    }

    public static Path getDzcDirPath() {
        return Paths.get(evkRootDirPath, DIR_NAME_DZC);
    }

    public static Path getResourcesDirPath() {
        return Paths.get(evkRootDirPath, DIR_NAME_RESOURCES);
    }

    public static Path getPrinterDirPath() {
        return Paths.get(evkRootDirPath, DIR_NAME_PRINTER);
    }

    public static Path getInspectionDirPath() {
        return Paths.get(getResourcesDirPath().toString(), DIR_NAME_INSPECTION);
    }

    public static Path getEvkDbFileBackupPath() {
        return Paths.get(systemTmpDirPath, AppResources.DB_FILE_NAME);
    }

    //***********************************************************
    //****************** EVK - App ******************************
    //***********************************************************

    public static Path getAppLogsDirPath() {
        return Paths.get(getAppDirPath().toString(), DIR_NAME_APP_LOGS);
    }

    public static Path getAuditDirPath() {
        return Paths.get(getAppDirPath().toString(), DIR_NAME_AUDIT);
    }

    public static Path getAuditFileLogsDirPath() {
        return Paths.get(getAuditDirPath().toString(), DIR_NAME_APP_LOGS);
    }

    //***********************************************************
    //****************** DATA ***********************************
    //***********************************************************

    public static Path getI18nDirPath() {
        String i18nDirOverride = AppConfig.get("I18nDir", "");
        if (!StringUtils.isNullOrBlank(i18nDirOverride)) {
            return Paths.get(i18nDirOverride);
        }

        return Paths.get(getDataDirPath().toString(), DIR_NAME_I18N);
    }


    public static Path getFirmwareDirPath() {
        return Paths.get(getDataDirPath().toString(), DIR_NAME_FIRMWARE);
    }

    public static Path getGeneralDirPath() {
        return Paths.get(getDataDirPath().toString(), DIR_NAME_GENERAL);
    }

    public static Path getPosDirPath() {
        return Paths.get(getDataDirPath().toString(), DIR_NAME_POS);
    }

    public static Path getPrintingDirPath() {
        return Paths.get(getDataDirPath().toString(), DIR_NAME_PRINTING);
    }

    //***********************************************************
    //****************** DZC ***********************************
    //***********************************************************

    public static Path getDzcCacheDirPath() {
        return Paths.get(getDzcDirPath().toString(), DIR_NAME_DZC_CACHE);
    }

    public static Path getDzcDataDirPath() {
        return Paths.get(getDzcDirPath().toString(), DIR_NAME_DZC_DATA);
    }

    public static Path getDzcDbDirPath() {
        return Paths.get(getDzcDirPath().toString(), DIR_NAME_DZC_DB);
    }

    public static Path getDzcDbFilePath() {
        return Paths.get(getDzcDbDirPath().toString(), AppResources.DZC_DB_FILE_NAME);
    }

    //***********************************************************
    //****************** Updater ********************************
    //***********************************************************

    public static Path getTempDirDataPath() {
        return Paths.get(getTempDirPath().toString(), DIR_NAME_DATA);
    }


}
