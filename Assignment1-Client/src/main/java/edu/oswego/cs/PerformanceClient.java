package edu.oswego.cs;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.util.Random;

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

      int sampleSize;
      if (args.length > 2) {
         sampleSize = Integer.parseInt(args[2]);
      } else {
         sampleSize = 30;
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
      XorKey xorKey = generateXorKey(out, in);
      String tcpMessageTypeName = "TCP";
      String rttTestName = "RTT";
      BufferedWriter tcpRTTCSVWriter = setupCSVWriter(rttTestName, tcpMessageTypeName);
      measureRTTWithTCPMessages(logFileWriter, out, in, xorKey, sampleSize, tcpRTTCSVWriter);
      closeCSVWRITER(tcpRTTCSVWriter);
      String throughputTestName = "Throughput";
      BufferedWriter tcpThroughputWriter = setupCSVWriter(throughputTestName, tcpMessageTypeName);
      measureThroughputForTCPTests(out, in, logFileWriter, xorKey, sampleSize, tcpThroughputWriter);
      closeCSVWRITER(tcpThroughputWriter);
      closeTCPIOs(socket, out, in);

      DatagramChannel datagramChannel = null;
      InetSocketAddress address = new InetSocketAddress(host, portNumber);
      try {
        datagramChannel = DatagramChannel.open();
        datagramChannel.bind(null);
      } catch (IOException e) {
         System.err.println("There was an I/O Exception thrown when opening the DatagramChannel.");
         e.printStackTrace();
         System.exit(1);
      }

      String udpMessageTypeName = "UDP";
      BufferedWriter udpRTTWriter = setupCSVWriter(rttTestName, udpMessageTypeName);
      measureRTTWithUDPTests(datagramChannel, address, logFileWriter, xorKey, sampleSize, udpRTTWriter);
      closeCSVWRITER(udpRTTWriter);
      BufferedWriter udpThroughputWriter = setupCSVWriter(throughputTestName, udpMessageTypeName);
      measureThroughputForUDPTests(datagramChannel, address, logFileWriter, xorKey, sampleSize, udpThroughputWriter);
      closeCSVWRITER(udpThroughputWriter);

      try {
         datagramChannel.close();
         logFileWriter.close();
      } catch (IOException e) {
         System.err.println("There was an I/O Exception thrown when resources after UDP tests");
         e.printStackTrace();
         System.exit(1);
      }
   }

   /**
    * Generates the intial xorKey by first generating a seed, then sending the seed to the other device.
    * The seed is then used to generate the key.
    * @param out The data output stream of the server that the client is connected to.
    * @param in The data input stream of the server that the client is connected to.
    * @return The generated xorKey.
    */
   public static XorKey generateXorKey(DataOutputStream out, DataInputStream in) {
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
      XorKey xorKey = new XorKey(random.nextLong());
      return xorKey;
   }

   /**
    * This method encapsulets all of the RTT TCP tests required for the project including message sizes of 8, 64, and 512 bytes.
    * @param logFileWriter The file writer that is used for logging application information.
    * @param out The output data stream of the server the client is connected to.
    * @param in The input data stream of the server the client is connected to.
    * @param xorKey The xor key used to encrypt and decrypt the data.
    * @param sampleSize The sample size to be used for each test.
    * @return
    */
   public static void measureRTTWithTCPMessages(FileWriter logFileWriter, DataOutputStream out, DataInputStream in, XorKey xorKey, int sampleSize, BufferedWriter csvWriter) {
      int message1Size = 8;
      log("Started RTT to send " + message1Size + " Bytes", logFileWriter);
      measureRTTWithTCP(message1Size, logFileWriter, out, in, xorKey, sampleSize, csvWriter);
      int message2Size = 64;
      log("Started RTT to send " + message2Size + " Bytes", logFileWriter);
      measureRTTWithTCP(message2Size, logFileWriter, out, in, xorKey, sampleSize, csvWriter);
      int message3Size = 512;
      log("Started RTT to send " + message3Size + " Bytes", logFileWriter);
      measureRTTWithTCP(message3Size, logFileWriter, out, in, xorKey, sampleSize, csvWriter);
   }

   /**
    * This method measures round trip latency while utilizing TCP. This method was written with the intention to handle various message sizes
    * by generating the message data within the method. The data is then encrypted and loaded into a byte array before a timer is started. Once a timer
    * is started the message is sent off. The message is then retrieved from the server and validated. Finally the timer is stopped and the time is logged.
    * @param messageSize Specifies the message size in bytes to be sent to the server.
    * @param logFileWriter The file writer which will be used to log test information.
    * @param out The output data stream of the server that the client is connected to.
    * @param in The input data stream of the server that the client is connected to.
    * @param xorKey The xor key to be used for encrypting and decrypting the message.
    * @param sampleSize Specifies the amount of samples to be collected before the method is exited.
    * @return The xor key for future use.
    */
   public static void measureRTTWithTCP(int messageSize, FileWriter logFileWriter, DataOutputStream out, DataInputStream in, XorKey xorKey, int sampleSize, BufferedWriter csvWriter) {
      long[] expectedMessage = generateData(messageSize);
      for (int sample = 1; sample <= sampleSize; sample++) {
         long[] message = generateData(messageSize);
         // encode message
         xorKey.xorWithKey(message);
         try {
            ByteBuffer byteBuffer = ByteBuffer.allocate(message.length * Long.BYTES);
            byteBuffer.asLongBuffer().put(message);
            long start = System.nanoTime();
            out.write(byteBuffer.array());
            out.flush();
            long[] response = new long[message.length];
            for (int i = 0; i < message.length; i++) {
               response[i] = in.readLong();
            }
            // decode received message
            xorKey.xorWithKey(response);
            boolean validated = validateResponse(expectedMessage, response);
            long timeElapsed = System.nanoTime() - start;
            csvWriter.write("" + sample + "," + timeElapsed + "," + validated + "\n");
         } catch (IOException e) {
            System.err.println("I/O error during measurement of RTT with TCP");
            e.printStackTrace();
            System.exit(1);
         }
      }
   } 

   /**
    * Checks to see if two messages contain the same information.
    * @param message The first message
    * @param response The second message
    * @return Either true if the messages contain the same info or false if they do not.
    */
   public static boolean validateResponse(long[] message, long[] response) {
      for (int i = 0; i < message.length; i++) {
         if (message[i] != response[i]) return false;
      }
      return true;
   }

   /**
    * Logs information to a file and prints to the console. This method also handles I/O errors with the file writer if they arise.
    * @param logMessage The message to be logged.
    * @param logFileWriter The file writer that will record the message.
    */
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

   /**
    * Creates a file writer with append enabled using the specified path.
    * @param logFilePath Path to a file that exists or want to be created.
    * @return The created file writer
    */
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
   
   public static void closeTCPIOs(Socket socket, DataOutputStream out, DataInputStream in) {
      try {
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

   /**
    * Calls the generateTriangularNumber function to fill an array of longs that has a byte footprint of the specified size.
    * @param size The amount of bytes the generated data will encompass.
    * @return The generated long array filled with triangular numbers and consisting of size bytes.
    */
   public static long[] generateData(int size) {
      int numLongs = size / Long.BYTES;
      if (size % Long.BYTES > 0) numLongs++;
      long[] message = new long[numLongs];
      for (int i = 0; i < numLongs; i++) {
         message[i] = generateTriangularNumber(i);
      }
      return message;
   }

   /**
    * Measures the throughput for a specified number of messages that consist of a specified size and logs the collected throughput for a specified sample size. 
    * @param numMessages The number of messages to be sent and ACKed.
    * @param messageSize The size of each message.
    * @param out The data output stream that is connected to the server.
    * @param in The data input stream that is connected to the server.
    * @param logFileWriter The file writer that logs the throughput and test information.
    * @param sampleSize The number of samples to be collected before the function exits.
    * @param xorKey The xor key to be used for encrypting and decrypting messages.
    * @return The xor key for future use.
    */
   public static void measureThroughputForTCP(int numMessages, int messageSize, DataOutputStream out, DataInputStream in, FileWriter logFileWriter, XorKey xorKey, int sampleSize, BufferedWriter csvWriter) {
      int numLongsInMessage = messageSize / Long.BYTES;
      ByteBuffer byteBuffer = ByteBuffer.allocate(messageSize);
      int dataSize = numMessages * messageSize;
      long statusOkay = 200;
      byte[] encodedMessage = new byte[messageSize];
      log("Started throughput measurements for " + numMessages + " messages of size " + messageSize + " Bytes", logFileWriter);
      for (int sample = 1; sample <= sampleSize; sample++) {
         boolean acked = false;
         try {
            long[] data = generateData(dataSize);
            long startTime = System.nanoTime();
            for (int messageNum = 1; messageNum <= numMessages; messageNum++) {
               int startIndex = (messageNum - 1) * numLongsInMessage;
               int endIndex = messageNum * numLongsInMessage;
               // encode the message
               xorKey.xorWithKeyAndBounds(data, startIndex, endIndex);
               byteBuffer.rewind();
               byteBuffer.asLongBuffer().put(data, startIndex, numLongsInMessage);
               byteBuffer.rewind();
               byteBuffer.get(encodedMessage);
               out.write(encodedMessage);
               out.flush();
               long status = in.readLong();
               acked = status == statusOkay;
               if (!acked) System.out.println("There was an issue with the ack.");
            }
            long nanoTime = System.nanoTime() - startTime;
            double throughputBitsPerSecond = calculateThroughput(nanoTime, dataSize);
            csvWriter.write("" + sample + "," + throughputBitsPerSecond + "," + acked + "\n");
         } catch (IOException e) {
            System.err.println("There was an I/O exception thrown when trying to send a message during throughput measurement.");
            e.printStackTrace();
            System.exit(1);
         }
      }
   }

   /**
    * A Method that encapsulates the throughput tests for tcp communication. 
    * @param out The data output stream connected to the server.
    * @param in The data input stream connected to the server.
    * @param logFileWriter The file writer used to log test information.
    * @param sampleSize The number of samples to be collected for each test.
    * @param xorKey the xor key used for encrypting and decrypting messages.
    * @return The xor key for future use.
    */
   public static void measureThroughputForTCPTests(DataOutputStream out, DataInputStream in, FileWriter logFileWriter, XorKey xorKey, int sampleSize, BufferedWriter csvWriter) {
      int numMessagesForTest1 = 16384;
      int messageSizeForTest1 = 64;
      measureThroughputForTCP(numMessagesForTest1, messageSizeForTest1, out, in, logFileWriter, xorKey, sampleSize, csvWriter);

      int numMessagesForTest2 = 4096;
      int messageSizeForTest2 = 256;
      measureThroughputForTCP(numMessagesForTest2, messageSizeForTest2, out, in, logFileWriter, xorKey, sampleSize, csvWriter);

      int numMessagesForTest3 = 1024;
      int messageSizeForTest3 = 1024;
      measureThroughputForTCP(numMessagesForTest3, messageSizeForTest3, out, in, logFileWriter, xorKey, sampleSize, csvWriter);
   }

   public static void measureRTTWithUDP(int messageSize, DatagramChannel datagramChannel, InetSocketAddress address, FileWriter logFileWriter, XorKey xorKey, int sampleSize, BufferedWriter csvWriter) {
      long[] expectedMessage = generateData(messageSize);
      ByteBuffer byteBuffer = ByteBuffer.allocate(messageSize);
      log("Started RTT with UDP of size " + messageSize + " Bytes", logFileWriter);
      for (int sample = 1; sample <= sampleSize; sample++) {
         try {
            long[] message = generateData(messageSize);
            // encode message
            xorKey.xorWithKey(message);
            byteBuffer.asLongBuffer().put(message);
            byteBuffer.rewind();
            long startTime = System.nanoTime();
            datagramChannel.send(byteBuffer, address);
            byteBuffer.rewind();
            datagramChannel.receive(byteBuffer);
            byteBuffer.rewind();
            long[] receivedMessage = new long[message.length];
            byteBuffer.asLongBuffer().get(receivedMessage);
            // decode
            xorKey.xorWithKey(receivedMessage);
            boolean validResponse = validateResponse(expectedMessage, receivedMessage);
            long totalTime = System.nanoTime() - startTime;
            csvWriter.write("" + sample + "," + totalTime + "," + validResponse + "\n");
            // reset bytebuffer
            byteBuffer.rewind();
         } catch(IOException e) {
            System.err.println("There was an I/O Exception thrown while measuring RTT with UDP.");
            e.printStackTrace();
            System.exit(1);
         }
      }
   }

   public static void measureRTTWithUDPTests(DatagramChannel datagramChannel, InetSocketAddress address, FileWriter logFileWriter, XorKey xorKey, int sampleSize, BufferedWriter csvWriter) {
      int messageSizeForTest1 = 8;
      measureRTTWithUDP(messageSizeForTest1, datagramChannel, address, logFileWriter, xorKey, sampleSize, csvWriter);

      int messageSizeForTest2 = 64;
      measureRTTWithUDP(messageSizeForTest2, datagramChannel, address, logFileWriter, xorKey, sampleSize, csvWriter);

      int messageSizeForTest3 = 512;
      measureRTTWithUDP(messageSizeForTest3, datagramChannel, address, logFileWriter, xorKey, sampleSize, csvWriter);
   }

   public static void measureThroughputForUDPTests(DatagramChannel datagramChannel, InetSocketAddress address, FileWriter logFileWriter, XorKey xorKey, int sampleSize, BufferedWriter csvWriter) {
      int numMessagesForTest1 = 16384;
      int messageSizeForTest1 = 64;
      measureThroughputForUDPMessage(numMessagesForTest1, messageSizeForTest1, datagramChannel, address, logFileWriter, xorKey, sampleSize, csvWriter);

      int numMessagesForTest2 = 4096;
      int messageSizeForTest2 = 256;
      measureThroughputForUDPMessage(numMessagesForTest2, messageSizeForTest2, datagramChannel, address, logFileWriter, xorKey, sampleSize, csvWriter);

      int numMessagesForTest3 = 1024;
      int messageSizeForTest3 = 1024;
      measureThroughputForUDPMessage(numMessagesForTest3, messageSizeForTest3, datagramChannel, address, logFileWriter, xorKey, sampleSize, csvWriter);
   }

   public static void measureThroughputForUDPMessage(int numMessages, int messageSize, DatagramChannel datagramChannel, InetSocketAddress address, FileWriter logFileWriter, XorKey xorKey, int sampleSize, BufferedWriter csvWriter) {
      int numLongsInMessage = messageSize / Long.BYTES;
      ByteBuffer byteBuffer = ByteBuffer.allocate(messageSize);
      int dataSize = numMessages * messageSize;
      long statusOkay = 200;
      log("Started throughput measurements for " + numMessages + " messages of size " + messageSize + " Bytes", logFileWriter);
      for (int sample = 1; sample <= sampleSize; sample++) {
         boolean acked = false;
         try {
            long[] data = generateData(dataSize);
            long startTime = System.nanoTime();
            for (int messageNum = 1; messageNum <= numMessages; messageNum++) {
               int startIndex = (messageNum - 1) * numLongsInMessage;
               int endIndex = messageNum * numLongsInMessage;
               // encode the message
               xorKey.xorWithKeyAndBounds(data, startIndex, endIndex);
               byteBuffer.asLongBuffer().put(data, startIndex, numLongsInMessage);
               byteBuffer.rewind();
               datagramChannel.send(byteBuffer, address);
               byteBuffer.rewind();
               byteBuffer.limit(Long.BYTES);
               datagramChannel.receive(byteBuffer);
               byteBuffer.rewind();
               long status = byteBuffer.getLong();
               acked = status == statusOkay;
               if (!acked) log("There was an issue with the ack.", logFileWriter);
               // clears the limit and resets the position of the bytebuffer for the next sample.
               byteBuffer.clear();
            }
            long nanoTime = System.nanoTime() - startTime;
            double throughputBitsPerSecond = calculateThroughput(nanoTime, dataSize);
            csvWriter.write("" + sample + "," + throughputBitsPerSecond + "," + acked + "\n");
         } catch (IOException e) {
            System.err.println("There was an I/O exception thrown when trying to send a message during UDP throughput measurement.");
            e.printStackTrace();
            System.exit(1);
         }
      }
   }

   public static double calculateThroughput(long nanoTime, int dataSize) {
      double nanoSecondsInSeconds = Math.pow(10, 9);
      double seconds = nanoTime / nanoSecondsInSeconds;
      double throughputBytesPerSecond = dataSize / seconds; 
      double throughputBitsPerSecond = throughputBytesPerSecond * Byte.SIZE;
      return throughputBitsPerSecond;
   }

   public static BufferedWriter setupCSVWriter(String test, String messageType) {
      BufferedWriter bufferedWriter = null;
      String csvFileName = messageType + test + ".csv";
      try {
         bufferedWriter = new BufferedWriter(new PrintWriter(csvFileName));
         bufferedWriter.write("Sample Number," + messageType + " " + test + ",valid\n");
      } catch (IOException e) {
         System.err.println("An I/O exception was thrown while setting up the csv " + csvFileName);
         e.printStackTrace();
         System.exit(1);
      }
      return bufferedWriter;
   }

   public static void closeCSVWRITER(BufferedWriter bufferedWriter) {
      try {
         bufferedWriter.close();
      } catch (IOException e) {
         System.err.println("There was an I/O Exception thrown while closing a csv file.");
         e.printStackTrace();
         System.exit(1);
      }
   }
}
