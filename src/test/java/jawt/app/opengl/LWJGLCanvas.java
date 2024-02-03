package jawt.app.opengl;

import org.lwjgl.*;
import org.lwjgl.opengl.*;
import org.lwjgl.system.*;
import org.lwjgl.system.jawt.JAWT;
import org.lwjgl.system.jawt.*;
import org.lwjgl.system.linux.*;

import java.awt.*;
import java.awt.event.*;
import java.nio.*;
import java.util.*;


import static org.lwjgl.opengl.GL.*;
import static org.lwjgl.opengl.GL11.*;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.glfw.GLFWNativeWin32.*;
import static org.lwjgl.opengl.GLX13.*;
import static org.lwjgl.system.MemoryStack.*;
import static org.lwjgl.system.MemoryUtil.*;
import static org.lwjgl.system.jawt.JAWTFunctions.*;
import static org.lwjgl.system.linux.X11.*;

/**
 * A Canvas component that uses OpenGL for rendering.
 *
 * <p>This implementation supports Windows only.</p>
 */
@SuppressWarnings("serial")
public class LWJGLCanvas extends Canvas {

    private final JAWT awt;

    private JAWTDrawingSurface ds;

    private long context;

    private GLCapabilities caps;

    public LWJGLCanvas() {
        awt = JAWT.calloc();
        awt.version(JAWT_VERSION_1_4);
        if (!JAWT_GetAWT(awt)) {
            throw new IllegalStateException("GetAWT failed");
        }

        // AWT event listeners are invoked in the EDT

        addComponentListener(new ComponentAdapter() {
            @Override public void componentResized(ComponentEvent e) {
                if (context != NULL) {
                    paint();
                }
            }
        });
    }

    @Override
    public void update(Graphics g) {
        paint(g);
    }

    @Override
    public void paint(Graphics g) {
        paint();
        repaint();
    }

    private void paint() {
        if (ds == null) {
            // Get the drawing surface
            ds = JAWT_GetDrawingSurface(this, awt.GetDrawingSurface());
            if (ds == null) {
                throw new IllegalStateException("awt->GetDrawingSurface() failed");
            }
        }

        // Lock the drawing surface
        int lock = JAWT_DrawingSurface_Lock(ds, ds.Lock());
        if ((lock & JAWT_LOCK_ERROR) != 0) {
            throw new IllegalStateException("ds->Lock() failed");
        }

        try {
            // Get the drawing surface info
            JAWTDrawingSurfaceInfo dsi = JAWT_DrawingSurface_GetDrawingSurfaceInfo(ds, ds.GetDrawingSurfaceInfo());
            if (dsi == null) {
                throw new IllegalStateException("ds->GetDrawingSurfaceInfo() failed");
            }

            try {
                switch (Platform.get()) {
                    case LINUX:
                        // Get the platform-specific drawing info
                        JAWTX11DrawingSurfaceInfo dsi_x11 = JAWTX11DrawingSurfaceInfo.create(dsi.platformInfo());

                        long drawable = dsi_x11.drawable();
                        if (drawable == NULL) {
                            break;
                        }

                        if (context == NULL) {
                            createContextGLX(dsi_x11);
                        } else {
                            if (!glXMakeCurrent(dsi_x11.display(), drawable, context)) {
                                throw new IllegalStateException("glXMakeCurrent() failed");
                            }
                            GL.setCapabilities(caps);
                        }

                        render(getWidth(), getHeight());
                        glXSwapBuffers(dsi_x11.display(), drawable);

                        glXMakeCurrent(dsi_x11.display(), NULL, NULL);
                        GL.setCapabilities(null);
                        break;
                    case WINDOWS:
                        // Get the platform-specific drawing info
                        JAWTWin32DrawingSurfaceInfo dsi_win = JAWTWin32DrawingSurfaceInfo.create(dsi.platformInfo());

                        long hdc = dsi_win.hdc();
                        if (hdc == NULL) {
                            break;
                        }

                        // The render method is invoked in the EDT
                        if (context == NULL) {
                            createContextGLFW(dsi_win);
                        } else {
                            glfwMakeContextCurrent(context);
                            GL.setCapabilities(caps);
                        }

                        try (MemoryStack stack = stackPush()) {
                            IntBuffer pw = stack.mallocInt(1);
                            IntBuffer ph = stack.mallocInt(1);

                            glfwGetFramebufferSize(context, pw, ph);

                            render(pw.get(0), ph.get(0));
                        }
                        glfwSwapBuffers(context);

                        glfwMakeContextCurrent(NULL);
                        GL.setCapabilities(null);
                        break;
                }
            } finally {
                // Free the drawing surface info
                JAWT_DrawingSurface_FreeDrawingSurfaceInfo(dsi, ds.FreeDrawingSurfaceInfo());
            }
        } finally {
            // Unlock the drawing surface
            JAWT_DrawingSurface_Unlock(ds, ds.Unlock());
        }
    }

    protected void render(int w, int h) {
        float aspect = (float) w / h;
        double now = System.currentTimeMillis() * 0.001;
        float width = (float) Math.abs(Math.sin(now * 0.3));
        glClear(GL_COLOR_BUFFER_BIT);
        glViewport(0, 0, w, h);
        glBegin(GL_QUADS);
        glColor3f(0.4f, 0.6f, 0.8f);
        glVertex2f(-0.75f * width / aspect, 0.0f);
        glVertex2f(0, -0.75f);
        glVertex2f(+0.75f * width / aspect, 0);
        glVertex2f(0, +0.75f);
        glEnd();
        
        Toolkit.getDefaultToolkit().sync();
    }

    private void createContextGLFW(JAWTWin32DrawingSurfaceInfo dsi_win) {
        // glfwWindowHint can be used here to configure the GL context
        context = glfwAttachWin32Window(dsi_win.hwnd(), NULL);
        if (context == NULL) {
            throw new IllegalStateException("Failed to attach win32 window.");
        }

        // Any callbacks registered here will work. But care must be taken because
        // the callbacks are NOT invoked in the EDT, but in an AWT thread that
        // does the event polling. Many GLFW functions that require main thread
        // invocation, should only be called in that thread.

        // Because of how input focus is implemented in AWT, it is recommended that AWT
        // KeyListeners are always used for keyboard input.

        glfwMakeContextCurrent(context);
        
        caps = GL.createCapabilities();        
        glClearColor(0.3f, 0.4f, 0.5f, 1);
    }

    // Simplest possible context creation.
    private void createContextGLX(JAWTX11DrawingSurfaceInfo dsi_x11) {
        long display  = dsi_x11.display();
        long drawable = dsi_x11.drawable();

        PointerBuffer configs = Objects.requireNonNull(glXGetFBConfigs(display, 0));

        long config = NULL;
        for (int i = 0; i < configs.remaining(); i++) {
            XVisualInfo vi = glXGetVisualFromFBConfig(display, configs.get(i));
            if (vi == null) {
                continue;
            }
            try {
                if (vi.visualid() == dsi_x11.visualID()) {
                    config = configs.get(i);
                    break;
                }
            } finally {
                nXFree(vi.address());
            }
        }
        XFree(configs);

        if (config == NULL) {
            throw new IllegalStateException("Failed to find a compatible GLXFBConfig");
        }

        context = glXCreateNewContext(display, config, GLX_RGBA_TYPE, NULL, true);
        if (context == NULL) {
            throw new IllegalStateException("glXCreateContext() failed");
        }

        if (!glXMakeCurrent(display, drawable, context)) {
            throw new IllegalStateException("glXMakeCurrent() failed");
        }

        caps = GL.createCapabilities();
    }

    public void destroy() {
        // Free the drawing surface
        JAWT_FreeDrawingSurface(ds, awt.FreeDrawingSurface());

        awt.free();

        if (context != NULL) {
            glfwDestroyWindow(context);
        }
    }

}