// =============================================================================
// LAYER 5: RENDERER
// Создаёт, обновляет и патчит DOM-дерево по данным.
// Читает/пишет состояние через функции слоя STATE.
// Использует DOM HELPERS и UTILS, но не знает о бридже.
// =============================================================================

import { makePath, safeStringify, componentKey } from "./util.js";
import { domClear, domCreateTextSpan, domSetLeaf, domSyncOpen } from "./dom.js";
import {
	uiState,
	stateOpenPath,
	stateClosePath,
	stateIsOpen,
	stateRegisterDetails,
	stateRemoveByPrefix,
	stateExpandAll,
	stateCollapseAll,
} from "./state.js";

const renderMeta = new WeakMap();

const el = (tag, className, text = "") => {
	const node = document.createElement(tag);
	if (className) node.className = className;
	if (text !== "") node.textContent = text;
	return node;
};

const errorNode = (text) => el("div", "error", text);

function setLeafError(container, text) {
	domSetLeaf(container, errorNode(text));
}

function addBadge(elMeta, text) {
	elMeta.appendChild(el("span", "badge", text));
}

function wireDetails(details, path, onOpen) {
	stateRegisterDetails(path, details);

	details.addEventListener("toggle", () => {
		if (details.open) {
			stateOpenPath(path);
			onOpen?.();
		} else {
			stateClosePath(path);
		}
	});

	domSyncOpen(details, stateIsOpen(path));
}

function syncComponentData(card, component, objects) {
	card.component.type = component.type ?? "";
	card.component.rootId = component.rootId;
	card.objects = objects;
	card.type.textContent = component.type ?? "(без типа)";
	domSyncOpen(card.details, stateIsOpen(card.key));
}

function syncMeta(container, kind, path, build) {
	let meta = renderMeta.get(container);

	if (!meta || meta.kind !== kind || meta.path !== path) {
		domClear(container);
		meta = build();
		renderMeta.set(container, meta);
	}

	return meta;
}

function removeGone(map, nextKeys, getPath, removeNode) {
	for (const [key, node] of [...map]) {
		if (!nextKeys.has(key)) {
			stateRemoveByPrefix(getPath(key));
			removeNode(node);
			map.delete(key);
		}
	}
}

// --- Entry points ---

export function rendererRenderEntity(data, rootEl) {
	const { elTitle, elMeta, elStatus, elComponents } = rootEl;

	if (!data || typeof data !== "object") {
		elTitle.textContent = "Некорректные данные";
		elMeta.replaceChildren();
		elStatus.textContent = "Получен payload, который не удалось разобрать как объект";

		domClear(elComponents);
		elComponents.appendChild(errorNode(safeStringify(data)));
		return;
	}

	elTitle.textContent = `Entity ${safeStringify(data.id)}`;

	elMeta.replaceChildren();
	addBadge(elMeta, `clientSide: ${Boolean(data.clientSide)}`);
	addBadge(elMeta, `components: ${Array.isArray(data.components) ? data.components.length : 0}`);
	addBadge(elMeta, `objects: ${Object.keys(data.objects ?? {}).length}`);

	elStatus.textContent = "Ожидание...";

	_syncComponents(
		Array.isArray(data.components) ? data.components : [],
		data.objects || {},
		elComponents,
	);
}

// --- Components ---

class ComponentCard {
	constructor(component, objects) {
		this.key = componentKey(component);
		this.objects = objects;

		this.component = {
			type: "",
			rootId: component.rootId,
		};

		this.details = el("details", "component");
		this.details.dataset.path = this.key;

		this.summary = el("summary", "component-head");
		this.typeEl = el("div", "component-type", component.type ?? "(без типа)");
		this.body = el("div", "object");

		this.summary.appendChild(this.typeEl);
		this.details.appendChild(this.summary);
		this.details.appendChild(this.body);

		this.details.addEventListener("toggle", () => {
			if (this.details.open) {
				stateOpenPath(this.key);
				this.renderBody(true);
			} else {
				stateClosePath(this.key);
			}
		});

		domSyncOpen(this.details, stateIsOpen(this.key));
		this.sync(component, objects);
		this.updateState();
	}

	sync(component, objects) {
		this.objects = objects;
		this.component.type = component.type ?? "";
		this.component.rootId = component.rootId;

		this.typeEl.textContent = component.type ?? "(без типа)";
		domSyncOpen(this.details, stateIsOpen(this.key));

		this.updateState();
	}

	renderBody(autoexpand = false) {
		_patchObjectInto(
			this.body,
			this.component.rootId,
			this.objects,
			makePath(this.key, "root"),
			new Set(),
			autoexpand
		);
	}

	destroy() {
		stateRemoveByPrefix(this.key);
		this.details.remove();
	}

	isEmpty() {
		const obj = _getObjectById(this.objects, this.component.rootId);
		const emptyList = obj?.type === "collection" && (obj.values?.length ?? 0) === 0
		const emptyTable =
			obj?.type === "table" &&
			Object.keys(obj.values ?? {}).length === 0;
		return emptyList || emptyTable;
	}

	updateState() {
		this.details.classList.toggle("is-empty", this.isEmpty());
	}
}

function _syncComponents(components, objects, elComponents) {
	const nextKeys = new Set();
	const nextCards = new Map();

	for (const component of components) {
		const key = componentKey(component);
		nextKeys.add(key);

		let card = uiState.componentCards.get(key);
		if (!card) {
			card = new ComponentCard(component, objects);
		} else {
			card.sync(component, objects);
		}

		nextCards.set(key, card);
		elComponents.appendChild(card.details);

		if (card.details.open) {
			card.renderBody();
		}
	}

	for (const [key, card] of uiState.componentCards) {
		if (!nextKeys.has(key)) {
			card.destroy();
		}
	}

	uiState.componentCards = nextCards;
}

export function rendererExpandAll() {
	stateExpandAll();

	for (const [, card] of uiState.componentCards) {
		if (card?.details) domSyncOpen(card.details, true);
	}
}

export function rendererCollapseAll() {
	stateCollapseAll();

	for (const [, card] of uiState.componentCards) {
		if (card?.details) domSyncOpen(card.details, false);
	}
}


// --- Objects ---

function _getObjectById(objects, id) {
	const objectId = String(id);

	return objects?.[objectId] ?? objects?.[id] ?? objects?.[Number(objectId)];
}

function _patchObjectInto(container, id, objects, path, seen, autoexpand) {
	const objectId = String(id);

	const obj = _getObjectById(objects, objectId);

	if (!obj) {
		setLeafError(container, `Объект #${objectId} не найден в objects`);
		return;
	}

	const nextSeen = new Set(seen);
	nextSeen.add(objectId);

	switch (obj.type) {
		case "table":
			_patchTableInto(container, obj, objects, path, nextSeen, autoexpand);
			return;

		case "collection":
			_patchCollectionInto(container, obj, objects, path, nextSeen, autoexpand);
			return;

		default:
			setLeafError(container, `Неизвестный тип объекта #${objectId}: ${safeStringify(obj.type)}`);
	}
}

// --- Table ---

function _patchTableInto(container, obj, objects, path, seen, autoexpand) {
	const meta = syncMeta(container, "table", path, () => {
		const table = el("table", "obj-table");
		const tbody = document.createElement("tbody");
		table.appendChild(tbody);
		container.appendChild(table);

		return { kind: "table", path, table, tbody, rowByKey: new Map() };
	});

	const entries = Object.entries(obj.values || {}).sort(([a], [b]) =>
		a.localeCompare(b, "ru", { numeric: true })
	);

	const nextKeys = new Set();

	for (const [key, entry] of entries) {
		nextKeys.add(key);

		let row = meta.rowByKey.get(key);
		if (!row) {
			row = _createTableRow(key);
			meta.rowByKey.set(key, row);
		}

		_updateTableRow(row, key, entry, objects, `${path}/key:${key}`, seen, autoexpand);
		meta.tbody.appendChild(row);
	}

	removeGone(
		meta.rowByKey,
		nextKeys,
		(key) => `${path}/key:${key}`,
		(row) => row.remove(),
	);
}

function _createTableRow(key) {
	const tr = document.createElement("tr");

	const th = document.createElement("th");
	th.className = "table-key";
	th.textContent = key;

	const td = document.createElement("td");
	td.className = "table-value";

	tr.appendChild(th);
	tr.appendChild(td);

	return tr;
}

function _updateTableRow(row, key, entry, objects, path, seen, autoexpand) {
	row.firstChild.textContent = key;
	_patchEntryInto(row.lastChild, entry, objects, path, seen, autoexpand);
}

// --- Collection ---

function _patchCollectionInto(container, obj, objects, path, seen) {
	const meta = syncMeta(container, "collection", path, () => {
		const list = el("div", "collection");
		container.appendChild(list);

		return { kind: "collection", path, list, itemByIndex: new Map() };
	});

	const values = obj.values || [];
	const nextIndexes = new Set();

	for (let index = 0; index < values.length; index++) {
		const entry = values[index];
		nextIndexes.add(index);

		let item = meta.itemByIndex.get(index);
		if (!item) {
			item = _createCollectionItem(index);
			meta.itemByIndex.set(index, item);
		}

		_updateCollectionItem(item, entry, objects, `${path}/index:${index}`, seen);
		meta.list.appendChild(item);
	}

	removeGone(
		meta.itemByIndex,
		nextIndexes,
		(index) => `${path}/index:${index}`,
		(item) => item.remove(),
	);
}

function _createCollectionItem(index) {
	const item = document.createElement("div");
	item.className = "collection-item";

	const idx = document.createElement("div");
	idx.className = "collection-index";
	idx.appendChild(domCreateTextSpan(`[${index}]`, "badge"));

	const body = document.createElement("div");
	body.className = "collection-body";

	item.appendChild(idx);
	item.appendChild(body);

	return item;
}

function _updateCollectionItem(item, entry, objects, path, seen) {
	_patchEntryInto(item.lastChild, entry, objects, path, seen);
}

// --- Entries and refs ---

function _patchEntryInto(container, entry, objects, path, seen, autoexpand) {
	if (!entry || typeof entry !== "object") {
		domSetLeaf(container, domCreateTextSpan(String(entry), "value"));
		return;
	}

	switch (entry.type) {
		case "primitive":
			domSetLeaf(container, domCreateTextSpan(String(entry.value), "value"));
			return;

		case "null":
			domSetLeaf(container, domCreateTextSpan("null", "null"));
			return;

		case "ref":
			_patchRefInto(container, entry.id, objects, path, seen, autoexpand);
			return;

		default:
			domSetLeaf(container, domCreateTextSpan(safeStringify(entry), "value"));
	}
}

function _patchRefInto(container, refId, objects, path, seen, autoexpand) {
	let details = container.firstElementChild;
	let nested = null;

	const target = _getObjectById(objects, refId);

	const isEmptyCollection =
		target?.type === "collection" &&
		(Array.isArray(target.values) ? target.values.length === 0 : true);

	const summaryText = isEmptyCollection ? "empty list" : "...";

	if (!details || details.tagName !== "DETAILS" || details.dataset.path !== path) {
		domClear(container);

		details = el("details", "ref");
		details.dataset.path = path;

		const summary = el("summary", "", summaryText);
		nested = el("div", "nested");

		details.appendChild(summary);
		details.appendChild(nested);
		container.appendChild(details);

		wireDetails(details, path, () => {
			_renderRefBody(nested, refId, objects, path, seen);
		});

		if (autoexpand) {
			domSyncOpen(details, true);
		}
	} else {
		details.dataset.path = path;

		const summary = details.firstElementChild;
		if (summary) summary.textContent = summaryText;

		nested = details.lastElementChild;
		domSyncOpen(details, stateIsOpen(path));
	}

	if (details.open && nested) {
		_renderRefBody(nested, refId, objects, path, seen);
	}
}

function _renderRefBody(nested, refId, objects, path, seen) {
	const target = _getObjectById(objects, refId);

	if (!target) {
		domClear(nested);
		nested.appendChild(errorNode(`Объект #${refId} не найден в objects`));
		return;
	}

	_patchObjectInto(nested, refId, objects, path, seen);
}