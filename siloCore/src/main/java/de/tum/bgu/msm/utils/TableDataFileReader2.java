package de.tum.bgu.msm.utils;

/**
 * Created by kii on 9/15/2017.
 */
//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

/*
import com.pb.common.datafile.*;
import com.pb.common.datafile.FileType;
import com.pb.common.datafile.TableDataSet;
*/

import com.pb.common.datafile.*;

import java.io.File;
import java.io.IOException;

public abstract class TableDataFileReader2 extends TableDataReader {
    private File myDirectory;

    public TableDataFileReader2() {

    }

    //    public static com.pb.common.datafile.TableDataFileReader createReader(FileType type) {
//        com.pb.common.datafile.TableDataFileReader reader = null;
    public static TableDataFileReader2 createReader2(FileType type) {
        TableDataFileReader2 reader = null;
        if(!type.equals(FileType.CSV)) {
            throw new RuntimeException("Invalid file type: " + type);
        }
        reader = new CSVFileReader2();

        return (TableDataFileReader2) reader;
    }

    public static TableDataFileReader2 createReader2(File file) {
        TableDataFileReader2 reader = null;
        String fileName = file.getName();
        if(!fileName.endsWith(".csv")) {
            throw new RuntimeException("Could not determine file type for: " + fileName);
        }
        reader = new CSVFileReader2();

        return (TableDataFileReader2)reader;
    }

    public abstract TableDataSet readFile(File var1) throws IOException;

    public void setMyDirectory(File myDirectory) {
        this.myDirectory = myDirectory;
    }

    public File getMyDirectory() {
        return this.myDirectory;
    }
}