// =============================================================================
// LAYER 2: DOM HELPERS
// Низкоуровневые утилиты для работы с DOM.
// Не знают ни о состоянии приложения, ни о структуре данных.
// =============================================================================

export function domClear(node) {
	while (node.firstChild) {
		node.removeChild(node.firstChild);
	}
}

export function domCreateTextSpan(text, className) {
	const span = document.createElement("span");
	span.textContent = text;

	if (className) {
		span.className = className;
	}

	return span;
}

export function domSetLeaf(container, node) {
	if (container.firstChild === node && container.childNodes.length === 1) {
		return;
	}

	domClear(container);
	container.appendChild(node);
}

export function domSyncOpen(details, shouldOpen) {
	if (details.open !== shouldOpen) {
		details.open = shouldOpen;
	}
}
