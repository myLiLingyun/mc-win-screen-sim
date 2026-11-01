package com.endpoem.screen;

public interface Screen {
    void update(double dt);

    void render();

    default void key(int key, int action, int mods) {
    }

    default void charTyped(int codepoint) {
    }

    default void mouseButton(int button, int action, double mx, double my) {
    }

    default void mouseMoved(double mx, double my) {
    }

    default void scroll(double dy) {
    }

    default boolean showCursor() {
        return true;
    }

    default void onShow() {
    }

    default void onHide() {
    }
}
