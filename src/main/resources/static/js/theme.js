(function () {
    var STORAGE_KEY = 'openapi-viewer:theme';
    var toggleBtn = document.getElementById('theme-toggle');
    if (!toggleBtn) {
        return;
    }

    function currentTheme() {
        return document.documentElement.getAttribute('data-theme') === 'dark' ? 'dark' : 'light';
    }

    function updateLabel() {
        toggleBtn.textContent = currentTheme() === 'dark' ? '☀️ Light mode' : '🌙 Dark mode';
    }

    toggleBtn.addEventListener('click', function () {
        var next = currentTheme() === 'dark' ? 'light' : 'dark';
        document.documentElement.setAttribute('data-theme', next);
        localStorage.setItem(STORAGE_KEY, next);
        updateLabel();
    });

    updateLabel();
})();
