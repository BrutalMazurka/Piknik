package pik.dal;

import pik.common.AppResources;
import jCommons.logging.LoggerFactory;
import jCommons.utils.StringUtils;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

public class EmvPropertyFile {
    public static final String FILE_NAME = "emv.properties";
    private static final String PROP_KEY_LAST_TMS = "emv_last_tms";
    private static final String PROP_KEY_LAST_CLOSURE = "emv_last_closure";
    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormat.forPattern("yyyyMMddHHmmss");

    private static final Path filePath;
    private static Properties props;

    static {
        filePath = Paths.get(AppResources.getConfigDirPath().toString(), FILE_NAME);
    }


    private EmvPropertyFile() {
    }

    public static void load() {

        props = new Properties();
        FileInputStream fis = null;
        try {
            fis = new FileInputStream(filePath.toFile());
            props.load(fis);
            fis.close();
        } catch (IOException e) {
            System.out.println("Error: EmvPropertyFile - loading config: " + e.getMessage());
            LoggerFactory.getDefaultLogger().fatal("EmvPropertyFile - loading config", e);
        } finally {
            try {
                if (fis != null) {
                    fis.close();
                }
            } catch (Exception e) {
                LoggerFactory.getDefaultLogger().fatal("EmvPropertyFile - closing stream", e);
            }
        }
    }

    public static DateTime getLastClosure() {
        String timestampStr = props.getProperty(PROP_KEY_LAST_CLOSURE, "");
        if (StringUtils.isNullOrBlank(timestampStr)) {
            return getNullDateTime();
        }

        try {
            return DATE_TIME_FORMAT.parseDateTime(timestampStr);
        } catch (Exception exc) {
            LoggerFactory.getDefaultLogger().fatal("EmvPropertyFile - parsing emv_last_closure=" + timestampStr, exc);
        }

        return getNullDateTime();
    }

    public static void saveLastClosure(DateTime timestamp) {
        props.setProperty(PROP_KEY_LAST_CLOSURE, DATE_TIME_FORMAT.print(timestamp));
        saveToFile();
    }

    public static DateTime getLastTms() {
        String timestampStr = props.getProperty(PROP_KEY_LAST_TMS, "");
        if (StringUtils.isNullOrBlank(timestampStr)) {
            return getNullDateTime();
        }

        try {
            return DATE_TIME_FORMAT.parseDateTime(timestampStr);
        } catch (Exception exc) {
            LoggerFactory.getDefaultLogger().fatal("EmvPropertyFile - parsing emv_last_tms=" + timestampStr, exc);
        }

        return getNullDateTime();
    }

    public static void saveLastTms(DateTime timestamp) {
        props.setProperty(PROP_KEY_LAST_TMS, DATE_TIME_FORMAT.print(timestamp));
        saveToFile();
    }

    private static DateTime getNullDateTime() {
        return new DateTime(2024, 1, 1, 0, 0, 0);
    }

    private static void saveToFile() {
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(filePath.toFile());

            props.store(fos, null);
            fos.flush();
        } catch (Exception e) {
            LoggerFactory.getDefaultLogger().fatal("Saving EmvPropertyFile", e);
        } finally {
            try {
                if (fos != null) {
                    fos.close();
                }
            } catch (Exception e) {
                LoggerFactory.getDefaultLogger().fatal("Saving EmvPropertyFile - closing stream", e);
            }
        }
    }
}
