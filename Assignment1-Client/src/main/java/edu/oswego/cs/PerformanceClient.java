package edu.oswego.cs;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

public class PerformanceClient {
   
   public static void main(String[] args) {
      String host;
      if (args.length > 0) {
         host = args[0];
      } else {
         // A school server gee.cs.oswego.edu
         host = "127.0.0.1";
      }

      int portNumber;
      if (args.length > 1) {
         portNumber = Integer.parseInt(args[1]);
      } else {
         // One of my assigned ports
         portNumber = 26910;
      }

      Socket socket = null;
      DataOutputStream out = null;
      DataInputStream in = null;

      try {
         socket = new Socket(host, portNumber);
         // Set print writer to autoflush
         out = new DataOutputStream(socket.getOutputStream());
         in = new DataInputStream(socket.getInputStream());
      } catch (UnknownHostException e) {
         System.err.println("Could not find host: " + host);
         e.printStackTrace();
         System.exit(1);
      } catch (IOException e) {
         System.err.println("Could not achieve Input or Output access with the connection");
         e.printStackTrace();
         System.exit(1);
      }

      String logFilePath = "log.txt";
      FileWriter logFileWriter = createLogFileWriter(logFilePath);
      long xorKey = generateXorKey(out, in);
      int sampleSize = 30;
      xorKey = measureRTTWithTCPMessages(logFileWriter, out, in, xorKey, sampleSize);
      xorKey = measureThroughputForTCPTests(out, in, logFileWriter, sampleSize, xorKey);
      closeResources(socket, out, in, logFileWriter);
   }

   public static long generateXorKey(DataOutputStream out, DataInputStream in) {
      Random random = new Random();
      try {
         long seed = random.nextLong();
         out.writeLong(seed);
         out.flush();
         long responseSeed = in.readLong();
         int numOfIterationsBeforeKey = 5;
         out.writeInt(numOfIterationsBeforeKey);
         out.flush();
         int iterationValidation = in.readInt();
         boolean isSeedValid = seed == responseSeed;
         boolean isIterationValid = numOfIterationsBeforeKey == iterationValidation;
         boolean isKeyValid = isSeedValid & isIterationValid;
         System.out.println("Key is valid: " + isKeyValid);
         random.setSeed(seed);
         // Ensures having the seed isn't enough to find the key
         for (int i = 0; i < numOfIterationsBeforeKey; i++) {
            random.nextLong();
         }
      } catch (IOException e) {
         System.err.println("I/O error during key generation");
         e.printStackTrace();
         System.exit(1);
      }
      return random.nextLong();
   }

   public static long measureRTTWithTCPMessages(FileWriter logFileWriter, DataOutputStream out, DataInputStream in, long xorKey, int sampleSize) {
      int message1Size = 8;
      log("RTT to send " + message1Size + " Bytes:", logFileWriter);
      xorKey = measureRTTWithTCP(message1Size, logFileWriter, out, in, xorKey, sampleSize);
      int message2Size = 64;
      log("RTT to send " + message2Size + " Bytes:", logFileWriter);
      xorKey = measureRTTWithTCP(message2Size, logFileWriter, out, in, xorKey, sampleSize);
      int message3Size = 512;
      log("RTT to send " + message3Size + " Bytes:", logFileWriter);
      xorKey = measureRTTWithTCP(message3Size, logFileWriter, out, in, xorKey, sampleSize);
      return xorKey;
   }

   public static long measureRTTWithTCP(int messageSize, FileWriter logFileWriter, DataOutputStream out, DataInputStream in, long xorKey, int sampleSize) {
      for (int sample = 1; sample <= sampleSize; sample++) {
         long[] message = generateData(messageSize);
         // encode message
         xorWithKey(message, xorKey);
         try {
            ByteBuffer byteBuffer = ByteBuffer.allocate(message.length * Long.BYTES);
            byteBuffer.asLongBuffer().put(message);
            long start = System.nanoTime();
            out.write(byteBuffer.array());
            out.flush();
            // decode original message
            xorWithKey(message, xorKey);
            long[] response = new long[message.length];
            for (int i = 0; i < message.length; i++) {
               response[i] = in.readLong();
            }
            // advance key
            xorKey = xorShift(xorKey);
            // decode received message
            xorWithKey(response, xorKey);
            System.out.println(validateResponse(message, response));
            long timeElapsed = System.nanoTime() - start;
            log("" + timeElapsed, logFileWriter);
            // advance key for next message
            xorKey = xorShift(xorKey);
         } catch (IOException e) {
            System.err.println("I/O error during measurement of RTT with TCP");
            e.printStackTrace();
            System.exit(1);
         }
      }
      return xorKey;
   }

   // Updates the rng of the key for each step
   public static long xorShift(long key) {
      key ^= key << 13;
      key ^= key >>> 7;
      key ^= key << 17;
      return key;
   }

   public static void xorWithKey(long[] message, long xorKey) {
      int messageLength = message.length;
      xorWithKeyAndBounds(message, xorKey, 0, messageLength);
   }

   // Exclusive end
   public static void xorWithKeyAndBounds(long[] data, long xorKey, int start, int end) {
      for (int i = start; i < end; i++) {
         data[i] ^= xorKey;
      }
   }

   public static boolean validateResponse(long[] message, long[] response) {
      for (int i = 0; i < message.length; i++) {
         if (message[i] != response[i]) return false;
      }
      return true;
   }

   public static void log(String logMessage, FileWriter logFileWriter) {
      System.out.println(logMessage);
      try {
         logFileWriter.write(logMessage + "\n");
      } catch (IOException e) {
         System.err.println("There was an I/O error with the log file");
         e.printStackTrace();
         System.exit(1);
      }
   }

   public static FileWriter createLogFileWriter(String logFilePath) {
      FileWriter logFileWriter = null;
      try {
         File logFile = new File(logFilePath);
         logFile.createNewFile();
         boolean append = true;
         logFileWriter = new FileWriter(logFile, append);
      } catch (IOException e) {
         System.err.println("Unable to create or access file with filepath: " + logFilePath);
         e.printStackTrace();
         System.exit(1);
      }
      return logFileWriter;
   }

   public static void closeResources(Socket socket, DataOutputStream out, DataInputStream in, FileWriter logFileWriter) {
      try {
         logFileWriter.close();
         in.close();
         out.close();
         socket.close();
      } catch (IOException e) {
         System.err.println("There was an I/O exception when closing resources");
         e.printStackTrace();
         System.exit(1);
      }
   }

   public static long generateTriangularNumber(long num) {
      return (num * (num + 1)) >>> 2;
   }

   public static long[] generateData(int size) {
      int numLongs = size / Long.BYTES;
      if (size % Long.BYTES > 0) numLongs++;
      long[] message = new long[numLongs];
      for (int i = 0; i < numLongs; i++) {
         message[i] = generateTriangularNumber(i);
      }
      return message;
   }

   public static long measureThroughputForTCP(int numMessages, int messageSize, DataOutputStream out, DataInputStream in, FileWriter logFileWriter, int sampleSize, long xorKey) {
      int numLongsInMessage = messageSize / Long.BYTES;
      ByteBuffer byteBuffer = ByteBuffer.allocate(messageSize);
      int dataSize = numMessages * messageSize;
      long statusOkay = 200;
      log("Throughput measurements for " + numMessages + " messages of size " + messageSize + " Bytes", logFileWriter);
      for (int sample = 1; sample <= sampleSize; sample++) {
         try {
            long[] data = generateData(dataSize);
            long startTime = System.nanoTime();
            for (int messageNum = 1; messageNum <= numMessages; messageNum++) {
               int startIndex = (messageNum - 1) * numLongsInMessage;
               int endIndex = messageNum * numLongsInMessage;
               // encode the message
               xorWithKeyAndBounds(data, xorKey, startIndex, endIndex);
               byteBuffer.rewind();
               byteBuffer.asLongBuffer().put(data, startIndex, numLongsInMessage);
               byteBuffer.rewind();
               byte[] encodedMessage = new byte[messageSize];
               byteBuffer.get(encodedMessage);
               out.write(encodedMessage);
               out.flush();
               // advance key
               xorKey = xorShift(xorKey);
               long status = in.readLong();
               if (status != statusOkay) System.out.println("There was an issue with the ack.");
            }
            long nanoTime = System.nanoTime() - startTime;
            double nanoSecondsInSeconds = Math.pow(10, 6);
            double seconds = nanoTime / nanoSecondsInSeconds;
            double throughputBytesPerSecond = dataSize / seconds; 
            double throughputBitsPerSecond = throughputBytesPerSecond * Byte.SIZE;
            log(String.format("%.4f", throughputBitsPerSecond), logFileWriter);
         } catch (IOException e) {
            System.err.println("There was an I/O exception thrown when trying to send a message during throughput measurement.");
            e.printStackTrace();
            System.exit(1);
         }
      }
      return xorKey;
   }

   public static long measureThroughputForTCPTests(DataOutputStream out, DataInputStream in, FileWriter logFileWriter, int sampleSize, long xorKey) {
      int numMessagesForTest1 = 16384;
      int messageSizeForTest1 = 64;
      xorKey = measureThroughputForTCP(numMessagesForTest1, messageSizeForTest1, out, in, logFileWriter, sampleSize, xorKey);

      int numMessagesForTest2 = 4096;
      int messageSizeForTest2 = 256;
      xorKey = measureThroughputForTCP(numMessagesForTest2, messageSizeForTest2, out, in, logFileWriter, sampleSize, xorKey);

      int numMessagesForTest3 = 1024;
      int messageSizeForTest3 = 1024;
      xorKey = measureThroughputForTCP(numMessagesForTest3, messageSizeForTest3, out, in, logFileWriter, sampleSize, xorKey);
      return xorKey;
   }
}
