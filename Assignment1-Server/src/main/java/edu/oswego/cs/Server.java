package edu.oswego.cs;

import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
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

      int sampleSize;
      if (args.length > 1) {
         sampleSize = Integer.parseInt(args[1]);
      } else {
         sampleSize = 30;
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

      XorKey xorKey = generateXorKey(out, in);
      handleRTTWithTCPMessages(out, in, xorKey, sampleSize);
      handleThroughputForTCPMessageTests(out, in, xorKey, sampleSize);
      closeTCPResources(serverSocket, client, out, in);

      DatagramChannel datagramChannel = null;
      try {
         datagramChannel = DatagramChannel.open();
         InetSocketAddress addr = new InetSocketAddress(portNumber);
         datagramChannel.bind(addr);
      } catch (IOException e) {
         System.err.println("There was an I/O Exception thrown when trying to open a datagram channel.");
         e.printStackTrace();
         System.exit(1);
      }

      handleRTTWithUDPMessages(datagramChannel, xorKey, sampleSize);
      handleThroughputForUDPTests(datagramChannel, xorKey, sampleSize);

      try {
         datagramChannel.close();
      } catch (IOException e) {
         System.err.println("There was an I/O Exception thrown when trying to close the datagram channel");
         e.printStackTrace();
         System.exit(1);
      }
   }

   public static void closeTCPResources(ServerSocket serverSocket, Socket client, DataOutputStream out, DataInputStream in) {
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

   public static XorKey generateXorKey(DataOutputStream out, DataInputStream in) {
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
      return new XorKey(random.nextLong());
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

   public static void handleRTTWithTCPMessages(DataOutputStream out, DataInputStream in, XorKey xorKey, int sampleSize) {
      int message1Size = 8;
      System.out.println("Handling RTT TCP message of size " + message1Size + "Bytes");
      handleRTTWithTCPMessage(message1Size, out, in, xorKey, sampleSize);
      int message2Size = 64;
      System.out.println("Handling RTT TCP message of size " + message2Size + "Bytes");
      handleRTTWithTCPMessage(message2Size, out, in, xorKey, sampleSize);
      int message3Size = 512;
      System.out.println("Handling RTT TCP message of size " + message3Size + "Bytes");
      handleRTTWithTCPMessage(message3Size, out, in, xorKey, sampleSize);
   }

   public static void handleRTTWithTCPMessage(int messageSize, DataOutputStream out, DataInputStream in, XorKey xorKey, int sampleSize) {
      long[] expectedMessage = generateMessage(messageSize);
      for (int sampleNum = 1; sampleNum <= sampleSize; sampleNum++) {
         try {
            long[] message = new long[expectedMessage.length];
            for (int i = 0; i < expectedMessage.length; i++) {
               message[i] = in.readLong();
            }
            // decode message
            xorKey.xorWithKey(message);
            boolean validMessage = validateMessage(message, expectedMessage);
            if (!validMessage) System.out.println(validMessage);
            // encode message
            xorKey.xorWithKey(message);
            ByteBuffer byteBuffer = ByteBuffer.allocate(expectedMessage.length * Long.BYTES);
            byteBuffer.asLongBuffer().put(message);
            out.write(byteBuffer.array());
         } catch (IOException e) {
            System.err.println("There was an I/O exception thrown during a RTT tcp message");
            e.printStackTrace();
            System.exit(1);
         }
      }
   }

   public static boolean validateMessage(long[] message, long[] expectedMessage) {
      for (int i = 0; i < message.length; i++) {
         if (message[i] != expectedMessage[i]) return false;
      }
      return true;
   }

   public static boolean validateMessageWithGeneratedTriangularNumbers(long[] message, int startIndex) {
      for (int i = 0; i < message.length; i++) {
         if (message[i] != generateTriangularNumber(startIndex + i)) return false;
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

   public static void handleThroughputForTCPMessages(int numMessages, int messageSize, DataOutputStream out, DataInputStream in, XorKey xorKey, int sampleSize) {
      long okayStatusCode = 200;
      int numLongs = messageSize / Long.BYTES;
      for (int sampleNum = 1; sampleNum <= sampleSize; sampleNum++) {
         try {
            for (int messageNum = 1; messageNum <= numMessages; messageNum++) {
               long[] message = new long[numLongs];
               for (int i = 0; i < numLongs; i++) {
                  message[i] = in.readLong();
               }
               // decode message
               xorKey.xorWithKey(message);
               int startIndex = (messageNum - 1) * numLongs;
               boolean validMessage = validateMessageWithGeneratedTriangularNumbers(message, startIndex);
               if (!validMessage) System.out.println(validMessage);
               // send acknowledgment
               out.writeLong(okayStatusCode);
               out.flush();
            }
         } catch (IOException e) {
            System.err.println("There was an I/O exception thrown when handling throughput tcps messages.");
            e.printStackTrace();
            System.exit(1);
         }
      }
   }

   public static void handleThroughputForTCPMessageTests(DataOutputStream out, DataInputStream in, XorKey xorKey, int sampleSize) {
      int numMessagesForTest1 = 16384;
      int messageSizeForTest1 = 64;
      System.out.println("Handling Throughput for TCP with " + numMessagesForTest1 + " messages of " + messageSizeForTest1 + " bytes.");
      handleThroughputForTCPMessages(numMessagesForTest1, messageSizeForTest1, out, in, xorKey, sampleSize);

      int numMessagesForTest2 = 4096;
      int messageSizeForTest2 = 256;
      System.out.println("Handling Throughput for TCP with " + numMessagesForTest2 + " messages of " + messageSizeForTest2 + " bytes.");
      handleThroughputForTCPMessages(numMessagesForTest2, messageSizeForTest2, out, in, xorKey, sampleSize);

      int numMessagesForTest3 = 1024;
      int messageSizeForTest3 = 1024;
      System.out.println("Handling Throughput for TCP with " + numMessagesForTest3 + " messages of " + messageSizeForTest3 + " bytes.");
      handleThroughputForTCPMessages(numMessagesForTest3, messageSizeForTest3, out, in, xorKey, sampleSize);
   }

   public static void handleRTTWithUDPMessage(int messageSize, DatagramChannel datagramChannel, XorKey xorKey, int sampleSize) {
      long[] expectedMessage = generateMessage(messageSize);
      ByteBuffer byteBuffer = ByteBuffer.allocate(messageSize);
      for (int sample = 1; sample <= sampleSize; sample++) {
         try {
            long[] receivedMessage = new long[expectedMessage.length];
            SocketAddress clientAddr = datagramChannel.receive(byteBuffer);
            byteBuffer.rewind();
            byteBuffer.asLongBuffer().get(receivedMessage);
            byteBuffer.rewind();
            // decode
            xorKey.xorWithKey(receivedMessage);
            boolean validMessage = validateMessage(receivedMessage, expectedMessage);
            if (!validMessage) System.out.println("validation error in RTT UDP.");
            // encode message
            xorKey.xorWithKey(receivedMessage);
            byteBuffer.asLongBuffer().put(receivedMessage);
            byteBuffer.rewind();
            datagramChannel.send(byteBuffer, clientAddr);
            // Setup bytebuffer for next message
            byteBuffer.rewind();
         } catch (IOException e) {
            System.err.println("There was an I/O Exception thrown when handling RTT with UDP messages.");
            e.printStackTrace();
            System.exit(1);
         }
      }
   }

   public static void handleRTTWithUDPMessages(DatagramChannel datagramChannel, XorKey xorKey, int sampleSize) {
      int messageSizeForTest1 = 8;
      System.out.println("Handling RTT UDP message of size " + messageSizeForTest1 + "Bytes");
      handleRTTWithUDPMessage(messageSizeForTest1, datagramChannel, xorKey, sampleSize);

      int messageSizeForTest2 = 64;
      System.out.println("Handling RTT UDP message of size " + messageSizeForTest2 + "Bytes");
      handleRTTWithUDPMessage(messageSizeForTest2, datagramChannel, xorKey, sampleSize);

      int messageSizeForTest3 = 512;
      System.out.println("Handling RTT UDP message of size " + messageSizeForTest3 + "Bytes");
      handleRTTWithUDPMessage(messageSizeForTest3, datagramChannel, xorKey, sampleSize);
   }

   public static void handleThroughputUDPMessages(int numMessages, int messageSize, DatagramChannel datagramChannel, XorKey xorKey, int sampleSize) {
      long okayStatusCode = 200;
      int numLongs = messageSize / Long.BYTES;
      ByteBuffer byteBuffer = ByteBuffer.allocate(messageSize);
      for (int sampleNum = 1; sampleNum <= sampleSize; sampleNum++) {
         try {
            for (int messageNum = 1; messageNum <= numMessages; messageNum++) {
               long[] message = new long[numLongs];
               SocketAddress clientAddr = datagramChannel.receive(byteBuffer);
               byteBuffer.rewind();
               byteBuffer.asLongBuffer().get(message);
               // decode message
               xorKey.xorWithKey(message);
               int startIndex = (messageNum - 1) * numLongs;
               boolean validMessage = validateMessageWithGeneratedTriangularNumbers(message, startIndex);
               if (!validMessage) System.out.println("Non-valid message for UDP throughput measurement.");
               // send acknowledgment
               byteBuffer.rewind();
               byteBuffer.putLong(okayStatusCode);
               byteBuffer.rewind();
               byteBuffer.limit(Long.BYTES);
               datagramChannel.send(byteBuffer, clientAddr);
               // clear limit from Bytebuffer and reset position
               byteBuffer.clear();
            }
         } catch (IOException e) {
            System.err.println("There was an I/O exception thrown when handling throughput udp messages.");
            e.printStackTrace();
            System.exit(1);
         }
      }
   }

   public static void handleThroughputForUDPTests(DatagramChannel datagramChannel, XorKey xorKey, int sampleSize) {
      int numMessagesForTest1 = 16384;
      int messageSizeForTest1 = 64;
      System.out.println("Handling Throughput for UDP with " + numMessagesForTest1 + " messages of " + messageSizeForTest1 + " bytes.");
      handleThroughputUDPMessages(numMessagesForTest1, messageSizeForTest1, datagramChannel, xorKey, sampleSize);

      int numMessagesForTest2 = 4096;
      int messageSizeForTest2 = 256;
      System.out.println("Handling Throughput for UDP with " + numMessagesForTest2 + " messages of " + messageSizeForTest2 + " bytes.");
      handleThroughputUDPMessages(numMessagesForTest2, messageSizeForTest2, datagramChannel, xorKey, sampleSize);

      int numMessagesForTest3 = 1024;
      int messageSizeForTest3 = 1024;
      System.out.println("Handling Throughput for UDP with " + numMessagesForTest3 + " messages of " + messageSizeForTest3 + " bytes.");
      handleThroughputUDPMessages(numMessagesForTest3, messageSizeForTest3, datagramChannel, xorKey, sampleSize);
   }
}
