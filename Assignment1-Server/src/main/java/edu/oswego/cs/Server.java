package edu.oswego.cs;

import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.DataInputStream;
import java.io.DataOutputStream;

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
      DataOutputStream out = null;
      DataInputStream in = null;
      try {
         serverSocket = new ServerSocket(portNumber);
         client = serverSocket.accept();
         out = new DataOutputStream(client.getOutputStream());
         in = new DataInputStream(client.getInputStream());
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

   public static void closeResources(ServerSocket serverSocket, Socket client, DataOutputStream out, DataInputStream in) {
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

   public static long generateXorKey(DataOutputStream out, DataInputStream in) {
      Random random = new Random();
      try {
         long seed = in.readLong();
         out.writeLong(seed);
         out.flush();
         int numIterations = in.readInt();
         out.writeInt(numIterations);
         out.flush();
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

   public static void xorWithKey(long[] message, long xorKey) {
      int messageLength = message.length;
      for (int i = 0; i < messageLength; i++) {
         message[i] ^= xorKey;
      }
   }

   public static long handleRTTWithTCPMessages(DataOutputStream out, DataInputStream in, long xorKey, int sampleSize) {
      int message1Size = 8;
      xorKey = handleRTTWithTCPMessage(message1Size, out, in, xorKey, sampleSize);
      int message2Size = 64;
      xorKey = handleRTTWithTCPMessage(message2Size, out, in, xorKey, sampleSize);
      int message3Size = 512;
      xorKey = handleRTTWithTCPMessage(message3Size, out, in, xorKey, sampleSize);
      return xorKey;
   }

   public static long handleRTTWithTCPMessage(int messageSize, DataOutputStream out, DataInputStream in, long xorKey, int sampleSize) {
      long[] expectedMessage = generateMessage(messageSize);
      for (int sampleNum = 1; sampleNum <= sampleSize; sampleNum++) {
         try {
            long[] message = new long[expectedMessage.length];
            for (int i = 0; i < expectedMessage.length; i++) {
               message[i] = in.readLong();
            }
            xorWithKey(message, xorKey);
            System.out.println(validateMessage(message, expectedMessage));
            xorKey = xorShift(xorKey);
            xorWithKey(message, xorKey);
            ByteBuffer byteBuffer = ByteBuffer.allocate(expectedMessage.length * Long.BYTES);
            byteBuffer.asLongBuffer().put(message);
            out.write(byteBuffer.array());
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

   public static boolean validateMessage(long[] message, long[] expectedMessage) {
      for (int i = 0; i < message.length; i++) {
         if (message[i] != expectedMessage[i]) return false;
      }
      return true;
   }

   public static long generateTriangularNumber(long num) {
      return (num * (num + 1)) >>> 2;
   }

   public static long[] generateMessage(int size) {
      int numLongs = size / Long.BYTES;
      if (size % Long.BYTES > 0) numLongs++;
      long[] message = new long[numLongs];
      for (int i = 0; i < numLongs; i++) {
         message[i] = generateTriangularNumber(i);
      }
      return message;
   }
}
