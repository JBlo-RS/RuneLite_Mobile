package net.runelite.rlawt;

import java.awt.Component;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.ByteBuffer;

/**
 * Android replacement for RuneLite's rlawt (OpenGL for an AWT component), with the same public
 * API that RuneLite's GPU plugin calls. It is on the JVM's boot class path, so it is found before
 * the copy in RuneLite's rlawt jar, whose natives need JAWT and X11.
 *
 * <p>The OpenGL context is Mesa's Zink driver (OpenGL 4.6 on the phone's Vulkan driver) through
 * OSMesa, rendering into a native buffer the size of the component. {@link #swapBuffers()} copies
 * the finished frame to a front buffer, which com.jblors.rlm.ScreenCompositor draws into the
 * game area of the screen sent to Android ({@link #blitFrame}). Native side:
 * android/app/src/main/cpp/rlm_gl.c (librlmgl.so).
 *
 * <p>Only active when the app's GPU setting is on, which sets -Drlm.gl.lib (this class's native
 * library) and -Drlm.gl.osmesa (libOSMesa.so). Otherwise loadNatives() fails and RuneLite's GPU
 * plugin turns itself off, as it does without GPU support.
 */
public final class AWTContext {
    private static final int GL_BACK = 0x0405;

    private static boolean nativesLoaded;
    private static volatile AWTContext current;

    private final Component component;
    private long instance;
    private int alphaBits, depthBits = 24, stencilBits = 8;

    public static synchronized void loadNatives() {
        if (nativesLoaded) return;
        String lib = System.getProperty("rlm.gl.lib");
        if (lib == null) {
            throw new RuntimeException("GPU is turned off in RuneLite Mobile's settings");
        }
        System.load(lib);
        nativesLoaded = true;
    }

    public AWTContext(Component component) {
        if (!nativesLoaded) throw new IllegalStateException("natives not loaded");
        this.component = component;
        this.instance = create0();
        if (instance == 0) throw new RuntimeException("rlm-gl: cannot allocate context");
    }

    public void destroy() {
        if (current == this) current = null;
        if (instance != 0) destroy0(instance);
        instance = 0;
    }

    public void configurePixelFormat(int alpha, int depth, int stencil) {
        // 0 means "default"; the GPU plugin renders the scene into its own framebuffers
        alphaBits = alpha;
        if (depth > 0) depthBits = depth;
        if (stencil > 0) stencilBits = stencil;
    }

    public void configureMultisamples(int samples) {
        // The default framebuffer is a plain memory buffer; the GPU plugin does its own MSAA
    }

    public int getFramebuffer(boolean multisampled) {
        return 0; // the default framebuffer, i.e. the OSMesa buffer
    }

    public int getBufferMode() {
        // The buffer OSMesa's default framebuffer draws to (glReadBuffer(GL_FRONT) is an error there)
        return instance == 0 ? GL_BACK : getBufferMode0(instance);
    }

    public void createGLContext() {
        String osmesa = System.getProperty("rlm.gl.osmesa");
        int w = Math.max(1, component.getWidth()), h = Math.max(1, component.getHeight());
        // Turnip: used instead of the phone's Vulkan driver on Qualcomm GPUs (see vk_turnip.c)
        String turnip = System.getProperty("rlm.gl.turnip");
        String err = createGLContext0(instance, osmesa, turnip, depthBits, stencilBits, w, h);
        if (err != null) throw new RuntimeException("rlm-gl: " + err);
        initLwjgl();
        current = this;
        System.out.println("rlm-gl: OpenGL context " + w + "x" + h + " created");
    }

    public int setSwapInterval(int interval) {
        return 0; // no vsync for a memory buffer; RuneLite then uses its FPS target
    }

    public void makeCurrent() {
        String err = makeCurrent0(instance, Math.max(1, component.getWidth()), Math.max(1, component.getHeight()));
        if (err != null) throw new RuntimeException("rlm-gl: " + err);
    }

    public void detachCurrent() {
        detachCurrent0(instance);
    }

    public void swapBuffers() {
        // Publishes the frame; follows the component if it was resized since the last frame
        String err = swapBuffers0(instance, Math.max(1, component.getWidth()), Math.max(1, component.getHeight()));
        if (err != null) throw new RuntimeException("rlm-gl: " + err);
    }

    public long getGLContext() {
        return instance == 0 ? 0 : getGLContext0(instance);
    }

    public long getCGLShareGroup() {
        return 0;
    }

    public long getGLXDisplay() {
        return 0;
    }

    public long getWGLHDC() {
        return 0;
    }

    /**
     * Copies the latest finished frame (ARGB) into {@code screen} at (x, y), clipped to w x h.
     * Called by ScreenCompositor from the Android render thread. Returns false if no frame.
     */
    public static boolean blitFrame(int[] screen, int screenW, int screenH, int x, int y, int w, int h) {
        AWTContext c = current;
        if (c == null || c.instance == 0) return false;
        return blitFrame0(c.instance, screen, screenW, screenH, x, y, w, h);
    }

    /**
     * RuneLite calls GL.createCapabilities() next. LWJGL normally finds GL functions in the
     * library named by org.lwjgl.opengl.libname via dlsym, but OSMesa only exports part of
     * them by name (e.g. not glClipControl), so create LWJGL's GL with a function provider
     * backed by OSMesaGetProcAddress. LWJGL lives in RuneLite's class loader, so this goes
     * through reflection; with -Dorg.lwjgl.opengl.explicitInit=true nothing was created yet.
     */
    private void initLwjgl() {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        if (loader == null || !canLoad(loader, "org.lwjgl.opengl.GL")) loader = component.getClass().getClassLoader();
        try {
            Class<?> gl = Class.forName("org.lwjgl.opengl.GL", true, loader);
            Class<?> fp = Class.forName("org.lwjgl.system.FunctionProvider", true, loader);
            if (gl.getMethod("getFunctionProvider").invoke(null) != null) return; // already created
            InvocationHandler h = (proxy, m, args) -> provide(m, args);
            Object provider = Proxy.newProxyInstance(loader, new Class<?>[]{fp}, h);
            gl.getMethod("create", fp).invoke(null, provider);
            System.out.println("rlm-gl: LWJGL GL created with OSMesaGetProcAddress");
        } catch (ReflectiveOperationException | RuntimeException e) {
            System.out.println("rlm-gl: LWJGL GL.create(provider) failed, LWJGL will use its default: " + e);
        }
    }

    private Object provide(Method m, Object[] args) {
        switch (m.getName()) {
            case "getFunctionAddress": {
                String name;
                if (args[args.length - 1] instanceof ByteBuffer) {
                    ByteBuffer b = ((ByteBuffer) args[args.length - 1]).duplicate();
                    StringBuilder sb = new StringBuilder();
                    while (b.hasRemaining()) {
                        byte c = b.get();
                        if (c == 0) break;
                        sb.append((char) c);
                    }
                    name = sb.toString();
                } else {
                    name = String.valueOf(args[args.length - 1]);
                }
                return getProcAddress0(name);
            }
            case "free":
                return null;
            case "hashCode":
                return System.identityHashCode(this);
            case "equals":
                return args[0] == null ? Boolean.FALSE : Boolean.valueOf(Proxy.getInvocationHandler(args[0]) != null
                        && args[0] == this);
            case "toString":
                return "rlm-gl OSMesa function provider";
            default:
                return null;
        }
    }

    private static boolean canLoad(ClassLoader l, String name) {
        try {
            Class.forName(name, false, l);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    // --- native (librlmgl.so)
    private static native long create0();

    private static native void destroy0(long instance);

    private static native String createGLContext0(long instance, String osmesaPath, String turnipPath, int depth,
                                                  int stencil, int w, int h);

    private static native String makeCurrent0(long instance, int w, int h);

    private static native void detachCurrent0(long instance);

    private static native String swapBuffers0(long instance, int w, int h);

    private static native long getGLContext0(long instance);

    private static native int getBufferMode0(long instance);

    private static native long getProcAddress0(String name);

    private static native boolean blitFrame0(long instance, int[] screen, int screenW, int screenH,
                                             int x, int y, int w, int h);
}
