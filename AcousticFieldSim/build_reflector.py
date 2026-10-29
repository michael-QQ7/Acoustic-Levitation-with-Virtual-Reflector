"""Optional portable build/test (Python 3 + JDK 11 or newer); NetBeans still works normally."""
from pathlib import Path
import os, subprocess, shutil, sys, zipfile
root=Path(__file__).resolve().parent
os.chdir(root)
classes=root/'build/classes';classes.mkdir(parents=True,exist_ok=True)
jars=list((root/'jars').glob('*.jar'));cp=os.pathsep.join(str(p) for p in jars)
sources=sorted((root/'src').rglob('*.java'))
argfile=root/'build/sources.args';argfile.write_text('\n'.join('"'+str(p).replace('\\','/')+'"' for p in sources),encoding='utf-8')
subprocess.run(['java','com.sun.tools.javac.Main','--release','11','-encoding','UTF-8','-cp',cp,'-d',str(classes),'@'+str(argfile)],check=True)
for p in (root/'src').rglob('*'):
 if p.is_file() and p.suffix not in ('.java','.form'):
  dst=classes/p.relative_to(root/'src');dst.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(p,dst)
dist=root/'dist';dist.mkdir(exist_ok=True)
# Fold manifest lines at <72 bytes. Class-Path entries are relative to dist/.
classpath='Class-Path: '+' '.join('../jars/'+p.name for p in jars)
lines=[]
while len(classpath)>70:
 lines.append(classpath[:70]);classpath=' '+classpath[70:]
lines.append(classpath)
manifest='Manifest-Version: 1.0\r\nMain-Class: acousticfield3d.AcousticField3D\r\n'+'\r\n'.join(lines)+'\r\n\r\n'
with zipfile.ZipFile(dist/'AcousticField3D.jar','w',zipfile.ZIP_DEFLATED) as z:
 z.writestr('META-INF/MANIFEST.MF',manifest)
 for p in sorted(classes.rglob('*')):
  if p.is_file():z.write(p,p.relative_to(classes).as_posix())
print('Built:',dist/'AcousticField3D.jar')
if '--test' in sys.argv:
 tests=root/'build/test';tests.mkdir(exist_ok=True)
 testfiles=sorted((root/'test').rglob('*.java'))
 testcp=os.pathsep.join([str(classes),cp])
 subprocess.run(['java','com.sun.tools.javac.Main','--release','11','-encoding','UTF-8','-cp',testcp,'-d',str(tests)]+[str(p) for p in testfiles],check=True)
 for name in ['ReflectorPhysicsTest','ReflectorIntegrationTest']:
  cmd=['java','-Djava.awt.headless=true','-cp',os.pathsep.join([str(tests),testcp]),'acousticfield3d.algorithms.'+name]
  if name=='ReflectorIntegrationTest':cmd.append(str(root/'verification/reflector-panel.png'))
  subprocess.run(cmd,check=True)
 for name in ['acousticfield3d.protocols.MegaFPGATest', 'acousticfield3d.gui.panels.MegaFPGAPanelTest']:
  cmd=['java','-Djava.awt.headless=true','-cp',os.pathsep.join([str(tests),testcp]),name]
  if name.endswith('MegaFPGAPanelTest'):cmd.append(str(root/'verification/megafpga-panel.png'))
  subprocess.run(cmd,check=True)
 for test in sorted((root/'test').rglob('Dpcat*Test.java')):
  name='.'.join(test.relative_to(root/'test').with_suffix('').parts)
  subprocess.run(['java','-Djava.awt.headless=true','-cp',os.pathsep.join([str(tests),testcp]),name],check=True)
