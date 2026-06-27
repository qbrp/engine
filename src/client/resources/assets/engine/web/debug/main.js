// =============================================================================
// LAYER 6: CONTROLLER (main.js)
// Точка входа. Связывает DOM страницы, бридж и рендерер.
// Знает о конкретных элементах страницы и глобальных событиях.
// =============================================================================

import { bridgeWaitForBridge, bridgeExtractPayload } from "./bridge.js";
import { rendererRenderEntity, rendererExpandAll, rendererCollapseAll } from "./renderer.js";
import { domClear } from "./dom.js";

const rootEl = {
	elTitle: document.getElementById("entityTitle"),
	elMeta: document.getElementById("entityMeta"),
	elStatus: document.getElementById("status"),
	elComponents: document.getElementById("components"),
};

window.addEventListener("keydown", (e) => {
	if (e.ctrlKey && e.shiftKey && e.code === "KeyA") {
		rendererExpandAll();
		e.preventDefault();
		return;
	}

	if (e.ctrlKey && e.shiftKey && e.code === "KeyS") {
		rendererCollapseAll();
		e.preventDefault();
	}
});

(async () => {
	const bridge = await bridgeWaitForBridge();
	rootEl.elStatus.textContent = "Ожидание...";

	bridge.on("data", (payload) => {
		try {
			const raw = bridgeExtractPayload(payload);
			const data = JSON.parse(raw);
			rendererRenderEntity(data, rootEl);
			rootEl.elStatus.textContent = "";
		} catch (err) {
			domClear(rootEl.elComponents);
			rootEl.elTitle.textContent = "Ошибка разбора JSON";
			rootEl.elMeta.textContent = "";
			rootEl.elStatus.textContent = "Получен невалидный payload";

			const box = document.createElement("div");
			box.className = "error";
			box.textContent = String(err) + "\n\nRAW:\n" + bridgeExtractPayload(payload);
			rootEl.elComponents.appendChild(box);
		}
	});
})();
