package com.rivalesfc.game.gfx;

import com.badlogic.gdx.graphics.glutils.ShaderProgram;

public final class BlurShader {

    private BlurShader() {
    }

    private static final String VERT =
            "attribute vec4 a_position;\n"
                    + "attribute vec4 a_color;\n"
                    + "attribute vec2 a_texCoord0;\n"
                    + "uniform mat4 u_projTrans;\n"
                    + "varying vec4 v_color;\n"
                    + "varying vec2 v_texCoords;\n"
                    + "void main() {\n"
                    + "  v_color = a_color;\n"
                    + "  v_color.a = v_color.a * (255.0 / 254.0);\n"
                    + "  v_texCoords = a_texCoord0;\n"
                    + "  gl_Position = u_projTrans * a_position;\n"
                    + "}\n";

    private static final String FRAG =
            "#ifdef GL_ES\n"
                    + "#define LOWP lowp\n"
                    + "precision mediump float;\n"
                    + "#else\n"
                    + "#define LOWP\n"
                    + "#endif\n"
                    + "varying LOWP vec4 v_color;\n"
                    + "varying vec2 v_texCoords;\n"
                    + "uniform sampler2D u_texture;\n"
                    + "uniform vec2 u_texel;\n"
                    + "uniform float u_radius;\n"
                    + "void main() {\n"
                    + "  vec3 rgb = vec3(0.0);\n"
                    + "  float a = 0.0;\n"
                    + "  float total = 0.0;\n"
                    + "  for (int x = -2; x <= 2; x++) {\n"
                    + "    for (int y = -2; y <= 2; y++) {\n"
                    + "      float fx = float(x);\n"
                    + "      float fy = float(y);\n"
                    + "      float w = exp(-(fx * fx + fy * fy) / 4.0);\n"
                    + "      vec4 s = texture2D(u_texture, v_texCoords + vec2(fx, fy) * u_texel * u_radius, 1.5);\n"
                    + "      rgb += s.rgb * s.a * w;\n"
                    + "      a += s.a * w;\n"
                    + "      total += w;\n"
                    + "    }\n"
                    + "  }\n"
                    + "  vec3 outRgb = a > 0.0001 ? rgb / a : vec3(0.0);\n"
                    + "  gl_FragColor = v_color * vec4(outRgb, a / total);\n"
                    + "}\n";

    public static ShaderProgram create() {
        ShaderProgram.pedantic = false;
        ShaderProgram program = new ShaderProgram(VERT, FRAG);
        if (!program.isCompiled()) {
            com.badlogic.gdx.Gdx.app.error("BlurShader", "No compiló: " + program.getLog());
            program.dispose();
            return null;
        }
        return program;
    }
}
