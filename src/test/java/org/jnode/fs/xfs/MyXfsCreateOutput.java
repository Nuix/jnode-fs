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

    public MyXfsCreateOutput(String output_space){
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

    private void initComponents(String output){
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

    private int validate_file_type(String File_name){
        if (File_name.contains(".")){
            System.out.println("IT IS A FILE!!!!");
            file_name = file_name;
            return FILE;
        }
        else{
            System.out.println("IT IS A DIRECTORY!!!!");
            dir_name = file_name;
            return DIRECTORY;
        }
    }

    private boolean write_to_disk(boolean is_root, String file_name, Byte[] bytes_to_write) throws IOException {
        if (is_root){
            System.out.print("WE ARE GOING TO WRITE...");
            dir_name = file_name;
            if (validate_file_type(dir_name) == DIRECTORY){
                File file_to_write = new File(dir_name);
                if(file_to_write.exists()){
                    System.out.println("THE ROOT DIRECTORY EXISTS!!!");
                }
                else{
                    System.out.println("THE ROOT DIRECTORY DOE'S NOT EXISTS!!!");
                    file_to_write.mkdir();
                }
            }
        }
        else{
            File file_to_write;
            String root_dir = "./output/";

            if (validate_file_type(file_name) == FILE){
                if(file_to_write.exists()){
                }
                else{
                    try{
                        FileOutputStream outputStream = new FileOutputStream(file_to_write);
                        outputStream.write(bytes_to_write);
                        outputStream.close();
                    }catch(Exception e){
                        e.printStackTrace();
                    }
                }
            }
            else{
                final String dir_route = root_dir + dir_name;
                file_to_write = new File(dir_route);
                if(file_to_write.exists()){
                    System.out.println("THE DIR EXISTS...");
                }
                else{
                    file_to_write.mkdir();
                }
            }
        }
    }
}
