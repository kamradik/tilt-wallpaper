#version 300 es
      precision highp float;
      uniform sampler2D u_source;
      uniform vec2 u_step;
      uniform float u_level;
      in vec2 v_uv;
      out vec4 outColor;
      void main() {
        vec4 color = textureLod(u_source, v_uv, u_level) * 0.2270270270;
        color += textureLod(u_source, v_uv + u_step * 1.3846153846, u_level) * 0.3162162162;
        color += textureLod(u_source, v_uv - u_step * 1.3846153846, u_level) * 0.3162162162;
        color += textureLod(u_source, v_uv + u_step * 3.2307692308, u_level) * 0.0702702703;
        color += textureLod(u_source, v_uv - u_step * 3.2307692308, u_level) * 0.0702702703;
        outColor = color;
      }