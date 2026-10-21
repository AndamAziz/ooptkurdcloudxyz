// Public certificate check opened by the QR code on EnglishMaster certificates:
//   GET /functions/v1/verify-certificate?code=EM-XXXX-XXXX-XXXX
// Calls the verify_certificate() RPC with the public (anon) key and prints the result as plain
// text (Supabase serves function responses on its own domain as text, not HTML).
// Deployed with verify_jwt = false: scanning a QR code sends no credentials.

const CODE = /^EM-[A-Z2-9]{4}-[A-Z2-9]{4}-[A-Z2-9]{4}$/;
// Public publishable key of this project (also shipped in the app); used if the runtime does not provide one.
const FALLBACK_PUBLIC_KEY = "sb_publishable_RQzspUgpREKNiZOSsPb7vQ_juogKh-t";

function text(body: string, status = 200): Response {
  return new Response(body, {
    status,
    headers: {
      "Content-Type": "text/plain; charset=utf-8",
      "Cache-Control": "no-store",
      "X-Content-Type-Options": "nosniff",
    },
  });
}

Deno.serve(async (req) => {
  if (req.method !== "GET") return text("Method not allowed", 405);
  const raw = new URL(req.url).searchParams.get("code") ?? "";
  const code = raw.trim().toUpperCase().slice(0, 32);
  if (!CODE.test(code)) {
    return text("EnglishMaster — certificate check\n\nThe code is missing or not in the form EM-XXXX-XXXX-XXXX.\nکۆدەکە نییە یان شێوەکەی هەڵەیە.", 400);
  }

  const url = Deno.env.get("SUPABASE_URL")!;
  const key = Deno.env.get("SUPABASE_ANON_KEY") || FALLBACK_PUBLIC_KEY;
  const headers: Record<string, string> = { apikey: key, "Content-Type": "application/json" };
  if (!key.startsWith("sb_")) headers.Authorization = `Bearer ${key}`;

  const res = await fetch(`${url}/rest/v1/rpc/verify_certificate`, {
    method: "POST",
    headers,
    body: JSON.stringify({ p_code: code }),
  });
  if (!res.ok) return text("EnglishMaster — certificate check\n\nThe check is temporarily unavailable. Please try again later.", 503);

  const rows = (await res.json()) as Array<{
    holder_name: string; cefr_level: string; score: number; total: number; issued_at: string; revoked: boolean;
  }>;
  const c = rows[0];
  if (!c) {
    return text(`EnglishMaster — certificate check\n\n✗ NOT FOUND: no certificate has the code ${code}.\n✗ هیچ بڕوانامەیەک بەم کۆدە نییە.`, 404);
  }
  const date = c.issued_at.slice(0, 10);
  const status = c.revoked
    ? "✗ REVOKED — this certificate is no longer valid.\n✗ ئەم بڕوانامەیە هەڵوەشێنراوەتەوە."
    : "✓ VALID — this certificate was issued by EnglishMaster.\n✓ ئەم بڕوانامەیە ڕاستەقینەیە.";
  return text(
    [
      "EnglishMaster — certificate check",
      "",
      status,
      "",
      `Name / ناو:            ${c.holder_name}`,
      `Level / ئاست:          ${c.cefr_level}`,
      `Exam score / نمرە:     ${c.score} / ${c.total}`,
      `Issued / بەروار:       ${date}`,
      `Certificate ID:        ${code}`,
      "",
      "An EnglishMaster level achievement certificate (not an official CEFR certification).",
    ].join("\n"),
  );
});
