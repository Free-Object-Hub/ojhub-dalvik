package xyz.objecthub.android;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/** javac без Android SDK. Не входит в apk. */
public class VirtualLinkTest {
    static int failed;

    public static void main(String[] args) {
        firstSetDoesNotPush();
        secondSetPushes();
        backThenArmThenCutFuture();
        addRemoveCommand();
        nestedAndDynamic();
        unknownPageHitsBase();
        unknownCommandDoesNotThrow();
        restoreRoundTrip();
        if (failed != 0) {
            System.err.println(failed + " failed");
            System.exit(1);
        }
        System.out.println("ok");
    }

    static void firstSetDoesNotPush() {
        VirtualLink link = new VirtualLink();
        link._init("?");
        link.set("find");
        eq(link.history(), Arrays.asList("?"), "first set keeps stack");
        eq(link._i, false, "first set arms");
    }

    static void secondSetPushes() {
        VirtualLink link = armed("?");
        link.set("find");
        link.set("news");
        eq(link.history(), Arrays.asList("?", "?find", "?news"), "two pushes");
        eq(link.position(), 2, "pos at news");
        eq(link.back(), true, "back from news");
        eq(link.search(), "?find", "back lands on find");
        eq(link.forward(), true, "forward");
        eq(link.search(), "?news", "forward lands on news");
    }

    static void backThenArmThenCutFuture() {
        VirtualLink link = armed("?");
        link.set("find");
        link.set("news");
        link.back();
        // страница при mount зовёт set('find') и только снимает _i
        link.set("find");
        eq(link.history(), Arrays.asList("?", "?find", "?news"), "mount set does not cut");
        link.set("wiki");
        eq(link.history(), Arrays.asList("?", "?find", "?wiki"), "set cuts future");
        eq(link.forward(), false, "no future");
    }

    static void addRemoveCommand() {
        VirtualLink link = armed("?");
        link.set("find");
        link.add("dev");
        eq(link.search(), "?find&dev", "add command");
        link.add("dev");
        eq(link.search(), "?find&dev", "add once");
        link.remove("dev");
        eq(link.search(), "?find", "remove command");
    }

    static void nestedAndDynamic() {
        final String[] got = new String[1];
        final Object[] dyn = new Object[1];
        VirtualLink link = new VirtualLink();
        Map<String, Object> news = VirtualLink.nest();
        news.put("comms", new VirtualLink.Call() {
            @Override public void call(Object value) { got[0] = (String) value; }
        });
        link.actions.put("news/", news);
        Map<String, Object> vac = VirtualLink.nest();
        vac.put(":p", new VirtualLink.Call() {
            @Override public void call(Object value) { dyn[0] = value; }
        });
        link.actions.put("VacsC/", vac);
        link._init("?news/comms=3|9|back");
        link.get();
        eq(got[0], "3|9|back", "nested value");

        link.restore(Arrays.asList("?VacsC/5"), 0);
        link.get();
        if (!(dyn[0] instanceof String[])) fail("dynamic type");
        else eq(((String[]) dyn[0])[0], "5", "dynamic segment");
    }

    static void unknownPageHitsBase() {
        final int[] n = new int[1];
        VirtualLink link = new VirtualLink();
        link.basePage = new Runnable() {
            @Override public void run() { n[0]++; }
        };
        link._init("?nope");
        try {
            link.get();
            fail("unknown page must throw");
        } catch (RuntimeException expected) {
            eq(n[0], 1, "basePage once");
        }
    }

    static void unknownCommandDoesNotThrow() {
        VirtualLink link = new VirtualLink();
        link.actions.put("", new VirtualLink.Call() {
            @Override public void call(Object value) { }
        });
        link._init("?&missing");
        link.get();
    }

    static void restoreRoundTrip() {
        VirtualLink link = armed("?");
        link.set("find");
        link.set("news");
        link.back();
        List<String> h = link.history();
        int pos = link.position();
        VirtualLink copy = new VirtualLink();
        copy.restore(h, pos);
        eq(copy.search(), "?find", "restored search");
        eq(copy._i, true, "restored disarmed");
        copy.set("find");
        eq(copy.history().size(), 3, "restored mount does not push");
    }

    static VirtualLink armed(String addr) {
        VirtualLink link = new VirtualLink();
        link._init(addr);
        link._i = false;
        return link;
    }

    static void eq(Object actual, Object expected, String name) {
        if (actual == null ? expected != null : !actual.equals(expected)) {
            fail(name + " expected " + expected + " got " + actual);
        }
    }

    static void eq(boolean actual, boolean expected, String name) {
        if (actual != expected) fail(name + " expected " + expected + " got " + actual);
    }

    static void eq(int actual, int expected, String name) {
        if (actual != expected) fail(name + " expected " + expected + " got " + actual);
    }

    static void fail(String name) {
        failed++;
        System.err.println("FAIL " + name);
    }
}
