(function () {
    var picker = document.getElementById('matcher-example');
    if (!picker) {
        return;
    }

    var form = picker.form;
    var source = form.querySelector('textarea[name="matcherSource"]');
    var spec = form.querySelector('textarea[name="spec"]');
    var matcherId = document.getElementById('matcher-id');
    var scope = document.getElementById('matcher-scope');
    var parameters = document.getElementById('matcher-parameters');

    function showExample(frame) {
        document.querySelectorAll('.matcher-example-frame').forEach(function (other) {
            other.classList.toggle('matcher-example-frame-selected', other === frame);
        });
    }

    function clearFields() {
        source.value = '';
        spec.value = '';
        matcherId.value = '';
        scope.value = '';
        parameters.value = '';
        showExample(null);
    }

    function updateParameters(frame) {
        var control = frame.querySelector('.matcher-example-parameter');
        parameters.value = control ? JSON.stringify((function () {
            var result = {};
            result[control.dataset.key] = control.value;
            return result;
        })()) : '{}';
    }

    function loadParameterControl(frame) {
        var control = frame.querySelector('.matcher-example-parameter');
        if (!control) {
            return;
        }
        try {
            var defaults = JSON.parse(frame.querySelector('.matcher-example-parameters').value || '{}');
            if (defaults[control.dataset.key] !== undefined) {
                control.value = defaults[control.dataset.key];
            }
        } catch (ignored) {
            // The example parameters are controlled by the server.
        }
    }

    function selectExample() {
        var frame = document.getElementById('matcher-example-frame-' + picker.value);
        if (!frame) {
            clearFields();
            return;
        }
        showExample(frame);
        source.value = frame.querySelector('.matcher-example-source').value;
        spec.value = frame.querySelector('.matcher-example-spec').value;
        matcherId.value = frame.querySelector('.matcher-example-id').value;
        scope.value = frame.querySelector('.matcher-example-scope').value;
        loadParameterControl(frame);
        updateParameters(frame);
        source.focus();
    }

    picker.addEventListener('change', selectExample);
    document.getElementById('matcher-clear').addEventListener('click', function () {
        picker.value = '';
        clearFields();
        source.focus();
    });
    document.querySelectorAll('.matcher-example-parameter').forEach(function (control) {
        control.addEventListener('change', function () {
            var frame = control.closest('.matcher-example-frame');
            if (frame.classList.contains('matcher-example-frame-selected')) {
                updateParameters(frame);
            }
        });
    });

    var selectedFrame = document.getElementById('matcher-example-frame-' + picker.value);
    if (selectedFrame) {
        try {
            var selectedParameters = JSON.parse(parameters.value || '{}');
            var selectedControl = selectedFrame.querySelector('.matcher-example-parameter');
            if (selectedControl && selectedParameters[selectedControl.dataset.key] !== undefined) {
                selectedControl.value = selectedParameters[selectedControl.dataset.key];
            }
        } catch (ignored) {
            // The server will report malformed JSON when the matcher is run.
        }
    }
})();
