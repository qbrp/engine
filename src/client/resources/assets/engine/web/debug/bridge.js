// =============================================================================
// LAYER 4: BRIDGE
// Адаптер для связи со внешней системой (globalThis.grapheneBridge).
// Не знает о структуре данных приложения — только транспорт.
// =============================================================================

import { safeStringify } from "./util.js";

export function bridgeWaitForBridge() {
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

export function bridgeExtractPayload(payload) {
	if (typeof payload === "string") return payload;

	if (payload && typeof payload === "object") {
		if (typeof payload.text === "string") return payload.text;
		if (typeof payload.data === "string") return payload.data;
		return safeStringify(payload);
	}

	return String(payload);
}
