(function () {

    // Analyse button: disabled whenever no plugin row is checked (submitting
    // with nothing checked would just persist "everything off" and run
    // nothing), and also disabled up front if the server says the currently
    // shown results already reflect this exact spec content — re-running
    // would just reproduce them. That "up to date" state is a one-way latch:
    // touching any checkbox or rule-set select releases it for the rest of
    // this page view, on the assumption the visitor is deliberately picking
    // a different combination worth (re-)running, even back to one that
    // happens to match what's already shown.
    var pickerForm = document.getElementById('validation-picker-form');
    if (pickerForm) {
        var pluginCheckboxes = Array.prototype.slice.call(pickerForm.querySelectorAll('input[name="plugin"]'));
        var ruleSetSelects = Array.prototype.slice.call(pickerForm.querySelectorAll('select'));
        var analyseBtn = pickerForm.querySelector('button[type="submit"]');
        if (analyseBtn) {
            var resultUpToDate = pickerForm.getAttribute('data-result-up-to-date') === 'true';
            function updateAnalyseDisabled() {
                var anyChecked = pluginCheckboxes.some(function (cb) { return cb.checked; });
                analyseBtn.disabled = resultUpToDate || !anyChecked;
            }
            pluginCheckboxes.concat(ruleSetSelects).forEach(function (control) {
                control.addEventListener('change', function () {
                    resultUpToDate = false;
                    updateAnalyseDisabled();
                });
            });
            updateAnalyseDisabled();
        }
    }

    // Collapsible endpoint sections, with an expand/collapse-all button
    // scoped to each tag section.
    document.querySelectorAll('.tag-group').forEach(function (group) {
        var endpoints = Array.prototype.slice.call(group.querySelectorAll('.endpoint'));
        var toggleAllBtn = group.querySelector('[data-toggle-all]');

        function updateToggleAllLabel() {
            if (!toggleAllBtn) {
                return;
            }
            var allCollapsed = endpoints.length > 0 && endpoints.every(function (e) {
                return e.classList.contains('collapsed');
            });
            toggleAllBtn.textContent = allCollapsed ? 'Expand all' : 'Collapse all';
        }

        endpoints.forEach(function (endpoint) {
            var header = endpoint.querySelector(':scope > .endpoint-toggle');
            if (!header) {
                return;
            }
            header.addEventListener('click', function () {
                endpoint.classList.toggle('collapsed');
                updateToggleAllLabel();
            });
        });

        if (toggleAllBtn) {
            toggleAllBtn.addEventListener('click', function () {
                var shouldCollapse = toggleAllBtn.textContent === 'Collapse all';
                endpoints.forEach(function (endpoint) {
                    endpoint.classList.toggle('collapsed', shouldCollapse);
                });
                updateToggleAllLabel();
            });
            updateToggleAllLabel();
        }
    });

    // Tabbed request/response body content types.
    document.querySelectorAll('.tabset').forEach(function (tabset) {
        var nav = tabset.querySelector(':scope > .tabset-nav');
        var panelsWrap = tabset.querySelector(':scope > .tabset-panels');
        if (!nav || !panelsWrap) {
            return;
        }
        var btns = Array.prototype.slice.call(nav.querySelectorAll(':scope > .tab-btn'));
        var panels = Array.prototype.slice.call(panelsWrap.querySelectorAll(':scope > .tab-panel'));

        btns.forEach(function (btn, i) {
            btn.addEventListener('click', function () {
                btns.forEach(function (b) { b.classList.remove('active'); });
                panels.forEach(function (p) { p.classList.remove('active'); });
                btn.classList.add('active');
                if (panels[i]) {
                    panels[i].classList.add('active');
                }
            });
        });
    });

    // Collapsible Request Body / Responses sections.
    document.querySelectorAll('.section-toggle').forEach(function (header) {
        header.addEventListener('click', function () {
            var section = header.closest('.section');
            if (section) {
                section.classList.toggle('collapsed');
            }
        });
    });

    // Severity filter: each finding row carries data-severity (ERROR/WARNING/
    // INFO/HINT); each .endpoint card (an operation, a schema, a request
    // body, a response) carries data-has-findings. A row shows only if its
    // severity is checked; a card shows if it has a visible row, or — for a
    // card with no findings at all — if "Compliant" is checked. Rows outside
    // any .endpoint card (the General tab's flat list) are filtered the same
    // way, just with no card to hide. State persists the same way the theme
    // toggle does.
    var severityFilter = document.querySelector('.severity-filter');
    if (severityFilter) {
        var SEVERITY_FILTER_STORAGE_KEY = 'speculate:severity-filter';
        var allCheckbox = severityFilter.querySelector('.severity-filter-all');
        var valueCheckboxes = Array.prototype.slice.call(severityFilter.querySelectorAll('.severity-filter-value'));

        var saved = null;
        try {
            saved = JSON.parse(localStorage.getItem(SEVERITY_FILTER_STORAGE_KEY));
        } catch (e) {
            saved = null;
        }
        if (Array.isArray(saved)) {
            valueCheckboxes.forEach(function (cb) {
                cb.checked = saved.indexOf(cb.value) !== -1;
            });
            allCheckbox.checked = valueCheckboxes.every(function (cb) { return cb.checked; });
        }

        function applyFilter() {
            var active = valueCheckboxes.filter(function (cb) { return cb.checked; }).map(function (cb) { return cb.value; });

            document.querySelectorAll('.endpoint-findings tbody tr[data-severity]').forEach(function (row) {
                row.style.display = active.indexOf(row.getAttribute('data-severity')) === -1 ? 'none' : '';
            });

            document.querySelectorAll('.endpoint').forEach(function (card) {
                if (card.getAttribute('data-has-findings') === 'false') {
                    card.style.display = active.indexOf('COMPLIANT') === -1 ? 'none' : '';
                    return;
                }
                var anyRowVisible = Array.prototype.some.call(
                    card.querySelectorAll('.endpoint-findings tbody tr[data-severity]'),
                    function (row) { return row.style.display !== 'none'; }
                );
                card.style.display = anyRowVisible ? '' : 'none';
            });

            localStorage.setItem(SEVERITY_FILTER_STORAGE_KEY, JSON.stringify(active));
        }

        allCheckbox.addEventListener('change', function () {
            valueCheckboxes.forEach(function (cb) { cb.checked = allCheckbox.checked; });
            applyFilter();
        });
        valueCheckboxes.forEach(function (cb) {
            cb.addEventListener('change', function () {
                allCheckbox.checked = valueCheckboxes.every(function (c) { return c.checked; });
                applyFilter();
            });
        });

        applyFilter();
    }

    // Toggling nested schema details (object properties / array items).
    document.querySelectorAll('.schema-toggle-btn').forEach(function (btn) {
        var container = btn.closest('tr, .schema-array-items');
        if (!container) {
            return;
        }
        var content = container.querySelector('.schema-toggle-content');
        if (!content) {
            return;
        }
        btn.addEventListener('click', function () {
            var collapsed = content.classList.toggle('collapsed');
            btn.textContent = collapsed ? '▸' : '▾';
            btn.setAttribute('aria-expanded', String(!collapsed));
        });
    });

})();
