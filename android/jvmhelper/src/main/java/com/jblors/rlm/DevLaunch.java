package com.jblors.rlm;

import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.UIManager;

/**
 * Starts the RuneLite client in developer mode, without the launcher.
 *
 * <p>RuneLite only enables --developer-mode when no launcher version is set, and the launcher
 * always sets one, so this does the launcher's final step itself: read bootstrap.json, select
 * this platform's artifacts with the same rules as the launcher, check the jars the launcher
 * already downloaded into ~/.runelite/repository2 against bootstrap's SHA-256 hashes, and
 * start net.runelite.client.RuneLite in a URLClassLoader like the launcher's REFLECT mode.
 *
 * <p>The normal launch must have run once so the jars exist. Unlike the launcher, this does
 * not check bootstrap.json's signature (it is fetched over HTTPS from static.runelite.net).
 *
 * <p>The launcher ships a trimmed runelite-api-VERSION-runtime.jar without the gameval
 * constant classes, which the developer tools need. In developer mode the full
 * runelite-api-VERSION.jar is fetched from repo.runelite.net (checked against the repository's
 * SHA-1), cached next to the other jars and used instead.
 */
public final class DevLaunch {
    private static final String BOOTSTRAP = "https://static.runelite.net/bootstrap.json";

    private DevLaunch() {}

    public static void main(String[] args) throws Exception {
        File repo = new File(System.getProperty("user.home"), ".runelite/repository2");
        System.out.println("DevLaunch: fetching " + BOOTSTRAP);
        // HttpURLConnection, not java.net.http: this class is on the boot class path, and the
        // boot loader cannot see java.net.http (a platform-loader module)
        java.net.HttpURLConnection conn = (java.net.HttpURLConnection) new URL(BOOTSTRAP).openConnection();
        conn.setConnectTimeout(20000);
        conn.setReadTimeout(30000);
        String body;
        try (InputStream in = conn.getInputStream()) {
            body = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> bootstrap = (Map<String, Object>) new Json(body).value();
        System.out.println("DevLaunch: client version " + bootstrap.get("version"));

        String os = System.getProperty("os.name"), arch = System.getProperty("os.arch");
        List<URL> classpath = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        for (Object o : (List<?>) bootstrap.get("artifacts")) {
            Map<?, ?> a = (Map<?, ?>) o;
            if (!forThisPlatform(a.get("platform"), os, arch)) continue;
            File jar = new File(repo, (String) a.get("name"));
            if (!jar.isFile()) {
                problems.add("missing " + jar.getName());
                continue;
            }
            String want = (String) a.get("hash");
            String have = sha256(jar);
            if (want != null && !want.equalsIgnoreCase(have)) {
                problems.add("hash mismatch " + jar.getName());
                continue;
            }
            classpath.add(jar.toURI().toURL());
        }
        for (int k = 0; k < classpath.size(); k++) {
            String name = new File(classpath.get(k).toURI()).getName();
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("runelite-api-(.+)-runtime\\.jar").matcher(name);
            if (m.matches()) {
                File full = fullApiJar(repo, m.group(1));
                if (full != null) classpath.set(k, full.toURI().toURL());
            }
        }
        if (!problems.isEmpty()) {
            System.out.println("DevLaunch: cannot start, the client files are missing or out of date: " + problems);
            System.out.println("DevLaunch: use the normal 'Launch RuneLite' once to download/update them, then retry.");
            System.exit(2);
        }
        System.out.println("DevLaunch: " + classpath.size() + " jars verified");

        // As the launcher does with hardware acceleration OFF on Linux
        System.setProperty("sun.java2d.opengl", "false");

        URLClassLoader loader = new URLClassLoader(classpath.toArray(new URL[0]), ClassLoader.getPlatformClassLoader());
        UIManager.put("ClassLoader", loader);
        List<String> clientArgs = new ArrayList<>();
        clientArgs.add("--developer-mode");
        clientArgs.addAll(Arrays.asList(args));
        Thread t = new Thread(() -> {
            try {
                loader.loadClass("net.runelite.client.RuneLite").getMethod("main", String[].class)
                        .invoke(null, (Object) clientArgs.toArray(new String[0]));
            } catch (Exception e) {
                System.out.println("DevLaunch: unable to start client");
                e.printStackTrace(System.out);
            }
        }, "RuneLite");
        t.start();
    }

    /** Full runelite-api jar for the version, downloaded once; null if unavailable. */
    private static File fullApiJar(File repo, String version) {
        String base = "https://repo.runelite.net/net/runelite/runelite-api/" + version + "/runelite-api-" + version + ".jar";
        File jar = new File(repo, "runelite-api-" + version + ".jar");
        try {
            String want = fetch(base + ".sha1").trim().split("\\s+")[0];
            if (jar.isFile() && want.equalsIgnoreCase(digest(jar, "SHA-1"))) {
                System.out.println("DevLaunch: using cached full API " + jar.getName());
                return jar;
            }
            System.out.println("DevLaunch: downloading full API " + jar.getName());
            File tmp = new File(repo, jar.getName() + ".part");
            try (InputStream in = new URL(base).openStream()) {
                Files.copy(in, tmp.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            if (!want.equalsIgnoreCase(digest(tmp, "SHA-1"))) {
                tmp.delete();
                System.out.println("DevLaunch: full API checksum mismatch, keeping the runtime API");
                return null;
            }
            Files.move(tmp.toPath(), jar.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            return jar;
        } catch (Exception e) {
            System.out.println("DevLaunch: full API unavailable (" + e + "), keeping the runtime API;"
                    + " the Developer Tools plugin will not start");
            return null;
        }
    }

    private static String fetch(String url) throws Exception {
        try (InputStream in = new URL(url).openStream()) {
            return new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    /** Same rule as the launcher: no platform list = everywhere; else match OS name and arch. */
    private static boolean forThisPlatform(Object platforms, String os, String arch) {
        if (platforms == null) return true;
        String osType = os.toLowerCase().startsWith("linux") ? "linux"
                : os.toLowerCase().startsWith("mac") ? "macos"
                : os.toLowerCase().startsWith("windows") ? "win" : os;
        for (Object o : (List<?>) platforms) {
            Map<?, ?> p = (Map<?, ?>) o;
            Object name = p.get("name");
            if (name == null) continue;
            if (osType.equals(name) && (p.get("arch") == null || arch.equals(p.get("arch")))) return true;
        }
        return false;
    }

    private static String sha256(File f) throws Exception {
        return digest(f, "SHA-256");
    }

    private static String digest(File f, String algorithm) throws Exception {
        MessageDigest md = MessageDigest.getInstance(algorithm);
        try (InputStream in = Files.newInputStream(f.toPath())) {
            byte[] buf = new byte[1 << 16];
            int n;
            while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
        }
        StringBuilder sb = new StringBuilder();
        for (byte b : md.digest()) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    /** Minimal JSON reader (objects, arrays, strings, numbers, booleans, null). */
    private static final class Json {
        private final String s;
        private int i;

        Json(String s) {
            this.s = s;
        }

        Object value() {
            ws();
            char c = s.charAt(i);
            if (c == '{') return object();
            if (c == '[') return array();
            if (c == '"') return string();
            if (s.startsWith("true", i)) { i += 4; return Boolean.TRUE; }
            if (s.startsWith("false", i)) { i += 5; return Boolean.FALSE; }
            if (s.startsWith("null", i)) { i += 4; return null; }
            int start = i;
            while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) i++;
            return Double.parseDouble(s.substring(start, i));
        }

        private Map<String, Object> object() {
            Map<String, Object> m = new LinkedHashMap<>();
            i++;
            ws();
            if (s.charAt(i) == '}') { i++; return m; }
            while (true) {
                ws();
                String k = string();
                ws();
                i++; // ':'
                m.put(k, value());
                ws();
                if (s.charAt(i++) == '}') return m;
            }
        }

        private List<Object> array() {
            List<Object> l = new ArrayList<>();
            i++;
            ws();
            if (s.charAt(i) == ']') { i++; return l; }
            while (true) {
                l.add(value());
                ws();
                if (s.charAt(i++) == ']') return l;
            }
        }

        private String string() {
            StringBuilder sb = new StringBuilder();
            i++; // opening quote
            while (true) {
                char c = s.charAt(i++);
                if (c == '"') return sb.toString();
                if (c != '\\') { sb.append(c); continue; }
                char e = s.charAt(i++);
                switch (e) {
                    case 'n': sb.append('\n'); break;
                    case 't': sb.append('\t'); break;
                    case 'r': sb.append('\r'); break;
                    case 'b': sb.append('\b'); break;
                    case 'f': sb.append('\f'); break;
                    case 'u': sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16)); i += 4; break;
                    default: sb.append(e);
                }
            }
        }

        private void ws() {
            while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
        }
    }
}
