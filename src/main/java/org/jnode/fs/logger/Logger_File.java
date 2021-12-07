package org.jnode.fs.logger;

import org.apache.log4j.Logger;
import java.io.*;
import java.util.Properties;

public class Logger_File{

    private Properties config_file;
    private Logger logger;
    private String log_file_url = "./logs/";

    final int ERROR_LOGGER = 1;
    final int INFO_LOGGER = 2;
    final int DEBUG_LOGGER = 3;

    public Logger_File(){
        File prop_file = new File("log_paths.prop");
        config_file = new Properties();

        try{
            InputStream input = new FileInputStream(prop_file);
            config_file.load(input);
            String file_path = config_file.getProperty("ERROR_LOG_FILE_PATH");
            System.out.println(file_path);
            logger = Logger.getLogger(Logger_File.class.getName());
        }
        catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    //if we want to get a different path for the log file...
    public void set_file_path(String path_for_file, int logger_type, String file_name){
        switch(logger_type){
            case ERROR_LOGGER:
                //error log
                config_file.setProperty("ERROR_LOG_FILE_PATH", log_file_url + file_name);
            case INFO_LOGGER:
                //info log
                config_file.setProperty("INFO_LOG_FILE_PATH", log_file_url + file_name);
            case DEBUG_LOGGER:
                //debug log
                config_file.setProperty("DEBUG_LOG_FILE_PATH", log_file_url + file_name);
            default:
                //warning log
                config_file.setProperty("WARNING_LOG_FILE_PATH", log_file_url + file_name);
        }
    }

    //there is for get the file path from the properties file...
    public String get_file_path(int logger_type){
        String file_path = "";
        try{
            switch (logger_type){
                case ERROR_LOGGER:
                    //WE TRY TO READ ERROR PATH...
                    file_path = config_file.getProperty("ERROR_LOG_FILE_PATH");
                case INFO_LOGGER:
                    //WE TRY TO READ INFO PATH...
                    file_path = config_file.getProperty("INFO_LOG_FILE_PATH");
                case DEBUG_LOGGER:
                    //WE TRY TO READ DEBUG PATH...
                    file_path = config_file.getProperty("DEBUG_LOG_FILE_PATH");
                default:
                    //WE TRY TO READ WARNING PATH...
                    file_path = config_file.getProperty("WARNING_LOG_FILE_PATH");
            }
        }catch(Exception e){
            e.printStackTrace();
        }

        return file_path;
    }

    //there is for generating the log file...
    public void generate_log_file(int type){
        String file_name = "";
        try{
            switch(type){
                case ERROR_LOGGER:
                    //try to generate the error logger......
                    file_name = "error.log";
                    log_file_url = log_file_url + file_name;
                    logger.trace("ERROR TRACE BEGINS HERE.");
                case INFO_LOGGER:
                    //try to generate the info logger......
                    file_name = "info.log";
                    log_file_url = log_file_url + file_name;
                    logger.trace("INFO TRACE BEGINS HERE.");
                case DEBUG_LOGGER:
                    //try to generate the debug logger......
                    file_name = "debug.log";
                    log_file_url = log_file_url + file_name;
                    logger.trace("DEBUG TRACE BEGINS HERE.");
                default:
                    //try to generate the debug logger......
                    file_name = "warning.log";
                    log_file_url = log_file_url + file_name;
                    logger.trace("WARNING TRACE BEGINS HERE.");
            }
        }catch(Exception e){
            e.printStackTrace();
        }
    }

    public static void main(String[] args){
        System.out.println("EXAMPLE MAIN METHOD.");
        Logger_File logger_object = new Logger_File();
        //System.out.println(logger_object.get_file_path(1));
        //System.out.println(logger_object.get_file_path(2));
        //System.out.println(logger_object.get_file_path(3));
        //System.out.println(logger_object.get_file_path(4));
    }
}
