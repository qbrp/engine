let bridge = null

function waitForBridge() {
  return new Promise((resolve) => {
    const check = () => {
      const candidate = globalThis.grapheneBridge;

      if (candidate && typeof candidate.request === "function") {
        resolve(candidate);
        return;
      }

      setTimeout(check, 50);
    };

    check();
  });
}


(async () => {
  bridge = await waitForBridge();

  document.getElementById("login").addEventListener("click", function () {
    bridge.emit("login", {})
  });

  const checkbox = document.getElementById("auto_login_checkbox");
  checkbox.addEventListener('change', function() {
    bridge.emit("auto_login_checkbox", this.checked)
  });

  checkbox.checked = await bridge.request("auto_login_checkbox_value", {})
})();