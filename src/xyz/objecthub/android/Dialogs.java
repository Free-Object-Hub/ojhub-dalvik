package xyz.objecthub.android;

import android.app.Dialog;
import android.content.DialogInterface;

import java.util.ArrayList;

/**
 * Слой поверх рута вместо _.win. Назад сначала закрывает его,
 * и только потом VirtualLink. Окна не навигация.
 */
public final class Dialogs {
    private static final ArrayList<Dialog> open = new ArrayList<Dialog>();

    private Dialogs() {}

    public static void show(final Dialog d) {
        open.add(d);
        d.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
                open.remove(d);
            }
        });
        d.show();
    }

    /** true — диалог был и закрыт. */
    public static boolean back() {
        for (int i = open.size() - 1; i >= 0; i--) {
            Dialog d = open.get(i);
            if (d.isShowing()) {
                d.dismiss();
                return true;
            }
        }
        open.clear();
        return false;
    }

    /** Activity умирает: не оставлять Dialog, он держит её context. */
    public static void dropAll() {
        ArrayList<Dialog> copy = new ArrayList<Dialog>(open);
        open.clear();
        for (int i = 0; i < copy.size(); i++) {
            Dialog d = copy.get(i);
            d.setOnDismissListener(null);
            if (d.isShowing()) d.dismiss();
        }
    }
}
