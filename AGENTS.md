# ojhub-dalvik

Android-клиент Object Hub (протокол V133). Прототип. Plain Java + Android
Views. Никаких XML, Gradle, AndroidX, Kotlin, внешних библиотек.

## Философия

Минимум абстракций, минимум рантайма, максимум скорости. Код в стиле GHE:
страницы - обычные функции, разбор ответа сервера прямо внутри страницы.
Не "улучшай" архитектуру. Если сомневаешься - сделай проще и тупее.

## Сборка

- freebsdBuild.sh: десктопный сборщик для всех Unix систем, имеет freebsd в названии потому что MioBombVim использует её на десктопе
- JDK 17 (21 можно но deprecated)
- minSdk 19 (Android 4.4, Dalvik), targetSdk 34
- Весь код должен жить на API 19: никаких Lambda-API, java.time, Optional,
  String.join и прочего, чего нет на 4.4. Анонимные классы вместо лямбд.
- Перед ответом мысленно прогони: скомпилируется ли это на android-19?

## Структура

- Один пакет xyz.objecthub.android, плоская папка, без подпакетов
- Страницы: Page*.java, статические методы вида pageX(final int jId)
- JailRuntime - реестр джейлов, JailContext - один джейл (J)
- VirtualLink - роутер, чистая Java, без Android SDK
- Маршруты регистрируются в JailContext.bind(): J.link.actions.put(...)

## Правила кода

- Отступы - только табы. Если где то пробелы - пусть будут но не пиши сам.
- Контекст НИКОГДА не передаётся параметром. Он лежит в джейле: J.context
- Размеры только через Dp.dp(int). Без своей математики плотности
- Сеть: Http.req (в фоновом потоке, блокирует), Http.go / Http.cached (колбэки в UI). Потоки: Exec.NET / Exec.UI
- Глобалы: AppState (thisUser, token, sData, php). thisUser - JSONObject
- Разбор ответов inline в странице (optString/optInt). Без Proto-слоя, без моделей, без DTO, без новых глобалов
- Кэш ответов живёт в Http, не в коде страниц

## Шаблон страницы

  public static void pageX(final int jId) {
    JailContext J = JailRuntime.live(jId);
    if (J == null) return;
    J.link.set("route");
    J.helperMain.removeAllViews();
    // ... вёрстка из J.context ...
    J.helperMain.addView(root, new FrameLayout.LayoutParams(
        MATCH_PARENT, MATCH_PARENT));
  }

Плюс регистрация маршрута в bind() и, если нужно, кнопка в PageMain.

## Протокол

- Всегда HTTP 200, ошибки - числовые строки, Content-Type нет
- Маршруты: /server/133/<группа>/<имя>.php (иногда сразу <имя>.php без группы). Индексы AppState.sData не переставлять и не менять: они 1:1 с ojhub.js
- Токен: заголовок user-token. token:"false" (литерал) = нужна привязка устройства, это не гость (гость - пустой токен)
- Гость: thisUser.ID == 0 (AppState.isGuest())
- Отображаемое имя: клиент видит только username. Поля nickname нет
- Сверяйся с github.com/Free-Object-Hub/ojhub-openGo

## Не трогать без прямого запроса

- Altcha.java и Tls.java (ALTCHA-солвер, TLSv1.2 для Android 4.4)
- assets/certs (ISRG Root X1/X2)
- Порядок и значения в AppState.sData
- Подпись
- build.sh и связанная с ним инфраструктура

## Как портировать веб клиент

- Код пишется по одной странице/куску за раз, MioBombVim сам подключает и чинит
- Меняй минимум файлов. Не рефакторь соседний код "заодно"
- Новую страницу отдавай целиком, плюс отдельным блоком строки для bind()
  и PageMain
- Не знаешь сигнатуру существующего класса - спроси, не угадывай