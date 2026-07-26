export default function waitForBridge() {
  return new Promise((resolve) => {
    const check = () => {
      const candidate = globalThis.grapheneBridge;

      if (candidate && typeof candidate.emit === "function") {
        resolve(candidate);
        return;
      }

      setTimeout(check, 50);
    };

    check();
  });
}