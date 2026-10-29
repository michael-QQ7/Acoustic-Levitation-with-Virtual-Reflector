//FOR INCLUDING
uniform int reflectorEnabled;
uniform float reflectorY;
uniform float reflectorMagnitude;
uniform float reflectorPhase;
vec2 sourceField(vec3 point, vec3 source, vec3 normal, vec4 spec) {
    vec3 delta = point - source;
    float distance = max(length(delta), 1.0e-9);
    float cosine = clamp(dot(delta, normalize(normal)) / distance, -1.0, 1.0);
    float q = 0.5 * spec.x * spec.w * sqrt(max(0.0, 1.0 - cosine*cosine));
    float directivity = 1.0;
    if (abs(q) > 1.0e-6) directivity = sin(q) / q;
    float angle = spec.x * distance + spec.z;
    return (spec.y * directivity / distance) * vec2(cos(angle), sin(angle));
}
vec2 fieldAt(vec3 point) {
    vec2 field = vec2(0.0);
    for (int i = 0; i < N_TRANS; ++i) {
        field += sourceField(point, tPos[i], tNorm[i], tSpecs[i]);
        if (reflectorEnabled != 0 && reflectorMagnitude > 0.0) {
            vec3 imagePosition = vec3(tPos[i].x, 2.0*reflectorY - tPos[i].y, tPos[i].z);
            vec3 imageNormal = vec3(tNorm[i].x, -tNorm[i].y, tNorm[i].z);
            vec4 imageSpec = tSpecs[i];
            imageSpec.y *= reflectorMagnitude;
            imageSpec.z += reflectorPhase;
            field += sourceField(point, imagePosition, imageNormal, imageSpec);
        }
    }
    return field;
}
