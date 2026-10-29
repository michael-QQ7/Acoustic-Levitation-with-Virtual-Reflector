package acousticfield3d.renderer;

import acousticfield3d.math.Matrix4f;
import acousticfield3d.scene.MeshEntity;
import acousticfield3d.scene.Scene;
import acousticfield3d.simulation.Simulation;
import java.nio.FloatBuffer;
import java.util.HashMap;

import com.jogamp.opengl.GL2;

/**
 *
 * @author Asier
 */
public class ShaderTransducers extends Shader{
    private int numberOfTransducers;
    
    int transPosition;
    int transNormal;
    int transSpecs;
    int reflectorEnabled, reflectorY, reflectorMagnitude, reflectorPhase;
    
    public ShaderTransducers(String vProgram, String fProgram, int renderingOrder, int numberOfTransducers) {
        super(vProgram, fProgram, renderingOrder);
        this.numberOfTransducers = numberOfTransducers;
    }
    
    public void updateTransducersNumber(int n, GL2 gl, HashMap<String,String> templates){
        if (n != numberOfTransducers){
            numberOfTransducers = n;
            reload(gl,templates);
        }
    }
    
    public int getNumberOfTransducers() {
        return numberOfTransducers;
    }

    public void setNumberOfTransducers(int numberOfTransducers) {
        this.numberOfTransducers = numberOfTransducers;
    }

    @Override
    void getUniforms(GL2 gl) {
        super.getUniforms(gl);
        
        transPosition = gl.glGetUniformLocation(shaderProgramID, "tPos");
        transNormal = gl.glGetUniformLocation(shaderProgramID, "tNorm");
        transSpecs = gl.glGetUniformLocation(shaderProgramID, "tSpecs");
        reflectorEnabled = gl.glGetUniformLocation(shaderProgramID, "reflectorEnabled");
        reflectorY = gl.glGetUniformLocation(shaderProgramID, "reflectorY");
        reflectorMagnitude = gl.glGetUniformLocation(shaderProgramID, "reflectorMagnitude");
        reflectorPhase = gl.glGetUniformLocation(shaderProgramID, "reflectorPhase");
    }

    @Override
    protected String preProcessFragment(String sourceCode, HashMap<String,String> templates) {
        
        return super.preProcessFragment( 
                sourceCode.replaceAll("_N_TRANS_", getNumberOfTransducers() + ""), templates);
    }

    @Override
    void bindUniforms(GL2 gl, Scene scene,Renderer renderer, Simulation s, MeshEntity me, 
            Matrix4f projectionViewModel, Matrix4f projectionView, Matrix4f viewModel, Matrix4f model, FloatBuffer fb) {
        super.bindUniforms(gl, scene, renderer, s, me, projectionViewModel, projectionView, viewModel, model, fb);
        
        acousticfield3d.simulation.ReflectorSettings settings = s.getReflector();
        gl.glUniform1i(reflectorEnabled, settings.isEnabled() ? 1 : 0);
        gl.glUniform1f(reflectorY, (float)settings.getPlaneY());
        gl.glUniform1f(reflectorMagnitude, (float)settings.getMagnitude());
        gl.glUniform1f(reflectorPhase, (float)Math.toRadians(settings.getPhaseDegrees()));
        gl.glUniform3fv(transPosition, renderer.nTransducers, renderer.positions);
        gl.glUniform3fv(transNormal, renderer.nTransducers, renderer.normals);
        gl.glUniform4fv(transSpecs, renderer.nTransducers, renderer.specs);
    }
    
    
}
