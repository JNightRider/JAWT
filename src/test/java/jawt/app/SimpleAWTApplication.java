package jawt.app;

import com.jme3.app.SimpleApplication;
import com.jme3.system.JmeCanvasContext;
import jawt.app.jme3.JmeJBulletApp;
import jawt.app.jme3.JmeSimpleApp;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author wil
 */
public class SimpleAWTApplication extends JFrame {

    private SimpleApplication simpleApp;

    public SimpleAWTApplication() {
        initComponents();
    }

    @SuppressWarnings("unchecked")
    private void initComponents() {
        jPanel1 = new JPanel();
        jPanel2 = new JPanel();
        jPanel3 = new JPanel();

        jButton1 = new JButton();
        jButton2 = new JButton();


        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setTitle("AWT - LWJGL3");

        jPanel1.setLayout(new BorderLayout());
        jPanel2.setPreferredSize(new Dimension(534, 40));

        jButton1.setText("exit");
        jButton1.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                if (simpleApp != null) {
                    simpleApp.stop();
                    System.exit(0);
                }
            }
        });

        jButton2.setText("Load Scene3D");
        jButton2.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                JOptionPane.showMessageDialog(null, "USE >> setupJMEContext(true)");
            }
        });

        GroupLayout jPanel2Layout = new GroupLayout(jPanel2);
        jPanel2.setLayout(jPanel2Layout);
        jPanel2Layout.setHorizontalGroup(
                jPanel2Layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                        .addGroup(GroupLayout.Alignment.TRAILING, jPanel2Layout.createSequentialGroup()
                                .addContainerGap(317, Short.MAX_VALUE)
                                .addComponent(jButton2)
                                .addGap(18, 18, 18)
                                .addComponent(jButton1)
                                .addContainerGap())
        );
        jPanel2Layout.setVerticalGroup(
                jPanel2Layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                        .addGroup(GroupLayout.Alignment.TRAILING, jPanel2Layout.createSequentialGroup()
                                .addContainerGap(9, Short.MAX_VALUE)
                                .addGroup(jPanel2Layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                                        .addComponent(jButton1)
                                        .addComponent(jButton2))
                                .addContainerGap())
        );

        jPanel1.add(jPanel2, BorderLayout.PAGE_END);
        jPanel3.setLayout(new BorderLayout());
        jPanel1.add(jPanel3, BorderLayout.CENTER);

        getContentPane().add(jPanel1, BorderLayout.CENTER);

        /* start jme3 */
        setupJMEContext(true);

        pack();
        setLocationRelativeTo(null);
    }

    private void setupJMEContext(boolean escene3D) {
        simpleApp = escene3D ? new JmeJBulletApp() : new JmeSimpleApp();
        simpleApp.createCanvas();
        simpleApp.startCanvas();

        Canvas canvas = ((JmeCanvasContext) simpleApp.getContext()).getCanvas();
        jPanel3.add(canvas, BorderLayout.CENTER);
    }

    public static void main(String args[]) {
        try {
            for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        } catch (ClassNotFoundException ex) {
            Logger.getLogger(SimpleAWTApplication.class.getName()).log(Level.SEVERE, null, ex);
        } catch (InstantiationException ex) {
            Logger.getLogger(SimpleAWTApplication.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IllegalAccessException ex) {
            Logger.getLogger(SimpleAWTApplication.class.getName()).log(Level.SEVERE, null, ex);
        } catch (javax.swing.UnsupportedLookAndFeelException ex) {
            Logger.getLogger(SimpleAWTApplication.class.getName()).log(Level.SEVERE, null, ex);
        }
        EventQueue.invokeLater(new Runnable() {
            public void run() {
                new SimpleAWTApplication().setVisible(true);
            }
        });
    }


    private JButton jButton1;
    private JButton jButton2;
    private JPanel jPanel1;
    private JPanel jPanel2;
    private JPanel jPanel3;
}