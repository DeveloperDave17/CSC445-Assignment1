package edu.oswego.cs;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

public class PerformanceClient {
   
   public static void main(String[] args) {
      String host;
      if (args.length > 0) {
         host = args[0];
      } else {
         // A school server
         host = "gee.cs.oswego.edu";
      }

      int portNumber;
      if (args.length > 1) {
         portNumber = Integer.parseInt(args[1]);
      } else {
         // One of my assigned ports
         portNumber = 26910;
      }

      Socket socket = null;
      PrintWriter out = null;
      BufferedReader in = null;

      try {
         socket = new Socket(host, portNumber);
         // Set print writer to autoflush
         out = new PrintWriter(socket.getOutputStream(), true);
         in = new BufferedReader(new InputStreamReader(
            socket.getInputStream()
            ));
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
      xorKey = measureRTTWithTCPMessages(logFileWriter, out, in, xorKey);
      closeResources(socket, out, in, logFileWriter);
   }

   public static long generateXorKey(PrintWriter out, BufferedReader in) {
      ThreadLocalRandom random = ThreadLocalRandom.current();
      try {
         long seed = random.nextLong();
         out.println(seed);
         long responseSeed = Long.parseLong(in.readLine());
         int numOfIterationsBeforeKey = 5;
         out.println(numOfIterationsBeforeKey);
         int iterationValidation = Integer.parseInt(in.readLine());
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

   public static long measureRTTWithTCPMessages(FileWriter logFileWriter, PrintWriter out, BufferedReader in, long xorKey) {
      String message1 = "hey!";
      log("RTT to send " + message1 + ":", logFileWriter);
      xorKey = measureRTTWithTCP(message1, logFileWriter, out, in, xorKey);
      String message2 = "This is a secret message: 123412";
      log("RTT to send " + message2 + ":", logFileWriter);
      xorKey = measureRTTWithTCP(message2, logFileWriter, out, in, xorKey);
      String message3 = "1234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456";
      log("RTT to send " + message3 + ":", logFileWriter);
      xorKey = measureRTTWithTCP(message3, logFileWriter, out, in, xorKey);
      return xorKey;
   }

   public static long measureRTTWithTCP(String message, FileWriter logFileWriter, PrintWriter out, BufferedReader in, long xorKey) {
      // encode message
      message = xorWithKey(message, xorKey);
      try {
         long start = System.nanoTime();
         out.println(message);
         // retrieve original message
         message = xorWithKey(message, xorKey);
         String response = in.readLine();
         // advance key
         xorKey = xorShift(xorKey);
         // decode received message
         response = xorWithKey(response, xorKey);
         validateResponse(message, response);
         long timeElapsed = System.nanoTime() - start;
         log("" + timeElapsed, logFileWriter);
         // advance key for next message
         xorKey = xorShift(xorKey);
      } catch (IOException e) {
         System.err.println("I/O error during measurement of RTT with TCP");
         e.printStackTrace();
         System.exit(1);
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

   public static String xorWithKey(String message, long xorKey) {
      byte[] messageBytes = message.getBytes();
      int messageLength = messageBytes.length;
      int numBytesInLong = 8;
      byte[] xorKeyBytes = new byte[numBytesInLong];
      int currentKeyIndex = 0;
      for (int i = 0; i < messageLength; i++) {
         messageBytes[i] ^= xorKeyBytes[currentKeyIndex];
         currentKeyIndex++;
         if (currentKeyIndex == numBytesInLong) {
            currentKeyIndex = 0;
         }
      }
      return new String(messageBytes);
   }

   public static boolean validateResponse(String message, String response) {
      return message.equals(response);
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

   public static void closeResources(Socket socket, PrintWriter out, BufferedReader in, FileWriter logFileWriter) {
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
}
