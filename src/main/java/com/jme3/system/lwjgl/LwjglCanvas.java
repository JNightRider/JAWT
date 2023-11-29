/* Copyright (c) 2009-2023 jMonkeyEngine.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the
 *    distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its
 *    contributors may be used to endorse or promote products derived
 *    from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS
 * FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.jme3.system.lwjgl;

import com.jme3.input.KeyInput;
import com.jme3.input.MouseInput;
import com.jme3.input.awt.AwtKeyInput;
import com.jme3.input.awt.AwtMouseInput;
import com.jme3.system.AppSettings;
import com.jme3.system.JmeCanvasContext;

import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

import jawt.lwjgl.opengl.awt.AWTGLCanvas;
import jawt.lwjgl.opengl.awt.GLData;

/**
 * Clase <code>AWTLwjglCanvas</code> encargado de gestionar un renderizado
 * combinacional con la <b>API Swing-AWT</b>.
 * 
 * @author wil
 * @version 1.0.0
 * 
 * @since 1.0.0
 */
public class LwjglCanvas extends LwjglWindow implements JmeCanvasContext, Runnable {

    private static final Logger LOGGER = Logger.getLogger(LwjglCanvas.class.getName());
    
    private final AWTGLCanvas glAWTCanvas;
    private GLData glData;
    
    private final AtomicBoolean hasNativePeer = new AtomicBoolean(false);
    private final AtomicBoolean showing = new AtomicBoolean(false);
    private AtomicBoolean needResize = new AtomicBoolean(false);

    private final Semaphore signalTerminate = new Semaphore(0);
    private final Semaphore signalTerminated = new Semaphore(0);

    private final Object lock = new Object();
    private int width = 1;
    private int height = 1;

    private AwtKeyInput keyInput;
    private AwtMouseInput mouseInput;
    
    public LwjglCanvas() {
        super(Type.Canvas);
        
        glData      = new GLData();
        glAWTCanvas = new AWTGLCanvas(glData) {
            @Override
            public void disposeCanvas() {
            }

            @Override
            public void addNotify() {
                super.addNotify();
                synchronized (lock) {
                    hasNativePeer.set(true);
                }
                requestFocusInWindow();
            }

            @Override
            public void removeNotify() {
                synchronized (lock) {
                    hasNativePeer.set(false);
                }
                super.removeNotify();
            }};

        glAWTCanvas.setIgnoreRepaint(true);
        glAWTCanvas.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                synchronized (lock) {
                    int framebufferWidth = glAWTCanvas.getFramebufferWidth();
                    int framebufferHeight = glAWTCanvas.getFramebufferHeight();
                    if (width != framebufferWidth || height != framebufferHeight) {
                        width = framebufferWidth;
                        height = framebufferHeight;
                        needResize.set(true);
                    }
                }
            }
        });
    }

    @Override
    public Canvas getCanvas() {
        return this.glAWTCanvas;
    }

    @Override
    protected void showWindow() {
    }

    @Override
    protected void setWindowIcon(final AppSettings settings) {
    }

    @Override
    public void setTitle(String title) {
    }

    @Override
    public KeyInput getKeyInput() {
        if (keyInput == null) {
            keyInput = new AwtKeyInput();
            keyInput.setInputSource(glAWTCanvas);
        }

        return keyInput;
    }

    @Override
    public MouseInput getMouseInput() {
        if (mouseInput == null) {
            mouseInput = new AwtMouseInput();
            mouseInput.setInputSource(glAWTCanvas);
        }

        return mouseInput;
    }

    public boolean checkVisibilityState() {
        if (!hasNativePeer.get()) {
            return false;
        }

        boolean currentShowing = glAWTCanvas.isShowing();
        showing.set(currentShowing);
        return currentShowing;
    }


    @Override
    protected void destroyContext() {
        synchronized (lock) {
            glAWTCanvas.deleteContext();
        }
        // request the cleanup
        signalTerminate.release();
        try {
            // wait until the thread is done with the cleanup
            signalTerminated.acquire();
        } catch (InterruptedException ignored) {
        }
        super.destroyContext();
    }

    @Override
    protected void runLoop() {
        if (needResize.get()) {
            needResize.set(false);
            settings.setResolution(width, height);
            listener.reshape(width, height);
        }

        if (!checkVisibilityState()) {
            return;
        }

        try {
            glAWTCanvas.beforeRender();
            super.runLoop();
            glAWTCanvas.swapBuffers();

            glAWTCanvas.afterRender();
        } catch (Exception ex) {
            listener.handleError("..", ex);
        }
        
        try {
            if (signalTerminate.tryAcquire(10, TimeUnit.MILLISECONDS)) {
                glAWTCanvas.doDisposeCanvas();
                signalTerminated.release();
                return;
            }
        } catch (InterruptedException ignored) { }
    }

    @Override
    public void setSettings(AppSettings settings) {
        super.setSettings(settings);
        Object glGetData = settings.get("GLData");
        if (glGetData != null && (glGetData instanceof GLData)) {
            this.glData = (GLData) glGetData;
            this.glAWTCanvas.setData(this.glData);
            
            LOGGER.log(Level.INFO, "GLData set : {0}", glGetData.getClass().getCanonicalName());
        }
       
        if (settings.getBitsPerPixel() == 24) {
            this.glData.redSize = 8;
            this.glData.blueSize = 8;
            this.glData.greenSize = 8;
        } else if (settings.getBitsPerPixel() == 16) {
            this.glData.redSize = 5;
            this.glData.blueSize = 5;
            this.glData.greenSize = 6;
        }



        this.glData.depthSize = settings.getBitsPerPixel();
        this.glData.alphaSize = settings.getAlphaBits();
        this.glData.sRGB = settings.isGammaCorrection();
        
        this.glData.depthSize = settings.getDepthBits();
        this.glData.stencilSize = settings.getStencilBits();
        this.glData.samples = settings.getSamples();
        this.glData.stereo = settings.useStereo3D();

        //this.glData.profile = GLData.Profile.COMPATIBILITY;
        /*this.glData.pixelFormatFloat = true;*/
        this.glAWTCanvas.setPreferredSize(new Dimension(settings.getWidth(), settings.getHeight()));
        this.glAWTCanvas.glAwtInfor();
    }

    @Override
    public int getFramebufferHeight() {
        return glAWTCanvas.getFramebufferHeight();
    }

    @Override
    public int getFramebufferWidth() {
        return glAWTCanvas.getFramebufferWidth();
    }
}
