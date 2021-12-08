package org.jnode.fs.xfs;

import java.io.*;

public class MyXfsCreateOutput {
    private final int entry_limit = 1000;
    private String file_name;
    private String dir_name;
    private String[] entries;

    protected File output_entry;
    protected final int FILE = 1;
    protected final int DIRECTORY = 2;

    public MyXfsCreateOutput(String output_space) throws IOException {
        System.out.println("WE TRY TO WRITE THE OUTPUT...");
        if (output_space != null && !output_space.equals("")) {
            System.out.println("THE OUTPUT SPACE IS: " + output_space);
            initComponents(output_space);
        }
        else{
            System.out.println("THE OUTPUT SPACE IS: " + "");
            System.out.println("ERROR, WE DON'T HAVE OUTPUT SPACE.");
            initComponents("");
        }
    }

    private void initComponents(String output) throws IOException {
        if(output.equals("")){
            String Default = "./output/";
            output_entry = new File(Default);
            write_to_disk(true, output_entry, null);
        }
        else{
            output_entry = new File(output);
            write_to_disk(true, output_entry, null);
        }
        file_name = "";
        dir_name = "";

        entries = new String[entry_limit];
    }

    private int validate_file_type(String name){
        if (name.contains(".")){
            file_name = name;
            return FILE;
        }
        else{
            dir_name = name;
            return DIRECTORY;
        }
    }

    public void write_to_disk(boolean is_root, File file, byte[] bytes_to_write) throws IOException {
        if (is_root){
            System.out.println("WE ARE GOING TO WRITE...");
            dir_name = file.getName();
            if (validate_file_type(dir_name) == DIRECTORY){
                System.out.println("THE ROOT DIRECTORY WILL BE: " + dir_name);
                if(file.exists()){
                    System.out.println("THE ROOT DIRECTORY EXISTS!!!");
                }
                else{
                    System.out.println("THE ROOT DIRECTORY DOE'S NOT EXISTS!!!");
                    file.mkdir();
                }
            }
        }
        else{
            String root_dir = "output/";
            if (validate_file_type(file.getName()) == FILE){
                try{
                    System.out.println("FILE NAME: " + file_name);
                    final String file_route = root_dir + file.getPath();
                    file = new File(file_route);
                    if (bytes_to_write == null){
                        file.createNewFile();
                    }
                    else{
                        file.createNewFile();
                        FileOutputStream outputStream = new FileOutputStream(file_route);
                        outputStream.write(bytes_to_write);
                        outputStream.close();
                    }
                }
                catch(Exception e){
                    e.printStackTrace();
                }
            }
            else{
                final String dir_route = root_dir + file.getPath();
                System.out.println("FILE NAME: " + dir_name);
                file = new File(dir_route);
                if(file.exists()){
                    System.out.println("THE DIR EXISTS...");
                }
                else{
                    file.mkdir();
                }
            }
        }
    }
}
