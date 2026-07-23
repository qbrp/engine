const inputLine = document.querySelector(".input-line");
const promptElem = document.querySelector(".prompt");
const input = document.getElementById("input");
const output = document.getElementById("output");

let awaitingResponse = false;
let inInput = false
let processRunning = false;
let currentPrompt = "C:\\>";

function delay(ms) {
    return new Promise(resolve => setTimeout(resolve, ms));
}

function appendLine(text, className = "line") {
    const line = document.createElement("div");
    line.className = className;
    line.textContent = text;

    output.insertBefore(line, inputLine);
    output.scrollTop = output.scrollHeight;

    return line;
}

function commitInput(command) {
    if (processRunning) {
        appendLine(`${currentPrompt}${command}`);
    } else {
        appendLine(`C:\\>${command}`);
    }
}

function hidePrompt() {
    promptElem.textContent = "";
    promptElem.style.display = "none";
}

function showShellPrompt() {
    currentPrompt = "C:\\>";
    promptElem.textContent = currentPrompt;
    promptElem.style.display = "";
}

function showInputPrompt(prompt) {
    inInput = true
    currentPrompt = prompt;
    promptElem.textContent = currentPrompt;
    promptElem.style.display = "";
}

function lockInput() {
    awaitingResponse = true;
    input.contentEditable = "false";
}

function unlockInput() {
    awaitingResponse = false;
    input.contentEditable = "true";
    input.focus();
}

function onInput(text) {
    globalThis.grapheneBridge.emit("input", {
        text: text.toString(),
        input: inInput
    });
}

async function waitForBridge() {
    while (!globalThis.grapheneBridge) {
        await delay(20);
    }

    return globalThis.grapheneBridge;
}

(async () => {
    const bridge = await waitForBridge();

    bridge.on("print", payload => {
        appendLine(payload.line);
    });

    bridge.on("clear", () => {
        while (output.firstChild && output.firstChild !== inputLine) {
            output.removeChild(output.firstChild);
        }});


    bridge.on("complete", () => {
        processRunning = false;

        unlockInput();
        showShellPrompt();
    });

    bridge.on("wait_input", payload => {
        showInputPrompt(payload.line);
        unlockInput();
    });

    const status = await bridge.request("status", {});

    for (const line of status.lines) {
        appendLine(line);
    }

    if (status.process != null) {
        processRunning = true;
        hidePrompt();
        lockInput();

        if (status.wait) {
            unlockInput();
        }
    } else {
        showShellPrompt();
    }
})();

input.addEventListener("keydown", e => {
    if (e.key !== "Enter") return;

    e.preventDefault();

    if (awaitingResponse) return;

    const value = input.textContent.trim();
    if (!value) return;

    commitInput(value);

    processRunning = true;

    input.textContent = "";

    lockInput();
    hidePrompt();

    onInput(value);
    inInput = false
});

input.focus();