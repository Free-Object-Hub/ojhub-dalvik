package xyz.objecthub.android;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Роутер одного джейла. Порт createVirtualLink из nhConfig.js 0.99_BETA1
 * и API _.link из newHelper.js (compile / search / set / add / remove / get).
 *
 * История своя, location и History API не трогаем.
 * _i == true глушит push в set(): так страница, которую только что
 * открыли через get(), не пишет себя в историю второй раз.
 * В вебе первое set() после захода именно поэтому ничего не пушит.
 */
public final class VirtualLink {

    /** Лист маршрута. value — String, либо String[] на динамическом пути. */
    public interface Call {
        void call(Object value);
    }

    public interface Cmd {
        void call(String value);
    }

    public interface Render {
        void onRender(VirtualLink link);
    }

    /**
     * Лог без Android: этот файл гоняется javac без SDK (tools/VirtualLinkTest).
     * App.onCreate подставляет android.util.Log.
     */
    public interface Log {
        void w(String msg, Throwable t);
    }

    public static Log log = new Log() {
        @Override
        public void w(String msg, Throwable t) {
            System.err.println(msg + (t == null ? "" : " " + t));
        }
    };

    public String defTitle = "";
    /** Последний title, который страница отдала в set(). Не document.title. */
    public String title = "";

    /**
     * Как actions в GHE: значение — либо Call, либо Map вложенных ключей.
     * Вложенный объект в JS пишется ключом с хвостом "/": "news/".
     * Динамический сегмент — ключ с ":".
     */
    public final Map<String, Object> actions = new LinkedHashMap<String, Object>();
    public final Map<String, Cmd> commands = new LinkedHashMap<String, Cmd>();
    public Runnable basePage = new Runnable() {
        @Override public void run() { }
    };

    /** Блокировка push. Публичная, как J.link._i в клиенте. */
    public boolean _i = true;

    public Render onRender;

    private final List<String> _cmd = new ArrayList<String>();
    private List<String> _stack = new ArrayList<String>();
    private int _pos;
    private boolean _inited;

    public VirtualLink() {
        _stack.add("?");
    }

    public static Map<String, Object> nest() {
        return new LinkedHashMap<String, Object>();
    }

    public String[] compile() {
        return compile(search());
    }

    /** Срез после '?'. Пустая строка даёт [""], как "".split('&') в JS. */
    public String[] compile(String e) {
        if (e == null) e = "";
        if (e.startsWith("?")) e = e.substring(1);
        if (e.length() == 0) return new String[] { "" };
        return e.split("&", -1);
    }

    public String search() {
        if (_stack.isEmpty() || _pos < 0 || _pos >= _stack.size()) return "?";
        return _stack.get(_pos);
    }

    public List<String> history() {
        return new ArrayList<String>(_stack);
    }

    public int position() {
        return _pos;
    }

    /**
     * В GHE get() сам зовёт _init() без адреса и затирает stack[0] в undefined,
     * если явный _init(addr) ещё не был. Здесь повторный _init — no-op,
     * адрес меняет только явный _init(addr) или restore().
     */
    public void _init(String addr) {
        if (_inited) return;
        _inited = true;
        if (_stack.isEmpty()) _stack.add("?");
        if (addr != null) {
            if (!addr.startsWith("?")) addr = "?" + addr;
            _stack.set(0, addr);
        }
        _render();
    }

    /** Поднять историю из Prefs. get() после этого не пушит текущую страницу. */
    public void restore(List<String> stack, int pos) {
        List<String> next = new ArrayList<String>();
        if (stack != null) {
            for (int i = 0; i < stack.size(); i++) {
                String s = stack.get(i);
                if (s == null || s.length() == 0) s = "?";
                if (!s.startsWith("?")) s = "?" + s;
                next.add(s);
            }
        }
        if (next.isEmpty()) next.add("?");
        if (pos < 0 || pos >= next.size()) pos = next.size() - 1;
        _stack = next;
        _pos = pos;
        _inited = true;
        _i = true;
        _cmd.clear();
        _render();
    }

    public void set(String page) {
        set(page, defTitle);
    }

    public void set(String page, String title) {
        if (title != null) this.title = title;
        if (!_i) {
            String[] link = compile();
            link[0] = page == null ? "" : page;
            List<String> cut = new ArrayList<String>(_stack.subList(0, _pos + 1));
            cut.add(join(link));
            _stack = cut;
            _pos++;
            _render();
        }
        _i = false;
    }

    public void add(String cmd) {
        if (cmd == null) return;
        String[] link = compile();
        if (indexOf(link, cmd) >= 0) return;
        String[] next = new String[link.length + 1];
        System.arraycopy(link, 0, next, 0, link.length);
        next[link.length] = cmd;
        _cmd.add(cmd);
        _stack.set(_pos, join(next));
        _render();
    }

    public void remove(String cmd) {
        String[] link = compile();
        int idx = indexOf(link, cmd);
        if (idx < 0) return;
        String[] next = new String[link.length - 1];
        int p = 0;
        for (int i = 0; i < link.length; i++) {
            if (i != idx) next[p++] = link[i];
        }
        int ci = _cmd.indexOf(cmd);
        if (ci >= 0) _cmd.remove(ci);
        _stack.set(_pos, join(next));
        _render();
    }

    /** Системная Назад. false — истории нет, Activity решает сама. */
    public boolean back() {
        if (_pos <= 0) return false;
        _goto(_pos - 1);
        return true;
    }

    public boolean forward() {
        if (_pos >= _stack.size() - 1) return false;
        _goto(_pos + 1);
        return true;
    }

    private void _goto(int pos) {
        _pos = pos;
        _i = true;
        _render();
        try {
            get();
        } catch (RuntimeException e) {
            // get() уже вызвал basePage и пробросил, как кривой popstate.
            // Кнопку Назад из-за битого маршрута не роняем, но и не молчим.
            log.w("goto " + search() + " failed", e);
        }
    }

    public void get() {
        _init(null);
        String[] links = compile();
        String first = links.length > 0 ? links[0] : "";
        int eq = first.indexOf('=');
        final String firstKey;
        Object firstValue;
        if (eq < 0) {
            firstKey = first;
            firstValue = null;
        } else {
            firstKey = first.substring(0, eq);
            firstValue = first.substring(eq + 1);
        }
        String[] cmdParts = new String[Math.max(0, links.length - 1)];
        for (int i = 1; i < links.length; i++) cmdParts[i - 1] = links[i];

        try {
            if (firstKey.indexOf('/') < 0) {
                Object main = actions.get(firstKey);
                if (!(main instanceof Call)) {
                    throw new IllegalStateException("no action '" + firstKey + "'");
                }
                ((Call) main).call(firstValue);
            } else {
                dispatchNested(firstKey, firstValue);
            }
        } catch (RuntimeException e) {
            basePage.run();
            throw e;
        }

        // Снимок команд. В вебе forEach идёт по тому же массиву, что мутирует remove().
        _cmd.clear();
        List<String> snapshot = new ArrayList<String>();
        for (int i = 0; i < cmdParts.length; i++) {
            String c = cmdParts[i];
            if (c != null && c.length() > 0) {
                _cmd.add(c);
                snapshot.add(c);
            }
        }
        for (int i = 0; i < snapshot.size(); i++) {
            String cmdPre = snapshot.get(i);
            int e2 = cmdPre.indexOf('=');
            String key = e2 < 0 ? cmdPre : cmdPre.substring(0, e2);
            String val = e2 < 0 ? null : cmdPre.substring(e2 + 1);
            Cmd cmd = commands.get(key);
            if (cmd != null) {
                cmd.call(val);
            } else {
                // В ojhub.js в текст ошибки попадает сама функция cmd, не ключ.
                log.w("command '" + key + "' doesn't exist", null);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void dispatchNested(String firstKey, Object firstValue) {
        String[] route = firstKey.split("/", -1);
        Object dir = actions;
        List<String> dynamic = new ArrayList<String>();
        for (int i = 0; i < route.length; i++) {
            String point = route[i];
            if (!(dir instanceof Map)) {
                throw new IllegalStateException("bad nest at '" + point + "'");
            }
            Map<String, Object> map = (Map<String, Object>) dir;
            String dynKey = findDyn(map);
            if (dynKey != null) {
                dynamic.add(point);
                point = dynKey;
                if (point.endsWith("/")) point = point.substring(0, point.length() - 1);
            }
            Object kDir = map.get(point + "/");
            if (kDir != null) {
                dir = kDir;
            } else {
                if (!dynamic.isEmpty()) {
                    firstValue = dynamic.toArray(new String[dynamic.size()]);
                }
                Object fn = map.get(point);
                if (!(fn instanceof Call)) {
                    throw new IllegalStateException("no action '" + point + "'");
                }
                ((Call) fn).call(firstValue);
                return;
            }
        }
    }

    private static String findDyn(Map<String, Object> map) {
        for (String key : map.keySet()) {
            if (key.startsWith(":")) return key;
        }
        return null;
    }

    /** Как в вебе: снять слушателей. Кнопок у нас нет, глушим таблицы. */
    public void _destroy() {
        actions.clear();
        commands.clear();
        basePage = new Runnable() {
            @Override public void run() { }
        };
        _stack.clear();
        _cmd.clear();
        onRender = null;
        _inited = false;
    }

    public void _render() {
        if (onRender != null) onRender.onRender(this);
    }

    private static String join(String[] link) {
        StringBuilder sb = new StringBuilder("?");
        for (int i = 0; i < link.length; i++) {
            if (i > 0) sb.append('&');
            if (link[i] != null) sb.append(link[i]);
        }
        return sb.toString();
    }

    private static int indexOf(String[] link, String cmd) {
        for (int i = 0; i < link.length; i++) {
            if (cmd.equals(link[i])) return i;
        }
        return -1;
    }
}
