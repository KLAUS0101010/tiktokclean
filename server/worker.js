// TikTok Clean server: keeps the list of notes (♪) and the update link. Runs free on Cloudflare Workers.
// Needs: a KV namespace bound as BADGES, and a secret called ADMIN_PASSWORD (see INSTRUCTION-RU.md, part 3).

const JSON_HEADERS = { "content-type": "application/json; charset=utf-8", "cache-control": "no-store" };
const MAX_FAILS = 8;          // wrong passwords allowed per IP ...
const FAIL_WINDOW = 900;      // ... in this many seconds

const reply = (obj, status = 200) => new Response(JSON.stringify(obj), { status, headers: JSON_HEADERS });

async function load(env) {
  const raw = await env.BADGES.get("state");
  if (!raw) return { badges: {}, update: null };
  try { const s = JSON.parse(raw); return { badges: s.badges || {}, update: s.update || null }; }
  catch { return { badges: {}, update: null }; }
}

const save = (env, state) => env.BADGES.put("state", JSON.stringify(state));

// compare two strings in the same time no matter where they differ
function same(a, b) {
  const x = new TextEncoder().encode(String(a)), y = new TextEncoder().encode(String(b));
  let r = x.length ^ y.length;
  for (let i = 0; i < Math.max(x.length, y.length); i++) r |= (x[i] || 0) ^ (y[i] || 0);
  return r === 0;
}

export default {
  async fetch(request, env) {
    const url = new URL(request.url);

    if (request.method === "GET" && url.pathname === "/badges") {
      return reply(await load(env));
    }

    if (request.method === "POST" && url.pathname === "/admin") {
      if (!env.ADMIN_PASSWORD) return reply({ error: "На сервере не задан ADMIN_PASSWORD" }, 500);
      let body;
      try { body = await request.json(); } catch { return reply({ error: "Плохой запрос" }, 400); }

      const ip = request.headers.get("cf-connecting-ip") || "unknown";
      const failKey = "fail:" + ip;
      const fails = parseInt((await env.BADGES.get(failKey)) || "0", 10);
      if (fails >= MAX_FAILS) return reply({ error: "Слишком много неверных паролей. Подожди 15 минут." }, 429);
      if (!same(body.password ?? "", env.ADMIN_PASSWORD)) {
        await env.BADGES.put(failKey, String(fails + 1), { expirationTtl: FAIL_WINDOW });
        return reply({ error: "Неверный пароль" }, 403);
      }

      const state = await load(env);
      switch (body.action) {
        case "check":
          break;
        case "grant": {
          const user = String(body.user || "").replace(/^@/, "").toLowerCase();
          const color = String(body.color || "");
          const reason = String(body.reason || "").slice(0, 200);
          if (!/^[a-z0-9._]{1,24}$/.test(user)) return reply({ error: "Плохой ник" }, 400);
          if (!/^#[0-9a-fA-F]{6}$/.test(color)) return reply({ error: "Плохой цвет" }, 400);
          if (Object.keys(state.badges).length >= 2000 && !state.badges[user]) return reply({ error: "Слишком много нот" }, 400);
          state.badges[user] = { c: color.toUpperCase(), r: reason, t: Math.floor(Date.now() / 1000) };
          break;
        }
        case "revoke": {
          const user = String(body.user || "").replace(/^@/, "").toLowerCase();
          delete state.badges[user];
          break;
        }
        case "set_update": {
          const build = Number(body.build);
          const link = String(body.url || "");
          if (!Number.isInteger(build) || build < 1 || build > 999999) return reply({ error: "Плохой номер сборки" }, 400);
          if (!/^https:\/\/[^\s]{4,500}$/.test(link)) return reply({ error: "Ссылка должна начинаться с https://" }, 400);
          state.update = { build, url: link, notes: String(body.notes || "").slice(0, 500) };
          break;
        }
        case "clear_update":
          state.update = null;
          break;
        default:
          return reply({ error: "Неизвестное действие" }, 400);
      }
      if (body.action !== "check") await save(env, state);
      return reply(state);
    }

    return reply({ error: "Не найдено" }, 404);
  },
};
