package ru.mirea.data;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.PriorityQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class DecryptorImpl extends Cryptor implements Decryptor{
    private final static int BUFFER_SIZE = 64000;
    private Integer globalPriority = 0;

    public int decryption(String password, File inputFile) throws IOException, InterruptedException {
        ArrayList<Integer> lengthSubblock = createSizeBlock(password);
        BlockingQueue<BlockProperties> qIn = new LinkedBlockingQueue<>();
        PriorityQueue<BlockProperties> qOut = new PriorityQueue<>();
        byte[] bytes = new byte[BUFFER_SIZE];
        byte[][] groupBytes = new byte[15][BUFFER_SIZE];
        int quantitySymbols;
        globalPriority = 0;

        String path1 = inputFile.getAbsolutePath();
        FileInputStream fileInputStream = new FileInputStream(path1);

        String path2 = inputFile.getAbsolutePath() + "dec";
        FileOutputStream fileOutputStream = new FileOutputStream(path2);

        BlockDecryptor blockDecryptor = new BlockDecryptor(qIn, qOut, lengthSubblock);
        Printer printer = new Printer(1, qOut, fileOutputStream);
        Thread thread1 = new Thread(blockDecryptor);
        Thread thread2 = new Thread(blockDecryptor);
        Thread thread3 = new Thread(blockDecryptor);
        Thread thread4 = new Thread(blockDecryptor);
        Thread threadPrinter = new Thread(printer);


        thread1.start();
        thread2.start();
        thread3.start();
        thread4.start();
        threadPrinter.start();

        int i = 0;
        while ((quantitySymbols = fileInputStream.read(bytes, 0, BUFFER_SIZE)) > 0){
            groupBytes[i%15] = bytes.clone();
            while (qIn.size() > 5)
                Thread.sleep(1);
            qIn.put(new BlockProperties(quantitySymbols, groupBytes[i%15]));
            ++i;
        }

        while (!qIn.isEmpty()){
            Thread.sleep(10);
        }
        blockDecryptor.close();

        for (int j = 0; j < 10; j++){
            qIn.put(new BlockProperties(-1, bytes));
        }

        thread1.join();
        thread2.join();
        thread3.join();
        thread4.join();

        while (!qOut.isEmpty()){
            Thread.sleep(1);
        }
        printer.close();

        threadPrinter.join();

        fileOutputStream.close();
        fileInputStream.close();

        return 0;
    }

    private class BlockDecryptor implements Runnable{
        private boolean isThreadActive = true;
        BlockingQueue<BlockProperties> qIn;
        PriorityQueue<BlockProperties> qOut;
        ArrayList<Integer> lengthSubblock;

        BlockDecryptor(BlockingQueue<BlockProperties> qIn, PriorityQueue<BlockProperties> qOut, ArrayList<Integer> lengthSubblock) {
            this.qIn = qIn;
            this.qOut = qOut;
            this.lengthSubblock = lengthSubblock;
        }

        @Override
        public void run() {
            while (isThreadActive) {
                try {
                    BlockProperties block;
                    synchronized (qIn) {
                        synchronized (globalPriority) {
                            block = qIn.take();
                            block.priority = globalPriority;
                            ++globalPriority;
                            if (globalPriority % 51 == 0)
                                System.gc();
                        }
                    }

                    if (block.length == -1)
                        continue;

                    StringBuilder binarySequence = byteToStr(block.length, block.bytes);
                    binarySequence = binarySequence.reverse();

                    StringBuilder reverseSequence = crypt(binarySequence, lengthSubblock);
                    StringBuilder result = binStrToStr(reverseSequence);

                    byte[] tmpBytes = new byte[result.length()];
                    for (int j = 0; j < result.length(); j++) {
                        tmpBytes[j] = (byte) result.charAt(j);
                    }


                    binarySequence.delete(0, binarySequence.length());
                    reverseSequence.delete(0, reverseSequence.length());
                    result.delete(0, result.length());

                    block.bytes = tmpBytes;
                    block.length = tmpBytes.length;
                    synchronized (qOut){
                        qOut.add(block);
                    }
                }
                catch (InterruptedException e){
                    e.printStackTrace();
                }
            }
        }

        void close(){
            isThreadActive = false;
        }
    }
}
