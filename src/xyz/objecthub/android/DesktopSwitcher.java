package xyz.objecthub.android;

import android.graphics.Typeface;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;

import java.util.List;

/**
 * Нижние рабочие столы. Кнопки системные (buttonBar), как нижний ряд в диалоге.
 * Джейлов мало, removeAllViews здесь нормален.
 */
public final class DesktopSwitcher {
    private DesktopSwitcher() {}

    public static void rebuild(LinearLayout bar, final int activeId) {
        bar.removeAllViews();
        List<JailContext> all = JailRuntime.list();
        for (int i = 0; i < all.size(); i++) {
            final int jId = all.get(i).id;
            Button chip = button(bar, String.valueOf(jId), jId == activeId);
            chip.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    JailRuntime.activate(jId);
                }
            });
            bar.addView(chip);
        }

        Button plus = button(bar, "+", false);
        plus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int id = JailRuntime.createJail();
                JailRuntime.activate(id);
            }
        });
        bar.addView(plus);

        if (all.size() > 1) {
            Button close = button(bar, "×", false);
            close.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    JailRuntime.destroyJail(activeId);
                }
            });
            bar.addView(close);
        }
    }

    private static Button button(LinearLayout bar, String text, boolean active) {
        Button t = new Button(bar.getContext(), null, android.R.attr.buttonBarButtonStyle);
        t.setText(text);
        t.setAllCaps(false);
        t.setMinWidth(0);
        t.setMinimumWidth(0);
        if (active) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }
}
