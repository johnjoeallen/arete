// Namespace / submitter picker. Both are plain self-asserted labels stored in
// cookies — not authentication. Changing the namespace reloads so the spec
// list refilters; the submitter just updates the cookie for the next save.
(function () {
    function setCookie(name, value) {
        document.cookie = name + '=' + encodeURIComponent(value)
            + '; path=/; max-age=31536000; SameSite=Lax';
    }

    function slug(raw) {
        return (raw || '').trim().toLowerCase()
            .replace(/[^a-z0-9._-]+/g, '-')
            .replace(/^[-.]+|[-.]+$/g, '')
            .slice(0, 63);
    }

    window.areteSetNamespace = function (raw) {
        var s = slug(raw);
        if (!s) { return; }
        setCookie('arete_namespace', s);
        window.location.assign('/');
    };

    window.areteSetSubmitter = function (raw) {
        setCookie('arete_submitter', slug(raw) || 'ui');
    };
})();
