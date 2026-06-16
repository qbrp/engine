document.addEventListener("keydown", (e) => {
    if ((e.ctrlKey || e.metaKey) && e.code === "KeyA") {
        const active = document.activeElement;

        if (!active?.isContentEditable) return;

        e.preventDefault();

        const selection = window.getSelection();
        const range = document.createRange();

        range.selectNodeContents(active);

        selection.removeAllRanges();
        selection.addRange(range);
    }
});