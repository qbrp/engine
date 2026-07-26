import waitForBridge from "./bridge.js";

(async () => {
  const closeButton = document.getElementById("closeWindow");
  if (!closeButton) return;

  const bridge = await waitForBridge();
  closeButton.addEventListener("click", () => {
    bridge.emit("window:close", {});
  });
})();
