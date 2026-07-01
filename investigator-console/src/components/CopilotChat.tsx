import { useState } from "react";
import { askCopilot } from "../api/queries";

interface Turn {
  role: "you" | "copilot";
  text: string;
}

/**
 * The AI copilot chat (Phase 5E), driven through the BFF's `copilot` mutation → ai-copilot-service's
 * agent (RAG + platform tools). Seeded with a couple of quick prompts scoped to the current alert, so
 * an analyst can ask "why was this flagged / is it reportable?" in one click.
 */
export function CopilotChat({ alertId, accountId }: { alertId: string; accountId: string }) {
  const [turns, setTurns] = useState<Turn[]>([]);
  const [input, setInput] = useState("");
  const [busy, setBusy] = useState(false);

  async function send(question: string) {
    const q = question.trim();
    if (!q || busy) return;
    setTurns((t) => [...t, { role: "you", text: q }]);
    setInput("");
    setBusy(true);
    try {
      const reply = await askCopilot(q);
      setTurns((t) => [...t, { role: "copilot", text: reply }]);
    } catch (e) {
      setTurns((t) => [...t, { role: "copilot", text: `⚠️ ${String(e)}` }]);
    } finally {
      setBusy(false);
    }
  }

  const suggestions = [
    `Why was alert ${alertId.slice(0, 8)} flagged?`,
    `Is account ${accountId} involved in a fraud ring, and is this reportable?`,
  ];

  return (
    <div className="copilot">
      <div className="chat-log">
        {turns.length === 0 && (
          <div className="muted">Ask the copilot about this alert or account.</div>
        )}
        {turns.map((t, i) => (
          <div key={i} className={`bubble ${t.role}`}>
            <b>{t.role}</b>
            <span>{t.text}</span>
          </div>
        ))}
        {busy && <div className="muted">copilot is thinking…</div>}
      </div>

      <div className="suggestions">
        {suggestions.map((s) => (
          <button key={s} className="btn ghost small" disabled={busy} onClick={() => send(s)}>
            {s}
          </button>
        ))}
      </div>

      <form
        className="chat-input"
        onSubmit={(e) => {
          e.preventDefault();
          send(input);
        }}
      >
        <input
          value={input}
          placeholder="Ask the copilot…"
          onChange={(e) => setInput(e.target.value)}
        />
        <button className="btn" type="submit" disabled={busy || !input.trim()}>
          Send
        </button>
      </form>
    </div>
  );
}
