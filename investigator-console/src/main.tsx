import React from "react";
import ReactDOM from "react-dom/client";
import App from "./App";
import { initAuth } from "./keycloak";
import "./styles.css";

// Boot Keycloak (login-required) BEFORE rendering, so the app only ever mounts for an authenticated
// analyst and every component can assume a live token. A failure here means Keycloak is unreachable.
initAuth()
  .then(() => {
    ReactDOM.createRoot(document.getElementById("root")!).render(
      <React.StrictMode>
        <App />
      </React.StrictMode>,
    );
  })
  .catch((err) => {
    document.getElementById("root")!.innerHTML =
      `<div style="padding:2rem;font-family:sans-serif;color:#b00">` +
      `Could not reach Keycloak for login (${String(err)}). Is the stack up on :8087?</div>`;
  });
