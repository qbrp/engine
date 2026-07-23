const input = document.getElementById('input');

input.addEventListener('input', () => {
  input.style.height = 'auto';
  input.style.height = input.scrollHeight + 'px';
});

input.focus()