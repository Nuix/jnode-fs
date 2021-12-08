package org.jnode.fs.logger;

import org.apache.log4j.PropertyConfigurator;
import org.apache.log4j.Logger;
import java.io.*;
import java.util.Properties;

public class Logger_File{

    private Properties config_file;
    private Logger logger;
    private final String log_file_url = "src/main/java/org/jnode/fs/logger/logs/";

    private final int ERROR_LOGGER = 1;
    private final int INFO_LOGGER = 2;

    public Logger_File(){
        File prop_file = new File("src/main/java/org/jnode/fs/logger/log4j.properties");
        config_file = new Properties();
        try{
            InputStream input = new FileInputStream(prop_file);
            config_file.load(input);
            logger = Logger.getLogger(Logger_File.class);
            PropertyConfigurator.configure("src/main/java/org/jnode/fs/logger/log4j.properties");
        }
        catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    //if we want to get a different path for the log file...
    public void set_file_path(String path_for_file){
        if (path_for_file == null || path_for_file.equals("")){
            config_file.setProperty("log4j.appender.file.File", log_file_url + path_for_file);
        }
        else{
            config_file.setProperty("log4j.appender.file.File", log_file_url + path_for_file);
        }
    }

    //there is for get the file path from the properties file...
    public String get_file_path(){
        String file_path = "";
        if (config_file != null) {
                //WE TRY TO READ LOG PATH...
                file_path = config_file.getProperty("log4j.appender.file.File");
        }
        return file_path;
    }

    //there is for generating the log file...
    public void write_to_file(int type, String message){
        try{
            if(type == ERROR_LOGGER) {
                //WE TRY TO WRITE ERROR...
                logger.error(message);
            }
            else if (type == INFO_LOGGER) {
                //WE TRY TO WRITE INFO...
                logger.info(message);
            }
            else {
                //WE TRY TO WRITE DEBUG...
                logger.debug(message);
            }
        }catch(Exception e){
            e.printStackTrace();
        }
    }
}
