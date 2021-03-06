package ru.mirea.data;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.PriorityQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class PackerImpl implements Packer{
    private Integer globalPriority = 0;
    private Charset charset = Charset.forName("Windows-1251");
    private int BUFFER_SIZE = 64000;

    @Override
    public int pack(File inputFile, File outputFile, boolean isCompression) throws IOException, InterruptedException {
        String path = outputFile.getAbsolutePath() + ".afk";
        FileOutputStream fileOutputStream = new FileOutputStream(path, true);
        path = inputFile.getAbsolutePath();

        if (!isCompression) {
            return packWithoutCompression(inputFile, fileOutputStream);
        } else {
            if (new File(path).length() == 0) {
                return packWithoutCompression(inputFile, fileOutputStream);
            }
            return packWithCompression(inputFile, fileOutputStream);
        }
    }

    private int packWithCompression(File inputFile, FileOutputStream fileOutputStream) throws IOException, InterruptedException {
        byte[][] groupBytes = new byte[15][BUFFER_SIZE];
        byte[] bytes = new byte[BUFFER_SIZE];
        int quantitySymbols;
        String path = inputFile.getAbsolutePath();
        Thread[] threads = new Thread[4];

        FileInputStream fileInputStream = new FileInputStream(path);
        BlockingQueue<BlockComponents> qIn = new LinkedBlockingQueue<>();
        PriorityQueue<BlockComponents> qOut = new PriorityQueue<>();
        globalPriority = 0;

        String fileName = inputFile.getName() + ":";

        BlockPacker packer = new BlockPacker(qIn, qOut);
        Printer printer = new Printer(1, qOut, fileOutputStream);

        try{
            for (int i = 0; i < threads.length; i++)
                threads[i] = new Thread(packer);
            Thread threadPrinter = new Thread(printer);

            for (int i = 0; i < threads.length; i++)
                threads[i].start();
            threadPrinter.start();

            int i = 0;
            while ((quantitySymbols = fileInputStream.read(bytes, 0, BUFFER_SIZE)) > 0) {
                groupBytes[i%15] = bytes.clone();
                while (qIn.size() > 5)
                    Thread.sleep(1);
                qIn.put(new BlockComponents(fileName, quantitySymbols, groupBytes[i%15]));
                fileName = "";
            }

            while (!qIn.isEmpty()){
                Thread.sleep(5);
            }
            packer.close();

            for (int j = 0; j < threads.length; j++){
                qIn.put(new BlockComponents("-1", -1, groupBytes[i%15]));
            }

            for (int j = 0; j < threads.length; j++)
                threads[j].join();

            while (!qOut.isEmpty()){
                Thread.sleep(1);
            }
            printer.close();

            threadPrinter.join();

            qIn.clear();
            qOut.clear();
        }
        finally {
            fileInputStream.close();
            fileOutputStream.close();
        }
        return 0;
    }

    private int packWithoutCompression(File inputFile, FileOutputStream fileOutputStream) throws IOException {
        byte[] bytes = new byte[BUFFER_SIZE];
        int quantitySymbols;
        String path = inputFile.getAbsolutePath();
        FileInputStream fileInputStream = new FileInputStream(path);
        try {
            ByteBuffer buf = charset.encode(getInfo(inputFile));
            byte[] meta = buf.array();
            fileOutputStream.write(meta, 0, meta.length);

            while ((quantitySymbols = fileInputStream.read(bytes, 0, BUFFER_SIZE)) > 0) {
                fileOutputStream.write(bytes, 0, quantitySymbols);
            }
        }
        finally {
            fileInputStream.close();
            fileOutputStream.close();
        }
        return 0;
    }

    private class BlockComponents implements Comparable<BlockComponents>{
        private String fileName;
        private StringBuilder meta;
        private int length;
        private int priority;
        private byte[] byteBlock;

        BlockComponents(String fileName, int length, byte[] byteBlock){
            this.fileName = fileName;
            this.length = length;
            this.byteBlock = byteBlock;
        }

        public int compareTo(BlockComponents block) {
            return priority - block.priority;
        }
    }


    private class BlockPacker implements Runnable {
        private boolean isThreadActive = true;
        BlockingQueue<BlockComponents> qIn;
        PriorityQueue<BlockComponents> qOut;

        BlockPacker(BlockingQueue<BlockComponents> qIn, PriorityQueue<BlockComponents> qOut) {
            this.qIn = qIn;
            this.qOut = qOut;
        }

        @Override
        public void run() {
            Compressor compressor = new CompressorImpl();
            while (isThreadActive) {
                try {
                    BlockComponents block;
                    synchronized (qIn){
                        synchronized (globalPriority){
                            block = qIn.take();
                            block.priority = globalPriority;
                            ++globalPriority;
                        }
                    }

                    if ("-1".equals(block.fileName))
                        continue;

                    StringBuilder strBlock = new StringBuilder();
                    for (int j = 0; j < block.length; j++)
                        strBlock.append((char)block.byteBlock[j]);

                    StringBuilder compressBlock = compressor.compression(strBlock);

                    if (compressBlock.toString().equals("-1")) {
                        block.meta = (block.fileName.length() != 0) ?
                                new StringBuilder ("0" + block.fileName + block.length + ":") :
                                new StringBuilder("3" + block.length + ":");
                        synchronized (qOut) {
                            qOut.add(block);
                        }
                        continue;
                    }

                    if (compressBlock.toString().equals("-2")) {
                        block.meta = (block.fileName.length() != 0) ?
                                new StringBuilder("4" + block.fileName + block.length  + ":") :
                                new StringBuilder("5" + block.length + ":");
                        block.length = 1;
                        synchronized (qOut) {
                            qOut.add(block);
                        }
                        continue;
                    }

                    block.byteBlock = new byte[compressBlock.length()];
                    for (int j = 0; j < compressBlock.length(); j++) {
                        block.byteBlock[j] = (byte) compressBlock.charAt(j);
                    }

                    block.length = block.byteBlock.length;
                    block.meta = (block.fileName.length() != 0) ?
                            new StringBuilder("1" + block.fileName) :
                            new StringBuilder("2");
                    synchronized (qOut) {
                        qOut.add(block);
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        void close(){
            isThreadActive = false;
        }
    }

    private class Printer implements Runnable{
        private int sleepTime;
        private boolean isThreadActive = true;
        PriorityQueue<BlockComponents> qOut;
        FileOutputStream fileOutputStream;
        int priority = 0;

        Printer(int sleepTime, PriorityQueue<BlockComponents> qOut, FileOutputStream fileOutputStream){
            this.sleepTime = sleepTime;
            this.qOut = qOut;
            this.fileOutputStream = fileOutputStream;
        }

        @Override
        public void run() {
            while (isThreadActive) {
                try {
                    synchronized (qOut) {
                        if (!qOut.isEmpty()) {
                            if (qOut.peek().priority == priority) {
                                BlockComponents block = qOut.poll();
                                ByteBuffer buf = charset.encode(block.meta.toString());
                                byte[] meta = buf.array();
                                fileOutputStream.write(meta, 0, block.meta.length());
                                fileOutputStream.write(block.byteBlock, 0, block.length);
                                ++priority;
                                block.byteBlock = null;
                                block.meta = null;
                            }
                        }
                    }
                    Thread.sleep(sleepTime);
                }
                catch (IOException e){
                    e.printStackTrace();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }

        void close(){
            isThreadActive = false;
        }
    }

    private String getInfo(File file){
        String metaInfo = "0";
        long bytes = file.length();
        metaInfo +=  file.getName() + ":" + bytes + ":";
        return metaInfo;
    }

}
