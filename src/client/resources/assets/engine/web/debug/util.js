// =============================================================================
// LAYER 3: UTILS
// Чистые функции без побочных эффектов.
// Не зависят от DOM, состояния или бриджа.
// =============================================================================

export function makePath(...parts) {
	return parts.join("/");
}

export function safeStringify(value) {
	if (typeof value === "string") return value;

	try {
		return JSON.stringify(value);
	} catch {
		return String(value);
	}
}

export function componentKey(component) {
	return makePath(
		"component",
		String(component?.type ?? ""),
		String(component?.rootId ?? ""),
	);
}
