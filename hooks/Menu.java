package dev.clean;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Map;

/** The "Меню мода": every screen is built in code, no resource files are needed. */
final class Menu {
    private static final int BG = 0xFF1B1B1F, CARD = 0xFF2A2A30, TEXT = 0xFFFFFFFF, DIM = 0xFFA0A0AA, ACCENT = 0xFFFE2C55;
    static final String NOTE_TEXT = "Чтобы получить ноту рядом с именем обратитесь к этому человеку в telegram: @cleanmoders";
    private static final int[] PALETTE = {
            0xFFFE2C55, 0xFFFF6A00, 0xFFFFB300, 0xFFFFE600, 0xFF7ED321, 0xFF00C853, 0xFF00E5FF, 0xFF2979FF,
            0xFF651FFF, 0xFFD500F9, 0xFFFF80AB, 0xFFFFFFFF};

    private final Activity act;
    private final Dialog dialog;
    private final TextView title;
    private final TextView back;
    private final LinearLayout body;
    private final float d;
    private Runnable backAction;

    private String adminPass;           // only in memory, gone when the app closes
    private int pickedColor = PALETTE[2];

    private Menu(Activity a) {
        act = a;
        d = a.getResources().getDisplayMetrics().density;
        dialog = new Dialog(a, android.R.style.Theme_DeviceDefault_Dialog_NoActionBar);
        LinearLayout root = new LinearLayout(a);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(round(BG, 18));
        LinearLayout head = new LinearLayout(a);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(dp(8), dp(10), dp(8), dp(6));
        back = text("←", 22, TEXT);
        back.setPadding(dp(12), dp(4), dp(12), dp(4));
        back.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { if (backAction != null) backAction.run(); } });
        title = text("", 18, TEXT);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        TextView close = text("✕", 20, DIM);
        close.setPadding(dp(12), dp(4), dp(12), dp(4));
        close.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { dialog.dismiss(); } });
        head.addView(back);
        head.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        head.addView(close);
        root.addView(head);
        ScrollView sv = new ScrollView(a);
        body = new LinearLayout(a);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(14), dp(4), dp(14), dp(16));
        sv.addView(body);
        root.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(root);
        Window w = dialog.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
            w.setLayout((int) (a.getResources().getDisplayMetrics().widthPixels * 0.94), (int) (a.getResources().getDisplayMetrics().heightPixels * 0.82));
            w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
    }

    // ---- entry points ---------------------------------------------------------------------

    static void open(Activity a) {
        if (a == null) return;
        try {
            Menu m = new Menu(a);
            m.dialog.show();
            m.main();
        } catch (Throwable t) {
            try { Toast.makeText(a, "Меню мода: " + t, Toast.LENGTH_LONG).show(); } catch (Throwable ignored) { }
        }
    }

    /** Opens the menu straight on the download screen for this video link. */
    static void openDownload(Activity a, String link) {
        if (a == null) return;
        try {
            Menu m = new Menu(a);
            m.dialog.show();
            m.downloadScreen(link);
        } catch (Throwable t) {
            try { Toast.makeText(a, "Меню мода: " + t, Toast.LENGTH_LONG).show(); } catch (Throwable ignored) { }
        }
    }

    /** Tap on a note next to a name: tells why it was given. */
    static void showBadge(Activity a, String user, Badges.Badge b) {
        if (a == null) return;
        try {
            Menu m = new Menu(a);
            m.dialog.show();
            m.screen("♪ @" + user, null);
            TextView n = m.text(Badges.NOTE, 64, b.color);
            n.setGravity(Gravity.CENTER);
            m.body.addView(n);
            m.body.addView(m.paragraph("Нота выдана за:", DIM));
            m.body.addView(m.paragraph(b.reason.isEmpty() ? "заслуги перед проектом" : b.reason, TEXT));
        } catch (Throwable ignored) { }
    }

    // ---- screens --------------------------------------------------------------------------

    private void screen(String t, Runnable onBack) {
        title.setText(t);
        backAction = onBack;
        back.setVisibility(onBack == null ? View.GONE : View.VISIBLE);
        body.removeAllViews();
    }

    private void main() {
        screen("Меню мода", null);
        body.addView(item("🌍  Регион подмены", Store.region().label(), new Runnable() { @Override public void run() { regions(""); } }));
        body.addView(item("🎨  Оформление", "Liquid Glass и Хэллоуин", new Runnable() { @Override public void run() { looks(); } }));
        body.addView(item("⚙  Функции и лента", "баннер, репост комментариев, фильтр по лайкам", new Runnable() { @Override public void run() { functions(); } }));
        body.addView(item("⬇  Скачать видео", "без водяного знака, любое качество, с размером", new Runnable() { @Override public void run() { downloadScreen(""); } }));
        body.addView(item("♪  Получить ноту", "значок рядом с именем", new Runnable() { @Override public void run() { getNote(); } }));
        Badges.Update u = Badges.update;
        boolean newer = u != null && u.build > Config.BUILD;
        body.addView(item("⬆  Автообновление", newer ? "доступна новая сборка " + u.build : "обновлений нет", new Runnable() { @Override public void run() { updateScreen(); } }));
        body.addView(item("🛠  Панель администратора", "только для владельца", new Runnable() { @Override public void run() { adminLogin(); } }));
        TextView foot = text("TikTok Clean · сборка " + Config.BUILD, 12, DIM);
        foot.setGravity(Gravity.CENTER);
        foot.setPadding(0, dp(16), 0, 0);
        body.addView(foot);
    }

    private Runnable toMain() { return new Runnable() { @Override public void run() { main(); } }; }

    // -- region
    private void regions(final String filter) {
        screen("Регион подмены", toMain());
        body.addView(paragraph("Страна, под которую TikTok будет думать, что у телефона сим-карта. Применяется после перезапуска TikTok.", DIM));
        final EditText search = edit("Поиск страны", filter);
        body.addView(search);
        final LinearLayout list = new LinearLayout(act);
        list.setOrientation(LinearLayout.VERTICAL);
        body.addView(list);
        fillRegions(list, filter);
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable e) { fillRegions(list, e.toString()); }
        });
    }

    private void fillRegions(LinearLayout list, String filter) {
        list.removeAllViews();
        String f = filter.trim().toLowerCase();
        String cur = Store.region().code;
        for (final Region r : Region.all()) {
            String label = r.label();
            if (!f.isEmpty() && !label.toLowerCase().contains(f) && !r.code.toLowerCase().equals(f)) continue;
            boolean sel = r.code.equals(cur);
            TextView t = text((sel ? "✓  " : "      ") + label + "   " + r.code, 16, sel ? ACCENT : TEXT);
            t.setPadding(dp(14), dp(13), dp(14), dp(13));
            t.setBackground(round(CARD, 10));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = dp(6);
            t.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    Store.setRegion(r.code);
                    Toast.makeText(act, "Регион: " + r.label() + ". Закрой TikTok полностью и открой снова.", Toast.LENGTH_LONG).show();
                    main();
                }
            });
            list.addView(t, lp);
        }
    }

    // -- looks and features
    private void looks() {
        screen("Оформление", toMain());
        body.addView(toggle("Liquid Glass", "нижняя панель (Главная, Друзья, Профиль) в виде стеклянной плашки: прозрачная, с бликом и тонкой рамкой", Look.KEY_GLASS, true, true));
        body.addView(toggle("Хэллоуин", "паутина в верхних углах, паучок и две маленькие тыквы внизу. Они прозрачные и не мешают нажимать", Look.KEY_HALLOWEEN, true, true));
        body.addView(paragraph("Liquid Glass здесь имитация: настоящее размытие фона за панелью Android для приложений не даёт. Если панель не нашлась (у неё должны быть подписи Главная, Друзья, Профиль), тема просто не применится.", DIM));
    }

    private void functions() {
        screen("Функции и лента", toMain());
        body.addView(section("Рекомендации: минимум лайков"));
        body.addView(paragraph("Видео с меньшим числом лайков не показываются, только в ленте рекомендаций (профили, поиск, подписки не трогаются). Если все видео в пачке слабые, пачка остаётся как есть.", DIM));
        final int[] values = {0, 100, 500, 1000, 5000};
        final String[] names = {"Выкл", "100", "500", "1 000", "5 000"};
        final int cur = Store.number("min_likes", 100);
        for (int i = 0; i < values.length; i++) {
            final int v = values[i];
            TextView t = text((v == cur ? "✓  " : "      ") + names[i], 16, v == cur ? ACCENT : TEXT);
            t.setPadding(dp(14), dp(12), dp(14), dp(12));
            t.setBackground(round(CARD, 10));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = dp(6);
            t.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View x) { Store.setNumber("min_likes", v); functions(); }
            });
            body.addView(t, lp);
        }
        body.addView(paragraph("Последний список: " + (AdFilter.lastNote.isEmpty() ? "ещё не было" : AdFilter.lastNote), DIM));
        body.addView(section("Новые функции TikTok"));
        body.addView(toggle("Баннер в профиле", "включить функцию, которую TikTok даёт не всем. Нужен перезапуск TikTok", "flag_banner", true, false));
        body.addView(toggle("Репост комментариев", "включить функцию, которую TikTok даёт не всем. Нужен перезапуск TikTok", "flag_repost", true, false));
        body.addView(paragraph("Эти переключатели лишь открывают кнопки в приложении. Если сервер TikTok не отдаёт аккаунту данные для функции, она может не заработать.", DIM));
    }

    /** A row with a name, an explanation and ВКЛ / ВЫКЛ. */
    private View toggle(String name, String sub, final String key, boolean def, final boolean look) {
        LinearLayout row = new LinearLayout(act);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackground(round(CARD, 12));
        row.setPadding(dp(16), dp(12), dp(16), dp(12));
        LinearLayout col = new LinearLayout(act);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(text(name, 17, TEXT));
        col.addView(text(sub, 12, DIM));
        row.addView(col, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        final TextView state = text(Store.flag(key, def) ? "ВКЛ" : "ВЫКЛ", 15, Store.flag(key, def) ? 0xFF7ED321 : DIM);
        state.setTypeface(null, android.graphics.Typeface.BOLD);
        state.setPadding(dp(10), 0, 0, 0);
        row.addView(state);
        final boolean d0 = def;
        row.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                boolean now = !Store.flag(key, d0);
                Store.setFlag(key, now);
                state.setText(now ? "ВКЛ" : "ВЫКЛ");
                state.setTextColor(now ? 0xFF7ED321 : DIM);
                if (look) Look.apply(act);
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(10);
        row.setLayoutParams(lp);
        return row;
    }

    // -- download
    private void downloadScreen(String url) {
        screen("Скачать видео", toMain());
        body.addView(paragraph("Нажми в TikTok «Поделиться → Копировать ссылку»: внизу появится кнопка «Скачать». Или вставь ссылку сюда. Водяного знака не будет, видео с запретом на скачивание тоже скачиваются.", DIM));
        final EditText link = edit("Ссылка на видео", url == null ? "" : url);
        body.addView(link);
        body.addView(button("Вставить из буфера", CARD, new Runnable() {
            @Override public void run() {
                try {
                    android.content.ClipboardManager cm = (android.content.ClipboardManager) act.getSystemService("clipboard");
                    android.content.ClipData c = cm.getPrimaryClip();
                    CharSequence s = c == null || c.getItemCount() == 0 ? null : c.getItemAt(0).getText();
                    String l = VideoInfo.findLink(s == null ? null : s.toString());
                    if (l != null) link.setText(l); else Toast.makeText(act, "В буфере нет ссылки на TikTok", Toast.LENGTH_SHORT).show();
                } catch (Throwable t) { Toast.makeText(act, "Не удалось прочитать буфер", Toast.LENGTH_SHORT).show(); }
            }
        }));
        body.addView(button("Найти видео", ACCENT, new Runnable() {
            @Override public void run() {
                String l = VideoInfo.findLink(link.getText().toString());
                if (l == null) { Toast.makeText(act, "Нужна ссылка на видео TikTok", Toast.LENGTH_SHORT).show(); return; }
                loadVideo(l);
            }
        }));
        if (url != null && !url.isEmpty()) loadVideo(url);
    }

    private void loadVideo(String link) {
        screen("Скачать видео", new Runnable() { @Override public void run() { downloadScreen(""); } });
        body.addView(paragraph("Загружаю информацию о видео…", DIM));
        Download.fetch(link, new Download.Info() {
            @Override public void done(VideoInfo info) { showQualities(info); }
        });
    }

    private String qualityText(VideoInfo.Quality q) {
        return q.label + "\n" + VideoInfo.sizeText(q.bytes, q.exact);
    }

    private void showQualities(final VideoInfo info) {
        screen("Скачать видео", new Runnable() { @Override public void run() { downloadScreen(""); } });
        if (info.error != null) {
            body.addView(paragraph(info.error, 0xFFFF6B6B));
            body.addView(paragraph("Что можно сделать: проверь ссылку, попробуй позже или включи VPN, если TikTok не открывает страницу.", DIM));
            return;
        }
        if (!info.images.isEmpty()) {
            body.addView(paragraph("Это фото-пост: " + info.images.size() + " фото. Сохраняются только картинки, без звука.", TEXT));
            final TextView st = paragraph("", DIM);
            body.addView(button("Сохранить все фото (" + info.images.size() + ")", ACCENT, new Runnable() {
                @Override public void run() {
                    st.setText("Начинаю…");
                    Download.saveImages(act, info.images, info.id, new Download.Progress() {
                        @Override public void text(String s) { st.setText(s); }
                        @Override public void finished(boolean ok, String message) { st.setText(message); }
                    });
                }
            }));
            body.addView(st);
            return;
        }
        body.addView(paragraph("Выбери качество. Размер со знаком «~» примерный, без него точный (точный уточняется в течение секунды).", DIM));
        final TextView status = paragraph("", DIM);
        for (final VideoInfo.Quality q : info.qualities) {
            final TextView row = text(qualityText(q), 16, TEXT);
            row.setPadding(dp(14), dp(13), dp(14), dp(13));
            row.setBackground(round(CARD, 10));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = dp(8);
            row.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    status.setText("Начинаю скачивание…");
                    Download.save(act, q, info.id, new Download.Progress() {
                        @Override public void text(String s) { status.setText(s); }
                        @Override public void finished(boolean ok, String message) { status.setText(message); }
                    });
                }
            });
            body.addView(row, lp);
            if (!q.exact) {
                Download.exactSize(q.url, new Download.Size() {
                    @Override public void done(long bytes) {
                        if (bytes > 0) { q.bytes = bytes; q.exact = true; row.setText(qualityText(q)); }
                    }
                });
            }
        }
        body.addView(status);
    }

    // -- note
    private void getNote() {
        screen("Получить ноту", toMain());
        TextView n = text(Badges.NOTE, 64, 0xFFFFB300);
        n.setGravity(Gravity.CENTER);
        body.addView(n);
        body.addView(paragraph(NOTE_TEXT, TEXT));
        body.addView(button("Открыть @cleanmoders в Telegram", ACCENT, new Runnable() {
            @Override public void run() {
                try { act.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/cleanmoders")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); }
                catch (Throwable t) { Toast.makeText(act, "Не удалось открыть Telegram", Toast.LENGTH_SHORT).show(); }
            }
        }));
    }

    // -- update
    private void updateScreen() {
        screen("Автообновление", toMain());
        body.addView(paragraph("Установлена сборка: " + Config.BUILD, TEXT));
        final Badges.Update u = Badges.update;
        if (!Net.enabled()) { body.addView(paragraph("При сборке не указан адрес сервера, обновления выключены.", DIM)); return; }
        if (u == null || u.build <= Config.BUILD) {
            body.addView(paragraph("У тебя последняя версия.", DIM));
            body.addView(button("Проверить ещё раз", CARD, new Runnable() {
                @Override public void run() { Badges.refresh(new Runnable() { @Override public void run() { updateScreen(); } }); }
            }));
            return;
        }
        body.addView(paragraph("Доступна сборка " + u.build + (u.notes.isEmpty() ? "" : "\n\n" + u.notes), TEXT));
        final TextView status = paragraph("", DIM);
        body.addView(button("Скачать и установить", ACCENT, new Runnable() {
            @Override public void run() {
                status.setText("Начинаю скачивание…");
                Updater.run(act, u.url, new Updater.Progress() { @Override public void text(String s) { status.setText(s); } });
            }
        }));
        body.addView(status);
        body.addView(paragraph("Если Android спросит разрешение на установку из этого источника, разреши. Обновление ставится поверх, данные остаются.", DIM));
    }

    // -- admin
    private void adminLogin() {
        screen("Панель администратора", toMain());
        if (!Net.enabled()) { body.addView(paragraph("При сборке не указан адрес сервера.", DIM)); return; }
        body.addView(paragraph("Введи пароль администратора.", DIM));
        final EditText pass = edit("Пароль", "");
        pass.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        body.addView(pass);
        final TextView status = paragraph("", DIM);
        body.addView(button("Войти", ACCENT, new Runnable() {
            @Override public void run() {
                final String p = pass.getText().toString();
                status.setText("Проверяю…");
                admin(p, "{\"action\":\"check\"", new Net.Callback() {
                    @Override public void done(boolean ok, String b) {
                        if (ok) { adminPass = p; Badges.apply(b); adminPanel(""); }
                        else status.setText(error(b));
                    }
                });
            }
        }));
        body.addView(status);
    }

    private void admin(String pass, String partialJson, Net.Callback cb) {
        Net.post("/admin", partialJson + ",\"password\":" + Json.quote(pass) + "}", cb);
    }

    private static String error(String body) {
        try { return Json.str(Json.obj(Json.parse(body)).get("error"), "Ошибка"); } catch (Throwable t) { return "Нет связи с сервером"; }
    }

    private void adminPanel(String msg) {
        screen("Панель администратора", toMain());
        if (!msg.isEmpty()) body.addView(paragraph(msg, 0xFF7ED321));

        // give a note
        body.addView(section("Выдать ноту"));
        final EditText user = edit("Ник в тиктоке (без @)", "");
        body.addView(user);
        final TextView preview = text(Badges.NOTE + "  цвет ноты", 20, pickedColor);
        preview.setPadding(dp(4), dp(10), dp(4), dp(4));
        body.addView(preview);
        LinearLayout sw = new LinearLayout(act);
        sw.setOrientation(LinearLayout.HORIZONTAL);
        for (final int c : PALETTE) {
            View v = new View(act);
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.OVAL);
            g.setColor(c);
            g.setStroke(dp(2), 0x55FFFFFF);
            v.setBackground(g);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(26), 1);
            lp.rightMargin = dp(4);
            v.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View x) { pickedColor = c; preview.setTextColor(c); }
            });
            sw.addView(v, lp);
        }
        body.addView(sw);
        SeekBar hue = new SeekBar(act);
        hue.setMax(360);
        hue.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean user) {
                if (!user) return;
                pickedColor = Color.HSVToColor(new float[]{p, 0.85f, 1f});
                preview.setTextColor(pickedColor);
            }
            @Override public void onStartTrackingTouch(SeekBar s) { }
            @Override public void onStopTrackingTouch(SeekBar s) { }
        });
        LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hl.topMargin = dp(8);
        body.addView(hue, hl);
        body.addView(paragraph("Палитра выше, ползунок — любой другой оттенок.", DIM));
        final EditText reason = edit("За что выдана", "");
        body.addView(reason);
        body.addView(button("Выдать ноту", ACCENT, new Runnable() {
            @Override public void run() {
                String u = user.getText().toString().trim().toLowerCase();
                if (u.startsWith("@")) u = u.substring(1);
                if (!u.matches("[a-z0-9._]{1,24}")) { Toast.makeText(act, "Ник: латиница, цифры, точка, подчёркивание", Toast.LENGTH_LONG).show(); return; }
                String hex = String.format("#%06X", pickedColor & 0xFFFFFF);
                admin(adminPass, "{\"action\":\"grant\",\"user\":" + Json.quote(u) + ",\"color\":" + Json.quote(hex)
                        + ",\"reason\":" + Json.quote(reason.getText().toString().trim()), done("Нота выдана @" + u));
            }
        }));

        // given notes
        body.addView(section("Выданные ноты"));
        Map<String, Badges.Badge> all = Badges.map;
        if (all.isEmpty()) body.addView(paragraph("Пока никому не выдано.", DIM));
        for (final Map.Entry<String, Badges.Badge> e : all.entrySet()) {
            LinearLayout row = new LinearLayout(act);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setBackground(round(CARD, 10));
            row.setPadding(dp(12), dp(8), dp(8), dp(8));
            TextView t = text(Badges.NOTE + " @" + e.getKey() + (e.getValue().reason.isEmpty() ? "" : "\n" + e.getValue().reason), 14, e.getValue().color);
            row.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            TextView x = text("✕ убрать", 13, 0xFFFF6B6B);
            x.setPadding(dp(10), dp(8), dp(10), dp(8));
            x.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    admin(adminPass, "{\"action\":\"revoke\",\"user\":" + Json.quote(e.getKey()), done("Нота у @" + e.getKey() + " убрана"));
                }
            });
            row.addView(x);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = dp(6);
            body.addView(row, lp);
        }

        // update
        body.addView(section("Автообновление"));
        body.addView(paragraph("Загрузи новый .apk в GitHub (Releases), скопируй прямую ссылку и вставь сюда. Номер сборки — число из названия сборки (tiktok-clean-N).", DIM));
        Badges.Update u = Badges.update;
        final EditText build = edit("Номер сборки", u == null ? "" : String.valueOf(u.build));
        build.setInputType(InputType.TYPE_CLASS_NUMBER);
        final EditText url = edit("Прямая ссылка на .apk (https://…)", u == null ? "" : u.url);
        final EditText notes = edit("Что нового (по желанию)", u == null ? "" : u.notes);
        body.addView(build);
        body.addView(url);
        body.addView(notes);
        body.addView(button("Опубликовать обновление", ACCENT, new Runnable() {
            @Override public void run() {
                String b = build.getText().toString().trim();
                if (!b.matches("[0-9]{1,6}")) { Toast.makeText(act, "Номер сборки — число", Toast.LENGTH_LONG).show(); return; }
                admin(adminPass, "{\"action\":\"set_update\",\"build\":" + b + ",\"url\":" + Json.quote(url.getText().toString().trim())
                        + ",\"notes\":" + Json.quote(notes.getText().toString().trim()), done("Обновление опубликовано"));
            }
        }));
        body.addView(button("Убрать обновление", CARD, new Runnable() {
            @Override public void run() { admin(adminPass, "{\"action\":\"clear_update\"", done("Обновление убрано")); }
        }));
    }

    private Net.Callback done(final String okMsg) {
        return new Net.Callback() {
            @Override public void done(boolean ok, String b) {
                if (ok) { Badges.apply(b); Store.putString("server_state", b); Entry.redecorate(); adminPanel(okMsg); }
                else Toast.makeText(act, error(b), Toast.LENGTH_LONG).show();
            }
        };
    }

    // ---- small building blocks ------------------------------------------------------------

    private int dp(int v) { return (int) (v * d); }

    private GradientDrawable round(int color, int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radiusDp * d);
        return g;
    }

    private TextView text(String s, int sp, int color) {
        TextView t = new TextView(act);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        return t;
    }

    private TextView paragraph(String s, int color) {
        TextView t = text(s, 15, color);
        t.setPadding(dp(4), dp(8), dp(4), dp(8));
        return t;
    }

    private TextView section(String s) {
        TextView t = text(s, 17, ACCENT);
        t.setTypeface(null, android.graphics.Typeface.BOLD);
        t.setPadding(dp(4), dp(20), dp(4), dp(6));
        return t;
    }

    private EditText edit(String hint, String value) {
        EditText e = new EditText(act);
        e.setHint(hint);
        e.setText(value);
        e.setSingleLine(true);
        e.setTextColor(TEXT);
        e.setHintTextColor(DIM);
        e.setBackground(round(CARD, 10));
        e.setPadding(dp(12), dp(12), dp(12), dp(12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(8);
        e.setLayoutParams(lp);
        return e;
    }

    private View button(String s, int color, final Runnable r) {
        TextView t = text(s, 16, TEXT);
        t.setGravity(Gravity.CENTER);
        t.setTypeface(null, android.graphics.Typeface.BOLD);
        t.setPadding(dp(12), dp(14), dp(12), dp(14));
        t.setBackground(round(color, 12));
        t.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { r.run(); } });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(10);
        t.setLayoutParams(lp);
        return t;
    }

    private View item(String name, String sub, final Runnable r) {
        LinearLayout l = new LinearLayout(act);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setBackground(round(CARD, 12));
        l.setPadding(dp(16), dp(14), dp(16), dp(14));
        l.addView(text(name, 17, TEXT));
        l.addView(text(sub, 13, DIM));
        l.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { r.run(); } });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(10);
        l.setLayoutParams(lp);
        return l;
    }
}
