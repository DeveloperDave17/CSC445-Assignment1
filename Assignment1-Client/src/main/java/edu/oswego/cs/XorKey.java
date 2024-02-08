package edu.oswego.cs;

public class XorKey {
   
   private long key;

   int numBytesXoredWithCurrentKey;

   public XorKey(long key) {
      this.key = key;
      numBytesXoredWithCurrentKey = 0;
   }

   public void xorWithKeyAndBounds(long[] data, int lowerBound, int upperBound) {
      int maxBytesAllowedToXorWithKey = 64;
      for (int i = lowerBound; i < upperBound; i++) {
         data[i] ^= key;
         numBytesXoredWithCurrentKey += Long.BYTES;
         // Checks if the key needs to be advanced
         if (numBytesXoredWithCurrentKey >= maxBytesAllowedToXorWithKey) {
            xorShift();
            numBytesXoredWithCurrentKey = 0;
         }
      }
   }

   public void xorWithKey(long[] data) {
      xorWithKeyAndBounds(data, 0, data.length);      
   }

   // Updates the rng of the key for each step
   public void xorShift() {
      key ^= key << 13;
      key ^= key >>> 7;
      key ^= key << 17;
   }

}
