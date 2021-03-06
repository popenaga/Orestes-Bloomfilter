package performance;

import java.util.Collections;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import orestes.bloomfilter.BloomFilter;
import orestes.bloomfilter.HashProvider.HashMethod;
import orestes.bloomfilter.test.MemoryBFTest;
import orestes.bloomfilter.test.helper.Helper;
import backport.java.util.concurrent.atomic.LongAdder;

public class RedisBFPerformance {
    public static void main(String[] args) throws Exception {
        int items = 100_000_000;
        int count = 30_000;
        int m = 100_000;
        int k = 10;
        BloomFilter<String> b = Helper.createRedisFilter("ruby", m, k, HashMethod.Murmur2);
        compareToRuby(count,  items, b);
        dumbParallelAdds(count, items, b);
        BloomFilter<String> b3 = Helper.createFilter( m * 10, k, HashMethod.Murmur2);
        dumbAdds(count * 10,  items * 10, b);
    }

    private static void compareToRuby(int count, int items, BloomFilter<String> b) {
        b.clear();
        Random r = new Random();
        LongAdder fp = new LongAdder();
        Set<String> seen = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
        long start = System.currentTimeMillis();
        for (int i = 0; i < count; i++) {
            // lost parallel from: IntStream.range(0, count).parallel().forEach
            String element = String.valueOf(r.nextInt(items));
            if (b.contains(element) && !seen.contains(element)) fp.increment();
            b.add(element);
            seen.add(element);
        }
        double fprate = 100.0 * fp.intValue() / count;
        System.out.println("False Positives = " + fp + ", FP-Rate = " + fprate);
        long end = System.currentTimeMillis();
        MemoryBFTest.printStat(start, end, count);
    }

    private static void dumbParallelAdds(int count, final int items, final BloomFilter<String> b) throws Exception {
        b.clear();
        final Random r = new Random();
        ExecutorService exec = Executors.newFixedThreadPool(20);
        long start = System.currentTimeMillis();
        for (int i = 0; i < count; i++) {
            exec.submit(new Callable<Boolean>() {
                @Override
                public Boolean call() throws Exception {
                    return b.add(String.valueOf(r.nextInt(items)));
                }
            });
        }
        exec.shutdown();
        exec.awaitTermination(5, TimeUnit.SECONDS);
        long end = System.currentTimeMillis();
        MemoryBFTest.printStat(start, end, count);
    }

    private static void dumbAdds(int count, int items, BloomFilter<String> b) throws Exception{
        b.clear();
        Random r = new Random();
        long start = System.currentTimeMillis();
        for (int i = 0; i < count; i++) {
            b.add(String.valueOf(r.nextInt(items)));
        }
        long end = System.currentTimeMillis();
        MemoryBFTest.printStat(start, end, count);
    }
}
