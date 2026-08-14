(function () {

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

    // "Hide compliant" filter: hides any .endpoint card (an operation, a
    // schema, a request body, a response) with data-has-findings="false"
    // once checked. State persists across page loads the same way the
    // theme toggle does.
    var hideCompliantCheckbox = document.getElementById('hide-compliant-checkbox');
    if (hideCompliantCheckbox) {
        var HIDE_COMPLIANT_STORAGE_KEY = 'speculate:hide-compliant';
        var hideCompliant = localStorage.getItem(HIDE_COMPLIANT_STORAGE_KEY) === 'true';
        hideCompliantCheckbox.checked = hideCompliant;
        document.body.classList.toggle('hide-compliant', hideCompliant);
        hideCompliantCheckbox.addEventListener('change', function () {
            document.body.classList.toggle('hide-compliant', hideCompliantCheckbox.checked);
            localStorage.setItem(HIDE_COMPLIANT_STORAGE_KEY, String(hideCompliantCheckbox.checked));
        });
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
