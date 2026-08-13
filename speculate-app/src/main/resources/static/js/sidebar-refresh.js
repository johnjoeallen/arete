(function () {
    var POLL_INTERVAL_MS = 3000;

    var list = document.querySelector('.spec-list');
    if (!list) {
        return;
    }

    function currentQuery() {
        return new URLSearchParams(window.location.search).get('q') || '';
    }

    function currentActiveId() {
        var tabBar = document.getElementById('tab-bar');
        return tabBar ? (tabBar.dataset.specId || null) : null;
    }

    function specsEqual(a, b) {
        if (a.length !== b.length) {
            return false;
        }
        for (var i = 0; i < a.length; i++) {
            if (a[i].id !== b[i].id || a[i].title !== b[i].title) {
                return false;
            }
        }
        return true;
    }

    function render(specs) {
        var activeId = currentActiveId();
        list.innerHTML = '';

        if (specs.length === 0) {
            var empty = document.createElement('li');
            empty.className = 'spec-list-empty';
            empty.textContent = 'No specs saved yet.';
            list.appendChild(empty);
            return;
        }

        specs.forEach(function (spec) {
            var item = document.createElement('li');
            item.className = 'spec-list-item';

            var link = document.createElement('a');
            link.href = '/spec/' + spec.id;
            link.textContent = spec.title;
            if (activeId && String(activeId) === String(spec.id)) {
                link.className = 'active';
            }
            item.appendChild(link);

            var form = document.createElement('form');
            form.method = 'post';
            form.action = '/api/specs/' + spec.id + '/delete';
            form.className = 'delete-form';
            var deleteBtn = document.createElement('button');
            deleteBtn.type = 'submit';
            deleteBtn.className = 'delete-btn';
            deleteBtn.title = 'Delete';
            deleteBtn.textContent = '×';
            form.appendChild(deleteBtn);
            item.appendChild(form);

            list.appendChild(item);
        });
    }

    function findSpec(specs, id) {
        for (var i = 0; i < specs.length; i++) {
            if (String(specs[i].id) === String(id)) {
                return specs[i];
            }
        }
        return null;
    }

    var lastSpecs = null;

    function poll() {
        var q = currentQuery();
        fetch('/api/specs' + (q ? '?q=' + encodeURIComponent(q) : ''))
            .then(function (res) { return res.ok ? res.json() : null; })
            .then(function (specs) {
                if (!specs) {
                    return;
                }

                // A body-only edit (title unchanged) doesn't change this list at
                // all, so it can't be caught by specsEqual below — if it's the
                // spec currently open, reload the page to pick it up.
                var activeId = currentActiveId();
                if (activeId && lastSpecs) {
                    var prevActive = findSpec(lastSpecs, activeId);
                    var newActive = findSpec(specs, activeId);
                    if (prevActive && newActive && prevActive.updatedAtMillis !== newActive.updatedAtMillis) {
                        window.location.reload();
                        return;
                    }
                }

                var changed = !lastSpecs || !specsEqual(lastSpecs, specs);
                lastSpecs = specs;
                if (changed) {
                    render(specs);
                }
            })
            .catch(function () {
                // Transient fetch failures (e.g. a mid-restart request) just wait for the next poll.
            });
    }

    setInterval(poll, POLL_INTERVAL_MS);
})();
