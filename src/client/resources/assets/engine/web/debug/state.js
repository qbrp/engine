// =============================================================================
// LAYER 1: STATE
// Единственный источник правды для UI-состояния.
// Не содержит логики рендеринга и не знает о DOM-элементах страницы.
// =============================================================================

export const uiState = {
	openPaths: new Set(),
	detailsByPath: new Map(),
	componentCards: new Map(),
};

export function stateOpenPath(path) {
	uiState.openPaths.add(path);
}

export function stateClosePath(path) {
	uiState.openPaths.delete(path);
}

export function stateIsOpen(path) {
	return uiState.openPaths.has(path);
}

export function stateRegisterDetails(path, el) {
	uiState.detailsByPath.set(path, el);
}

export function stateRemoveByPrefix(prefix) {
	for (const path of [...uiState.openPaths]) {
		if (path === prefix || path.startsWith(prefix + "/")) {
			uiState.openPaths.delete(path);
		}
	}

	for (const [path] of [...uiState.detailsByPath]) {
		if (path === prefix || path.startsWith(prefix + "/")) {
			uiState.detailsByPath.delete(path);
		}
	}
}

export function stateExpandAll() {
	for (const [path] of uiState.detailsByPath) {
		uiState.openPaths.add(path);
	}
}

export function stateCollapseAll() {
	uiState.openPaths.clear();
}
