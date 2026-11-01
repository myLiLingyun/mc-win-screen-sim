package com.endpoem.gl;

import com.endpoem.Assets;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL31.GL_INVALID_INDEX;
import static org.lwjgl.opengl.GL31.glGetUniformBlockIndex;
import static org.lwjgl.opengl.GL31.glUniformBlockBinding;

/**
 * Compiles Minecraft's own GLSL programs. Minecraft's {@code #moj_import} directive is
 * expanded from the copies of the 26.2 shader include files bundled in assets/shaders.
 */
public final class Shader {
    public final int program;

    public Shader(Assets assets, String vertexFile, String fragmentFile, String... defines) {
        String vertex = expand(assets, vertexFile, defines);
        String fragment = expand(assets, fragmentFile, defines);
        int vs = compile(GL_VERTEX_SHADER, vertexFile, vertex);
        int fs = compile(GL_FRAGMENT_SHADER, fragmentFile, fragment);
        program = glCreateProgram();
        glAttachShader(program, vs);
        glAttachShader(program, fs);
        glBindAttribLocation(program, 0, "Position");
        glBindAttribLocation(program, 1, "UV0");
        glBindAttribLocation(program, 2, "Color");
        glLinkProgram(program);
        glDeleteShader(vs);
        glDeleteShader(fs);
        if (glGetProgrami(program, GL_LINK_STATUS) == 0) {
            String log = glGetProgramInfoLog(program);
            throw new IllegalStateException("failed to link " + vertexFile + "/" + fragmentFile + ":\n" + log);
        }
        bindBlock("Projection", Gfx.UBO_PROJECTION);
        bindBlock("DynamicTransforms", Gfx.UBO_TRANSFORMS);
        bindBlock("Globals", Gfx.UBO_GLOBALS);
        bindBlock("Fog", Gfx.UBO_FOG);
        use();
        setSampler("Sampler0", 0);
        setSampler("Sampler1", 1);
    }

    private void bindBlock(String name, int binding) {
        int index = glGetUniformBlockIndex(program, name);
        if (index != GL_INVALID_INDEX) {
            glUniformBlockBinding(program, index, binding);
        }
    }

    public void use() {
        glUseProgram(program);
    }

    public void setSampler(String name, int unit) {
        int loc = glGetUniformLocation(program, name);
        if (loc >= 0) {
            glUniform1i(loc, unit);
        }
    }

    public void delete() {
        glDeleteProgram(program);
    }

    private static int compile(int type, String label, String source) {
        int sh = glCreateShader(type);
        glShaderSource(sh, source);
        glCompileShader(sh);
        if (glGetShaderi(sh, GL_COMPILE_STATUS) == 0) {
            String log = glGetShaderInfoLog(sh);
            throw new IllegalStateException("failed to compile " + label + ":\n" + log + "\n--- source ---\n" + source);
        }
        return sh;
    }

    private static String expand(Assets assets, String coreFile, String[] defines) {
        Path main = assets.shaderCore(coreFile);
        try {
            String raw = Files.readString(main);
            String version = "#version 330";
            List<String> lines = List.of(raw.split("\\R", -1));
            for (String line : lines) {
                if (line.strip().startsWith("#version")) {
                    version = line.strip();
                    break;
                }
            }
            StringBuilder out = new StringBuilder();
            out.append(version).append('\n');
            for (String d : defines) {
                out.append(d).append('\n');
            }
            Set<String> seen = new HashSet<>();
            boolean versionSkipped = false;
            for (String line : lines) {
                String t = line.strip();
                if (t.startsWith("#version") && !versionSkipped) {
                    versionSkipped = true;
                    continue;
                }
                if (t.startsWith("#moj_import")) {
                    String inc = betweenAngles(t);
                    if (inc != null && seen.add(inc)) {
                        out.append(expandInclude(assets, inc, seen));
                    }
                    continue;
                }
                out.append(line).append('\n');
            }
            return out.toString();
        } catch (IOException e) {
            throw new IllegalStateException("cannot read shader " + main, e);
        }
    }

    private static String expandInclude(Assets assets, String id, Set<String> seen) throws IOException {
        String name = id;
        int colon = name.indexOf(':');
        if (colon >= 0) {
            name = name.substring(colon + 1);
        }
        Path path = assets.shaderInclude(name);
        StringBuilder out = new StringBuilder();
        for (String line : Files.readAllLines(path)) {
            String t = line.strip();
            if (t.startsWith("#version")) {
                continue;
            }
            if (t.startsWith("#moj_import")) {
                String inc = betweenAngles(t);
                if (inc != null && seen.add(inc)) {
                    out.append(expandInclude(assets, inc, seen));
                }
                continue;
            }
            out.append(line).append('\n');
        }
        return out.toString();
    }

    private static String betweenAngles(String line) {
        int a = line.indexOf('<');
        int b = line.indexOf('>', a + 1);
        if (a < 0 || b < 0) {
            return null;
        }
        return line.substring(a + 1, b);
    }
}
