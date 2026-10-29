#!/bin/sh
cd "$(dirname "$0")" || exit 1
exec java -cp 'dist/AcousticField3D.jar:jars/*' acousticfield3d.AcousticField3D "$@"
