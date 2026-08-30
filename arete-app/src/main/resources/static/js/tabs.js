(function () {
    var STORAGE_KEY = 'openapi-viewer:tabs';

    function loadTabs() {
        try {
            var raw = JSON.parse(localStorage.getItem(STORAGE_KEY));
            return Array.isArray(raw) ? raw : [];
        } catch (e) {
            return [];
        }
    }

    function saveTabs(tabs) {
        localStorage.setItem(STORAGE_KEY, JSON.stringify(tabs));
    }

    var bar = document.getElementById('tab-bar');
    if (!bar) {
        return;
    }

    var currentId = bar.dataset.specId || null;
    var currentTitle = bar.dataset.specTitle || null;

    var tabs = loadTabs();

    var closedTabId = new URLSearchParams(window.location.search).get('closedTab');
    if (closedTabId) {
        var hadClosedTab = tabs.some(function (t) { return String(t.id) === String(closedTabId); });
        tabs = tabs.filter(function (t) { return String(t.id) !== String(closedTabId); });
        if (hadClosedTab) {
            saveTabs(tabs);
        }
        var cleanUrl = window.location.pathname + window.location.hash;
        window.history.replaceState(null, '', cleanUrl);
    }

    if (currentId) {
        var idx = -1;
        for (var i = 0; i < tabs.length; i++) {
            if (String(tabs[i].id) === String(currentId)) {
                idx = i;
                break;
            }
        }
        if (idx === -1) {
            tabs.push({ id: currentId, title: currentTitle });
        } else if (currentTitle) {
            tabs[idx].title = currentTitle;
        }
        saveTabs(tabs);
    }

    function render() {
        bar.innerHTML = '';

        tabs.forEach(function (tab) {
            var item = document.createElement('span');
            item.className = 'tab' + (String(tab.id) === String(currentId) ? ' active' : '');

            var link = document.createElement('a');
            link.href = '/spec/' + tab.id;
            link.textContent = tab.title || ('Spec ' + tab.id);
            item.appendChild(link);

            var closeBtn = document.createElement('button');
            closeBtn.type = 'button';
            closeBtn.className = 'tab-close';
            closeBtn.textContent = '×';
            closeBtn.setAttribute('aria-label', 'Close tab');
            closeBtn.addEventListener('click', function (e) {
                e.preventDefault();
                closeTab(tab.id);
            });
            item.appendChild(closeBtn);

            bar.appendChild(item);
        });

        var homeLink = document.createElement('a');
        homeLink.href = '/';
        homeLink.className = 'tab-new';
        homeLink.title = 'Paste a new spec';
        homeLink.textContent = '+';
        bar.appendChild(homeLink);
    }

    function closeTab(id) {
        var wasActive = String(id) === String(currentId);
        tabs = tabs.filter(function (t) {
            return String(t.id) !== String(id);
        });
        saveTabs(tabs);

        if (wasActive) {
            if (tabs.length > 0) {
                window.location.href = '/spec/' + tabs[tabs.length - 1].id;
            } else {
                window.location.href = '/';
            }
        } else {
            render();
        }
    }

    render();
})();
