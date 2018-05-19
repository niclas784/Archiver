package ru.mirea.archiver;

import org.junit.Test;
import ru.mirea.data.Packer;
import ru.mirea.data.PackerImpl;
import ru.mirea.data.Unpacker;
import ru.mirea.data.UnpackerImpl;

import java.io.*;
import java.util.ArrayList;
import java.util.Random;

import static org.junit.Assert.*;

public class UnpackTest {
    private Random r = new Random();

    @Test
    public void unpackTest() throws Exception {
        Packer packer = new PackerImpl();
        Unpacker unpacker = new UnpackerImpl();
        String path1 = new File(".").getCanonicalPath() + "\\" + "test1";
        String path2 = new File(".").getCanonicalPath() + "\\" + "test2";
        String path4 = new File(".").getCanonicalPath() + "\\" + "test3";
        String path5 = new File(".").getCanonicalPath() + "\\" + "test4";
        String path3 = new File(".").getCanonicalPath() + "\\" + "testing.afk";
        for (int i = 1; i < 50; i++){
            System.out.println(i);
            FileOutputStream fileOutputStream1 = new FileOutputStream(path1);
            FileOutputStream fileOutputStream2 = new FileOutputStream(path2);
            String tmp1 = generateString(i*1000);
            String tmp2 = generateString(i*2000);
            fileOutputStream1.write(tmp1.getBytes(), 0, tmp1.getBytes().length);
            fileOutputStream2.write(tmp2.getBytes(), 0, tmp2.getBytes().length);

            fileOutputStream1.close();
            fileOutputStream2.close();

            ArrayList<String> files = new ArrayList<>();
            files.add("test1");
            files.add("test2");
            files.add("testing");

            packer.pack(files, false);

            File file1 = new File(path1);
            File file2 = new File(path2);
            assertTrue(file1.renameTo(new File(path4)));
            assertTrue(file2.renameTo(new File(path5)));

            unpacker.unpack("testing.afk");

            FileInputStream fileInputStream1 = new FileInputStream(path1);
            FileInputStream fileInputStream2 = new FileInputStream(path2);
            FileInputStream fileInputStream3 = new FileInputStream(path4);
            FileInputStream fileInputStream4 = new FileInputStream(path5);

            while ((fileInputStream1.available() != 0) || (fileInputStream3.available() != 0)){
                assertEquals(fileInputStream1.read(), fileInputStream3.read());
            }

            while ((fileInputStream2.available() != 0) || (fileInputStream4.available() != 0)){
                assertEquals(fileInputStream2.read(), fileInputStream4.read());
            }

            fileInputStream1.close();
            fileInputStream2.close();
            fileInputStream3.close();
            fileInputStream4.close();

            File file3 = new File(path4);
            File file4 = new File(path5);
            File file5 = new File(path3);
            assertTrue(file1.delete());
            assertTrue(file2.delete());
            assertTrue(file3.delete());
            assertTrue(file4.delete());
            assertTrue(file5.delete());
        }
    }

    private String generateString(int size){
        StringBuilder res = new StringBuilder();
        for (int i = 0; i < size; i++) {
            res.append((char) (r.nextInt(255)));
        }
        return res.toString();
    }
}