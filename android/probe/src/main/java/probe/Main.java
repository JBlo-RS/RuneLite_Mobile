package probe;

import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.stream.LongStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/** Smoke tests for the on-device JRE, covering what RuneLite leans on. Exit code = failures. */
public final class Main {
    private static int failures;

    interface Check {
        String run() throws Exception;
    }

    private static void check(String name, Check c) {
        long t0 = System.nanoTime();
        try {
            String detail = c.run();
            System.out.printf("PASS %-10s %6d ms  %s%n", name, (System.nanoTime() - t0) / 1_000_000, detail);
        } catch (Throwable t) {
            failures++;
            System.out.printf("FAIL %-10s %6d ms  %s%n", name, (System.nanoTime() - t0) / 1_000_000, t);
            t.printStackTrace(System.out);
        }
    }

    public static void main(String[] args) {
        System.out.println("java.version  = " + System.getProperty("java.version"));
        System.out.println("java.vm.name  = " + System.getProperty("java.vm.name") + " " + System.getProperty("java.vm.version"));
        System.out.println("java.home     = " + System.getProperty("java.home"));
        System.out.println("os            = " + System.getProperty("os.name") + " " + System.getProperty("os.arch") + " " + System.getProperty("os.version"));
        System.out.println("cpus/heap     = " + Runtime.getRuntime().availableProcessors() + " / " + Runtime.getRuntime().maxMemory() / (1 << 20) + " MB");

        check("jit", () -> {
            long best = Long.MAX_VALUE, n = 0;
            for (int round = 0; round < 5; round++) {
                long t = System.nanoTime();
                n = countPrimes(2_000_000);
                best = Math.min(best, System.nanoTime() - t);
            }
            if (n != 148933) throw new AssertionError("primes=" + n);
            return "primes<2M=" + n + ", best round " + best / 1_000_000 + " ms";
        });

        check("threads", () -> {
            long sum = LongStream.rangeClosed(1, 50_000_000L).parallel().map(x -> x ^ (x >>> 3)).sum();
            return "parallel sum=" + sum;
        });

        check("zip", () -> {
            byte[] data = new byte[1 << 20];
            for (int i = 0; i < data.length; i++) data[i] = (byte) (i * 31 % 251);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            try (GZIPOutputStream gz = new GZIPOutputStream(bos)) {
                gz.write(data);
            }
            byte[] back;
            try (InputStream in = new GZIPInputStream(new ByteArrayInputStream(bos.toByteArray()))) {
                back = in.readAllBytes();
            }
            if (!java.util.Arrays.equals(data, back)) throw new AssertionError("mismatch");
            return "1 MiB -> " + bos.size() + " bytes and back";
        });

        check("fonts", () -> {
            GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
            String[] families = ge.getAvailableFontFamilyNames();
            BufferedImage img = new BufferedImage(200, 40, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = img.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 16));
            g.drawString("RuneLite", 4, 28);
            g.dispose();
            int lit = 0;
            for (int y = 0; y < img.getHeight(); y++)
                for (int x = 0; x < img.getWidth(); x++)
                    if ((img.getRGB(x, y) >>> 24) != 0) lit++;
            if (lit == 0) throw new AssertionError("drawString drew nothing");
            return families.length + " families, drew " + lit + " px";
        });

        check("https", () -> {
            HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
            HttpResponse<String> r = http.send(
                    HttpRequest.newBuilder(URI.create("https://static.runelite.net/bootstrap.json"))
                            .timeout(Duration.ofSeconds(20)).build(),
                    HttpResponse.BodyHandlers.ofString());
            if (r.statusCode() != 200) throw new AssertionError("HTTP " + r.statusCode());
            return "bootstrap.json HTTP 200, " + r.body().length() + " chars, " + r.version();
        });

        System.out.println(failures == 0 ? "ALL CHECKS PASSED" : failures + " CHECK(S) FAILED");
        System.exit(failures);
    }

    private static long countPrimes(int limit) {
        boolean[] composite = new boolean[limit];
        long count = 0;
        for (int i = 2; i < limit; i++) {
            if (!composite[i]) {
                count++;
                for (long j = (long) i * i; j < limit; j += i) composite[(int) j] = true;
            }
        }
        return count;
    }
}
