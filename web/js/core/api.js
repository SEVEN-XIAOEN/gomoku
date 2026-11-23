/* ------------------------------------------------------------------
   网络层
   所有接口调用都从这里走：统一 JSON、统一错误信息、统一在线状态提示。
   后端返回的错误格式为 { "error": "..." }。
   ------------------------------------------------------------------ */
(function () {
    'use strict';

    window.QP = window.QP || {};

    function netState(ok, text) {
        if (window.QP.ui && window.QP.ui.netStatus) {
            window.QP.ui.netStatus(ok ? 'ok' : 'error', text);
        }
    }

    function request(url, init) {
        var options = init || {};
        options.credentials = 'same-origin';
        options.cache = 'no-store';
        if (options.body && !options.headers) {
            options.headers = { 'Content-Type': 'application/json' };
        }

        return fetch(url, options).then(function (response) {
            var type = response.headers.get('Content-Type') || '';
            var parsing = type.indexOf('application/json') >= 0
                ? response.json().catch(function () { return null; })
                : Promise.resolve(null);

            return parsing.then(function (data) {
                if (!response.ok) {
                    var message = (data && data.error) || ('请求失败（HTTP ' + response.status + '）');
                    var error = new Error(message);
                    error.status = response.status;
                    throw error;
                }
                netState(true, '在线');
                return data;
            });
        }, function () {
            netState(false, '连接中断');
            throw new Error('连不上服务器，请确认服务已经启动');
        });
    }

    window.QP.api = {
        get: function (url) {
            return request(url, { method: 'GET' });
        },
        post: function (url, payload) {
            return request(url, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(payload || {})
            });
        }
    };
})();
