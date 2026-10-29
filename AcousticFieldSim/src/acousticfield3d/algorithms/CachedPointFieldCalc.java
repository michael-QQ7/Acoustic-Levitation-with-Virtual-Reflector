package acousticfield3d.algorithms;

import acousticfield3d.gui.MainForm;
import acousticfield3d.gui.misc.ForcePlotsFrame;
import acousticfield3d.math.M;
import acousticfield3d.math.Vector2f;
import acousticfield3d.math.Vector3f;
import acousticfield3d.renderer.Renderer;
import java.nio.FloatBuffer;

/**
 *
 * @author Asier
 */
public class CachedPointFieldCalc {
    Vector3f position = new Vector3f();
    
    Vector2f pre = new Vector2f();
    Vector2f gx = new Vector2f(), gy = new Vector2f(), gz = new Vector2f();
    Vector2f gxy = new Vector2f(), gxz = new Vector2f(), gyz = new Vector2f();
    Vector2f gxx = new Vector2f(), gyy = new Vector2f(), gzz = new Vector2f();
    
    Vector2f gxxx = new Vector2f(), gyyy = new Vector2f(), gzzz = new Vector2f();
    Vector2f gxyy = new Vector2f(), gxzz = new Vector2f();
    Vector2f gyxx = new Vector2f(), gyzz = new Vector2f();
    Vector2f gzxx = new Vector2f(), gzyy = new Vector2f();
         
    Vector2f swap = new Vector2f(), tmp = new Vector2f();
    Vector3f nor = new Vector3f();
    Vector3f diffVec = new Vector3f();
    Vector3f diffVecS = new Vector3f();
    Vector3f tPos = new Vector3f();
    
    double K1, K2;
    double M1, M2;
    
    final int nTotalTrans, nTrans;
    final float h;
    final boolean useDirectivity;
    
    double[] a,b, ka, kb; //per transducers
    double[] xa,xb, xka, xkb;
    double[] ya,yb, yka, ykb;
    double[] za,zb, zka, zkb;
    
    double[] xya,xyb, xyka, xykb;
    double[] xza,xzb, xzka, xzkb;
    double[] yza,yzb, yzka, yzkb;
    double[] xxa,xxb, xxka, xxkb;
    double[] yya,yyb, yyka, yykb;
    double[] zza,zzb, zzka, zzkb;
    
    double[] xxxa,xxxb, xxxka, xxxkb;
    double[] yyya,yyyb, yyyka, yyykb;
    double[] zzza,zzzb, zzzka, zzzkb;
    double[] xyya,xyyb, xyyka, xyykb;
    double[] xzza,xzzb, xzzka, xzzkb;
    double[] yxxa,yxxb, yxxka, yxxkb;
    double[] yzza,yzzb, yzzka, yzzkb;
    double[] zxxa,zxxb, zxxka, zxxkb;
    double[] zyya,zyyb, zyyka, zyykb;

    double lowPressureK = 1;
    
    public static CachedPointFieldCalc create(final Vector3f pos, MainForm mf){
        // Images share the physical channel phase; optimizer dimension never doubles.
        final boolean reflection = false;
        final boolean directivity = true;
        
        final float requestedDiv = mf.miscPanel.getFiniteDiffH();
        final float divisor = Float.isFinite(requestedDiv) ? Math.max(32, requestedDiv) : 64;
        final float h = mf.simulation.getWavelenght() / divisor;
        CachedPointFieldCalc cp = new CachedPointFieldCalc(pos, reflection,directivity, h, mf.renderer);
        return cp;        
    }
    
    public CachedPointFieldCalc(final Vector3f pos, boolean reflectorEnabled, boolean useDirectivity, float h, Renderer r) {
        this.useDirectivity = useDirectivity;
        this.h = h;
        nTotalTrans = r.getnTransducers();
        nTrans = nTotalTrans;
        position.set( pos );
    }

    public void setPosition(Vector3f position) {
        this.position = position;
    }

    public Vector3f getPosition() {
        return position;
    }

    
    public int getNTrans() {
        return nTrans;
    }

    public double getLowPressureK() {
        return lowPressureK;
    }

    public void setLowPressureK(double lowPressureK) {
        this.lowPressureK = lowPressureK;
    }
    
    //<editor-fold defaultstate="collapsed" desc="pressure">
    
    public void updatePressure(final double[] v){
        pre.set(0,0);
        for(int i = 0; i < nTrans; ++i){
            final double cosP = Math.cos( v[i] );
            final double sinP = Math.sin( v[i] );
            a[i] = ka[i]*cosP - kb[i]*sinP;
            b[i] = ka[i]*sinP + kb[i]*cosP;
            pre.x += a[i];
            pre.y += b[i];
        }
    }
    
    public void evalField(Vector2f field){
        field.set( pre );
    }
    public double evalPressure(){
        return pre.length();
    }
    public void gradientPressure(double[] g){
        final double p = pre.length();
        if (p < 1e-30) { java.util.Arrays.fill(g, 0); return; }
        final double aDivP = pre.x / p;
        final double bDivP = pre.y / p;
        for(int i = 0; i < nTrans; ++i){
            g[i] = bDivP * a[i] - aDivP * b[i];
        }
    }
//</editor-fold>
    
    //<editor-fold defaultstate="collapsed" desc="gorkov">  
   
    public void updateGorkov(final double[] v){
        pre.set(0);
        gx.set(0); gy.set(0); gz.set(0);
        for(int i = 0; i < nTrans; ++i){
            final double cosP = Math.cos( v[i] );
            final double sinP = Math.sin( v[i] );
            
            a[i] = ka[i]*cosP - kb[i]*sinP;
            b[i] = ka[i]*sinP + kb[i]*cosP;
            pre.x += a[i]; pre.y += b[i];
            
            xa[i] = xka[i]*cosP - xkb[i]*sinP;
            xb[i] = xka[i]*sinP + xkb[i]*cosP;
            gx.x += xa[i]; gx.y += xb[i];
            
            ya[i] = yka[i]*cosP - ykb[i]*sinP;
            yb[i] = yka[i]*sinP + ykb[i]*cosP;
            gy.x += ya[i]; gy.y += yb[i];
            
            za[i] = zka[i]*cosP - zkb[i]*sinP;
            zb[i] = zka[i]*sinP + zkb[i]*cosP;
            gz.x += za[i]; gz.y += zb[i];
        }
    }
    
    public double evalGorkov(){
        return  M1 * lowPressureK * pre.dot(pre) - M2*( gx.dot(gx) + gy.dot(gy) + gz.dot(gz));
    }
    public void gradientGorkov(double[] g){
        for(int i = 0; i < nTrans; ++i){
            g[i] = K1 * lowPressureK *( pre.x*-b[i] + pre.y*a[i]) - 
                   K2*( gx.x*-xb[i]+gx.y*xa[i] + gy.x*-yb[i]+gy.y*ya[i] + gz.x*-zb[i]+gz.y*za[i]);
        }
    }
    //</editor-fold>
   
    //<editor-fold defaultstate="collapsed" desc="gorkovGradient">  
 
    public void updateGorkovGradient(final double[] v){
        pre.set(0);
        gx.set(0); gy.set(0); gz.set(0);
        gxx.set(0); gyy.set(0); gzz.set(0);
        gxy.set(0); gxz.set(0); gyz.set(0);
        for(int i = 0; i < nTrans; ++i){
            final double cosP = Math.cos( v[i] );
            final double sinP = Math.sin( v[i] );
            
            a[i] = ka[i]*cosP - kb[i]*sinP;
            b[i] = ka[i]*sinP + kb[i]*cosP;
            pre.x += a[i]; pre.y += b[i];
            
            xa[i] = xka[i]*cosP - xkb[i]*sinP;
            xb[i] = xka[i]*sinP + xkb[i]*cosP;
            gx.x += xa[i]; gx.y += xb[i];
            
            ya[i] = yka[i]*cosP - ykb[i]*sinP;
            yb[i] = yka[i]*sinP + ykb[i]*cosP;
            gy.x += ya[i]; gy.y += yb[i];
            
            za[i] = zka[i]*cosP - zkb[i]*sinP;
            zb[i] = zka[i]*sinP + zkb[i]*cosP;
            gz.x += za[i]; gz.y += zb[i];
            
            xya[i] = xyka[i]*cosP - xykb[i]*sinP;
            xyb[i] = xyka[i]*sinP + xykb[i]*cosP;
            gxy.x += xya[i]; gxy.y += xyb[i];
            
            xza[i] = xzka[i]*cosP - xzkb[i]*sinP;
            xzb[i] = xzka[i]*sinP + xzkb[i]*cosP;
            gxz.x += xza[i]; gxz.y += xzb[i];
            
            yza[i] = yzka[i]*cosP - yzkb[i]*sinP;
            yzb[i] = yzka[i]*sinP + yzkb[i]*cosP;
            gyz.x += yza[i]; gyz.y += yzb[i];
            
            xxa[i] = xxka[i]*cosP - xxkb[i]*sinP;
            xxb[i] = xxka[i]*sinP + xxkb[i]*cosP;
            gxx.x += xxa[i]; gxx.y += xxb[i];
            
            yya[i] = yyka[i]*cosP - yykb[i]*sinP;
            yyb[i] = yyka[i]*sinP + yykb[i]*cosP;
            gyy.x += yya[i]; gyy.y += yyb[i];
            
            zza[i] = zzka[i]*cosP - zzkb[i]*sinP;
            zzb[i] = zzka[i]*sinP + zzkb[i]*cosP;
            gzz.x += zza[i]; gzz.y += zzb[i];
        }
    }
    
    public void calcGorkovGradient(Vector3f position, Vector3f forces, MainForm mf, double[] phases){
        setPosition(position);
        initFieldConstants(mf);
        updateGorkovGradient(phases);
        forces.x = (float) evalGorkovGradientX();
        forces.y = (float) evalGorkovGradientY();
        forces.z = (float) evalGorkovGradientZ();
    }
    
    public double evalGorkovGradientXProportion(){
        return  (K1*pre.dot(gx)) / (K2*( gx.dot(gxx) + gy.dot(gxy) + gz.dot(gxz)));
    }
    public double evalGorkovGradientYProportion(){
        return  (K1*pre.dot(gy)) / (K2*( gx.dot(gxy) + gy.dot(gyy) + gz.dot(gyz)));
    }
    public double evalGorkovGradientZProportion(){
        return  (K1*pre.dot(gz)) / (K2*( gx.dot(gxz) + gy.dot(gyz) + gz.dot(gzz)));
    }
    
    public double evalGorkovGradientX(){
        return  K1*pre.dot(gx) - K2*( gx.dot(gxx) + gy.dot(gxy) + gz.dot(gxz));
    }
    public double evalGorkovGradientY(){
        return  K1*pre.dot(gy) - K2*( gx.dot(gxy) + gy.dot(gyy) + gz.dot(gyz));
    }
    public double evalGorkovGradientZ(){
        return  K1*pre.dot(gz) - K2*( gx.dot(gxz) + gy.dot(gyz) + gz.dot(gzz));
    }
    public void gradientGorkovGradientX(double[] g){
        for(int i = 0; i < nTrans; ++i){
            g[i] = K1 * ( 
                    pre.x*-xb[i] + -b[i]*gx.x + pre.y*xa[i] + a[i]*gx.y ) - 
                   K2 * ( 
                    gx.x*-xxb[i] + -xb[i]*gxx.x + gx.y*xxa[i] + xa[i]*gxx.y +
                    gy.x*-xyb[i] + -yb[i]*gxy.x + gy.y*xya[i] + ya[i]*gxy.y +
                    gz.x*-xzb[i] + -zb[i]*gxz.x + gz.y*xza[i] + za[i]*gxz.y);
        }
    }
    public void gradientGorkovGradientY(double[] g){
        for(int i = 0; i < nTrans; ++i){
            g[i] = K1 * ( 
                    pre.x*-yb[i] + -b[i]*gy.x + pre.y*ya[i] + a[i]*gy.y ) - 
                   K2 * ( 
                    gx.x*-xyb[i] + -xb[i]*gxy.x + gx.y*xya[i] + xa[i]*gxy.y +
                    gy.x*-yyb[i] + -yb[i]*gyy.x + gy.y*yya[i] + ya[i]*gyy.y +
                    gz.x*-yzb[i] + -zb[i]*gyz.x + gz.y*yza[i] + za[i]*gyz.y);
        }
    }
    public void gradientGorkovGradientZ(double[] g){
        for(int i = 0; i < nTrans; ++i){
            g[i] = K1 * ( 
                    pre.x*-zb[i] + -b[i]*gz.x + pre.y*za[i] + a[i]*gz.y ) - 
                   K2 * ( 
                    gx.x*-xzb[i] + -xb[i]*gxz.x + gx.y*xza[i] + xa[i]*gxz.y +
                    gy.x*-yzb[i] + -yb[i]*gyz.x + gy.y*yza[i] + ya[i]*gyz.y +
                    gz.x*-zzb[i] + -zb[i]*gzz.x + gz.y*zza[i] + za[i]*gzz.y);
        }
    }
    //</editor-fold>
    
    //<editor-fold defaultstate="collapsed" desc="gorkovLaplacian">  
    public void allocateAndInit(MainForm mf){
        allocate(mf.renderer);
        initFieldConstants(mf);
    }
    
    public void allocate(Renderer r){
        a = new double[nTotalTrans]; b = new double[nTotalTrans];
        ka = new double[nTotalTrans]; kb = new double[nTotalTrans];
        
        xa = new double[nTotalTrans]; xb = new double[nTotalTrans];
        xka = new double[nTotalTrans]; xkb = new double[nTotalTrans];
        
        ya = new double[nTotalTrans]; yb = new double[nTotalTrans];
        yka = new double[nTotalTrans]; ykb = new double[nTotalTrans];
        
        za = new double[nTotalTrans]; zb = new double[nTotalTrans];
        zka = new double[nTotalTrans]; zkb = new double[nTotalTrans];
        
        xya = new double[nTotalTrans]; xyb = new double[nTotalTrans];
        xyka = new double[nTotalTrans]; xykb = new double[nTotalTrans];
        
        xza = new double[nTotalTrans]; xzb = new double[nTotalTrans];
        xzka = new double[nTotalTrans]; xzkb = new double[nTotalTrans];
        
        yza = new double[nTotalTrans]; yzb = new double[nTotalTrans];
        yzka = new double[nTotalTrans]; yzkb = new double[nTotalTrans];
        
        xxa = new double[nTotalTrans]; xxb = new double[nTotalTrans];
        xxka = new double[nTotalTrans]; xxkb = new double[nTotalTrans];
        
        yya = new double[nTotalTrans]; yyb = new double[nTotalTrans];
        yyka = new double[nTotalTrans]; yykb = new double[nTotalTrans];
        
        zza = new double[nTotalTrans]; zzb = new double[nTotalTrans];
        zzka = new double[nTotalTrans]; zzkb = new double[nTotalTrans];
        
        xxxa = new double[nTotalTrans]; xxxb = new double[nTotalTrans];
        xxxka = new double[nTotalTrans]; xxxkb = new double[nTotalTrans];
        yyya = new double[nTotalTrans]; yyyb = new double[nTotalTrans];
        yyyka = new double[nTotalTrans]; yyykb = new double[nTotalTrans];
        zzza = new double[nTotalTrans]; zzzb = new double[nTotalTrans];
        zzzka = new double[nTotalTrans]; zzzkb = new double[nTotalTrans];
        xyya = new double[nTotalTrans]; xyyb = new double[nTotalTrans];
        xyyka = new double[nTotalTrans]; xyykb = new double[nTotalTrans];
        xzza = new double[nTotalTrans]; xzzb = new double[nTotalTrans];
        xzzka = new double[nTotalTrans]; xzzkb = new double[nTotalTrans];
        yxxa = new double[nTotalTrans]; yxxb = new double[nTotalTrans];
        yxxka = new double[nTotalTrans]; yxxkb = new double[nTotalTrans];
        yzza = new double[nTotalTrans]; yzzb = new double[nTotalTrans];
        yzzka = new double[nTotalTrans]; yzzkb = new double[nTotalTrans];
        zxxa = new double[nTotalTrans]; zxxb = new double[nTotalTrans];
        zxxka = new double[nTotalTrans]; zxxkb = new double[nTotalTrans];
        zyya = new double[nTotalTrans]; zyyb = new double[nTotalTrans];
        zyyka = new double[nTotalTrans]; zyykb = new double[nTotalTrans];
    }
   
    
    public void updateGorkovLaplacian(final double[] v){
        gxxx.reset(); gyyy.reset(); gzzz.reset();
        gxyy.reset(); gxzz.reset(); gyxx.reset(); gyzz.reset(); gzxx.reset(); gzyy.reset();
        pre.set(0);
        gx.set(0); gy.set(0); gz.set(0);
        gxx.set(0); gyy.set(0); gzz.set(0);
        gxy.set(0); gxz.set(0); gyz.set(0);
        for(int i = 0; i < nTrans; ++i){
            final double cosP = Math.cos( v[i] );
            final double sinP = Math.sin( v[i] );
            
            a[i] = ka[i]*cosP - kb[i]*sinP;
            b[i] = ka[i]*sinP + kb[i]*cosP;
            pre.x += a[i]; pre.y += b[i];
            
            xa[i] = xka[i]*cosP - xkb[i]*sinP;
            xb[i] = xka[i]*sinP + xkb[i]*cosP;
            gx.x += xa[i]; gx.y += xb[i];
            
            ya[i] = yka[i]*cosP - ykb[i]*sinP;
            yb[i] = yka[i]*sinP + ykb[i]*cosP;
            gy.x += ya[i]; gy.y += yb[i];
            
            za[i] = zka[i]*cosP - zkb[i]*sinP;
            zb[i] = zka[i]*sinP + zkb[i]*cosP;
            gz.x += za[i]; gz.y += zb[i];
            
            xya[i] = xyka[i]*cosP - xykb[i]*sinP;
            xyb[i] = xyka[i]*sinP + xykb[i]*cosP;
            gxy.x += xya[i]; gxy.y += xyb[i];
            
            xza[i] = xzka[i]*cosP - xzkb[i]*sinP;
            xzb[i] = xzka[i]*sinP + xzkb[i]*cosP;
            gxz.x += xza[i]; gxz.y += xzb[i];
            
            yza[i] = yzka[i]*cosP - yzkb[i]*sinP;
            yzb[i] = yzka[i]*sinP + yzkb[i]*cosP;
            gyz.x += yza[i]; gyz.y += yzb[i];
            
            xxa[i] = xxka[i]*cosP - xxkb[i]*sinP;
            xxb[i] = xxka[i]*sinP + xxkb[i]*cosP;
            gxx.x += xxa[i]; gxx.y += xxb[i];
            
            yya[i] = yyka[i]*cosP - yykb[i]*sinP;
            yyb[i] = yyka[i]*sinP + yykb[i]*cosP;
            gyy.x += yya[i]; gyy.y += yyb[i];
            
            zza[i] = zzka[i]*cosP - zzkb[i]*sinP;
            zzb[i] = zzka[i]*sinP + zzkb[i]*cosP;
            gzz.x += zza[i]; gzz.y += zzb[i];
            xxxa[i] = xxxka[i]*cosP - xxxkb[i]*sinP;
            
            xxxb[i] = xxxka[i]*sinP + xxxkb[i]*cosP;
            gxxx.x += xxxa[i]; gxxx.y += xxxb[i];
            yyya[i] = yyyka[i]*cosP - yyykb[i]*sinP;
            yyyb[i] = yyyka[i]*sinP + yyykb[i]*cosP;
            gyyy.x += yyya[i]; gyyy.y += yyyb[i];
            zzza[i] = zzzka[i]*cosP - zzzkb[i]*sinP;
            zzzb[i] = zzzka[i]*sinP + zzzkb[i]*cosP;
            gzzz.x += zzza[i]; gzzz.y += zzzb[i];
            xyya[i] = xyyka[i]*cosP - xyykb[i]*sinP;
            xyyb[i] = xyyka[i]*sinP + xyykb[i]*cosP;
            gxyy.x += xyya[i]; gxyy.y += xyyb[i];
            xzza[i] = xzzka[i]*cosP - xzzkb[i]*sinP;
            xzzb[i] = xzzka[i]*sinP + xzzkb[i]*cosP;
            gxzz.x += xzza[i]; gxzz.y += xzzb[i];
            yxxa[i] = yxxka[i]*cosP - yxxkb[i]*sinP;
            yxxb[i] = yxxka[i]*sinP + yxxkb[i]*cosP;
            gyxx.x += yxxa[i]; gyxx.y += yxxb[i];
            yzza[i] = yzzka[i]*cosP - yzzkb[i]*sinP;
            yzzb[i] = yzzka[i]*sinP + yzzkb[i]*cosP;
            gyzz.x += yzza[i]; gyzz.y += yzzb[i];
            zxxa[i] = zxxka[i]*cosP - zxxkb[i]*sinP;
            zxxb[i] = zxxka[i]*sinP + zxxkb[i]*cosP;
            gzxx.x += zxxa[i]; gzxx.y += zxxb[i];
            zyya[i] = zyyka[i]*cosP - zyykb[i]*sinP;
            zyyb[i] = zyyka[i]*sinP + zyykb[i]*cosP;
            gzyy.x += zyya[i]; gzyy.y += zyyb[i];

        }
    }
    
    private Vector2f[] firstFields() { return new Vector2f[]{gx,gy,gz}; }
    private Vector2f[][] secondFields() {
        return new Vector2f[][]{{gxx,gxy,gxz},{gxy,gyy,gyz},{gxz,gyz,gzz}};
    }
    private Vector2f[][] thirdFields() {
        // [differentiation axis][velocity/pressure-gradient component]
        return new Vector2f[][]{{gxxx,gyxx,gzxx},{gxyy,gyyy,gzyy},{gxzz,gyzz,gzzz}};
    }
    private double evalLaplacianAxis(int axis) {
        Vector2f[] first=firstFields(); Vector2f[][] second=secondFields(),third=thirdFields();
        double v=K1*(first[axis].dot(first[axis])+pre.dot(second[axis][axis]));
        for(int j=0;j<3;j++) v-=K2*(second[axis][j].dot(second[axis][j])+first[j].dot(third[axis][j]));
        return v;
    }
    public double evalGorkovLaplacian() { return evalLaplacianAxis(0)+evalLaplacianAxis(1)+evalLaplacianAxis(2); }
    public double evalGorkovLaplacianX() { return evalLaplacianAxis(0); }
    public double evalGorkovLaplacianY() { return evalLaplacianAxis(1); }
    public double evalGorkovLaplacianZ() { return evalLaplacianAxis(2); }

    private static double phaseDot(Vector2f u, Vector2f v,
            double[] ua,double[] ub,double[] va,double[] vb,int i) {
        return -ub[i]*v.x+ua[i]*v.y-u.x*vb[i]+u.y*va[i];
    }
    private void gradientLaplacianAxis(int axis,double[] g) {
        Vector2f[] first=firstFields(); Vector2f[][] second=secondFields(),third=thirdFields();
        double[][] fA={xa,ya,za},fB={xb,yb,zb};
        double[][][] sA={{xxa,xya,xza},{xya,yya,yza},{xza,yza,zza}};
        double[][][] sB={{xxb,xyb,xzb},{xyb,yyb,yzb},{xzb,yzb,zzb}};
        double[][][] tA={{xxxa,yxxa,zxxa},{xyya,yyya,zyya},{xzza,yzza,zzza}};
        double[][][] tB={{xxxb,yxxb,zxxb},{xyyb,yyyb,zyyb},{xzzb,yzzb,zzzb}};
        for(int i=0;i<nTrans;i++) {
            g[i]=K1*(phaseDot(first[axis],first[axis],fA[axis],fB[axis],fA[axis],fB[axis],i)
                +phaseDot(pre,second[axis][axis],a,b,sA[axis][axis],sB[axis][axis],i));
            for(int j=0;j<3;j++) g[i]-=K2*(
                phaseDot(second[axis][j],second[axis][j],sA[axis][j],sB[axis][j],sA[axis][j],sB[axis][j],i)
                +phaseDot(first[j],third[axis][j],fA[j],fB[j],tA[axis][j],tB[axis][j],i));
        }
    }
    public void gradientGorkovLaplacianX(double[] g) { gradientLaplacianAxis(0,g); }
    public void gradientGorkovLaplacianY(double[] g) { gradientLaplacianAxis(1,g); }
    public void gradientGorkovLaplacianZ(double[] g) { gradientLaplacianAxis(2,g); }
    public void gradientGorkovLaplacian(double[] g) {
        gradientLaplacianAxis(0,g);
        double[] tmp=new double[nTrans];
        for(int axis=1;axis<3;axis++) {
            gradientLaplacianAxis(axis,tmp);
            for(int i=0;i<nTrans;i++) g[i]+=tmp[i];
        }
    }

    public void reset(){
        pre.reset();
        gx.reset(); gy.reset(); gz.reset();
        gxy.reset(); gxz.reset(); gyz.reset();
        gxx.reset(); gyy.reset(); gzz.reset();
        gxxx.reset();gyyy.reset();gzzz.reset();
        gxyy.reset();gxzz.reset();
        gyxx.reset();gyzz.reset();
        gzxx.reset();gzyy.reset();
        swap.reset(); tmp.reset();
        diffVec.reset(); diffVecS.reset();
        tPos.reset();
        nor.reset();
    }
    
    public void initFieldConstantsOnlyForAmp(Renderer r) {
        fillPropagationConstants(r.getForm(), false);
    }

    public void initFieldConstants(final MainForm mf) {
        reset();
        final Vector2f constants = new Vector2f();
        CalcField.calcGorkovConstants(mf.scene.getParticleRadious(), mf, constants);
        M1 = constants.x; M2 = constants.y;
        K1 = 2*M1; K2 = 2*M2;
        fillPropagationConstants(mf, true);
    }

    private void fillPropagationConstants(MainForm mf, boolean derivatives) {
        double[][] re = {ka,xka,yka,zka,xyka,xzka,yzka,xxka,yyka,zzka,
            xxxka,yyyka,zzzka,xyyka,xzzka,yxxka,yzzka,zxxka,zyyka};
        double[][] im = {kb,xkb,ykb,zkb,xykb,xzkb,yzkb,xxkb,yykb,zzkb,
            xxxkb,yyykb,zzzkb,xyykb,xzzkb,yxxkb,yzzkb,zxxkb,zyykb};
        final acousticfield3d.simulation.Simulation s = mf.simulation;
        if (s.transducers.size() != nTrans) throw new IllegalStateException("Array changed; restart optimization");
        final boolean discretize = mf.miscPanel.isAmpDiscretizer();
        final float step = 1.0f / mf.miscPanel.getAmpDiscretization();
        final double dh = Float.isFinite(h) && h > 0 ? h : s.getWavelenght()/64.0;
        for (int i=0; i<nTrans; i++) {
            acousticfield3d.simulation.Transducer t = s.transducers.get(i);
            double amp = t.calcRealDiscAmplitude(discretize, step);
            if (amp == 0) {
                for (int d=0; d<re.length; d++) { re[d][i]=0; im[d][i]=0; }
                continue;
            }
            AcousticPropagation.Source source = new AcousticPropagation.Source(t, s.getMediumSpeed());
            if (derivatives) {
                double[][] v = PropagationDerivatives.evaluate(source, s.getReflector(),
                        position.x, position.y, position.z, dh, useDirectivity);
                for (int d=0; d<re.length; d++) { re[d][i]=amp*v[d][0]; im[d][i]=amp*v[d][1]; }
            } else {
                double[] v = new double[2];
                AcousticPropagation.transfer(source, s.getReflector(), position.x, position.y,
                        position.z, useDirectivity, v);
                ka[i]=amp*v[0]; kb[i]=amp*v[1];
            }
        }
    }
}
