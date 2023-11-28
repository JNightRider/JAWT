package jawt.app.jme3;

import com.jme3.app.SimpleApplication;
import com.jme3.input.controls.ActionListener;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.scene.Geometry;
import com.jme3.scene.shape.Box;

public class JmeSimpleApp extends SimpleApplication  {

    Geometry geom;
    @Override
    public void simpleInitApp() {
        getFlyByCamera().setEnabled(false);
        getFlyByCamera().unregisterInput();

        Box b = new Box(1, 1, 1);
        geom = new Geometry("Box", b);

        Material mat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        mat.setColor("Color", ColorRGBA.Orange);
        geom.setMaterial(mat);

        rootNode.attachChild(geom);
    }

    @Override
    public void simpleUpdate(float tpf) {
        float speed = FastMath.PI/2;
        geom.rotate(0, tpf * speed, 0);
    }
}
