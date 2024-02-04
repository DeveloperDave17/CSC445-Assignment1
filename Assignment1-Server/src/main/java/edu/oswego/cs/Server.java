package edu.oswego.cs;

import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ThreadLocalRandom;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;

public class Server {
  
   public static void main(String[] args) {
      int portNumber;
      if (args.length > 0) {
         portNumber = Integer.parseInt(args[0]);
      } else {
         // One of my assigned ports
         portNumber = 26910;
      }

      ServerSocket serverSocket = null;
      Socket client = null;
      PrintWriter out = null;
      BufferedReader in = null;
      try {
         serverSocket = new ServerSocket(portNumber);
         client = serverSocket.accept();
         boolean enableAutoFlush = true;
         out = new PrintWriter(client.getOutputStream(), enableAutoFlush);
         in = new BufferedReader(new InputStreamReader(client.getInputStream()));
      } catch (IOException e) {
         System.err.println("There was an I/O exception when connecting to the client");
         e.printStackTrace();
         System.exit(1);
      }

      long xorKey = generateXorKey(out, in);
      int sampleSize = 30;
      xorKey = handleRTTWithTCPMessages(out, in, xorKey, sampleSize);
      closeResources(serverSocket, client, out, in);
   }

   public static void closeResources(ServerSocket serverSocket, Socket client, PrintWriter out, BufferedReader in) {
      try {   
         out.close();
         in.close();
         client.close();
         serverSocket.close();
      } catch (IOException e) {
         System.err.println("There was an I/O Exception thrown when try to close one of the resources");
         e.printStackTrace();
         System.exit(1);
      }
   }

   public static long generateXorKey(PrintWriter out, BufferedReader in) {
      ThreadLocalRandom random = ThreadLocalRandom.current();
      try {
         long seed = Long.parseLong(in.readLine());
         out.println(seed);
         int numIterations = Integer.parseInt(in.readLine());
         out.print(numIterations);
         random.setSeed(seed);
         for (int i = 0; i < numIterations; i++) {
            random.nextLong();
         }
      } catch (IOException e) {
         System.err.println("There was an I/O Exception thrown when generating the xor key");
         e.printStackTrace();
         System.exit(1);
      }
      return random.nextLong();
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

   public static long handleRTTWithTCPMessages(PrintWriter out, BufferedReader in, long xorKey, int sampleSize) {
      xorKey = handleRTTWithTCPMessage("hey!", out, in, xorKey, sampleSize);
      xorKey = handleRTTWithTCPMessage("This is a secret message: 123412", out, in, xorKey, sampleSize);
      xorKey = handleRTTWithTCPMessage("1234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456", out, in, xorKey, sampleSize);
      return xorKey;
   }

   public static long handleRTTWithTCPMessage(String expectedMessage, PrintWriter out, BufferedReader in, long xorKey, int sampleSize) {
      for (int sampleNum = 1; sampleNum <= sampleSize; sampleNum++) {
         try {
            String message = in.readLine();
            message = xorWithKey(message, xorKey);
            validateMessage(message, expectedMessage);
            xorKey = xorShift(xorKey);
            message = xorWithKey(message, xorKey);
            out.println(message);
            // Prime the key for next usage
            xorKey = xorShift(xorKey);
         } catch (IOException e) {
            System.err.println("There was an I/O exception thrown during a RTT tcp message");
            e.printStackTrace();
            System.exit(1);
         }
      }
      return xorKey;
   }

   public static void validateMessage(String message, String expectedMessage) {
      if (message.equals(expectedMessage)) {
         System.out.println("Message is valid.");
      } else {
         System.out.println("Message is invalid.");
      }
   }
}
