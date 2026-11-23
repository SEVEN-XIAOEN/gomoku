/* ------------------------------------------------------------------
   Hash 路由：#/lobby  #/gomoku  #/xiangqi
   每个页面注册 show / hide，切换时旧页面先隐藏再显示新页面。
   ------------------------------------------------------------------ */
(function () {
    'use strict';

    window.QP = window.QP || {};
    var routes = {};
    var handlers = [];
    var currentPath = null;

    /** 把 #/xiangqi?room=ABC123 拆成路径和查询参数。 */
    function parse(hash) {
        var raw = (hash || '').replace(/^#/, '');
        var query = {};
        var mark = raw.indexOf('?');
        if (mark >= 0) {
            var search = raw.slice(mark + 1);
            raw = raw.slice(0, mark);
            search.split('&').forEach(function (pair) {
                if (!pair) {
                    return;
                }
                var eq = pair.indexOf('=');
                var key = eq >= 0 ? pair.slice(0, eq) : pair;
                var value = eq >= 0 ? pair.slice(eq + 1) : '';
                try {
                    key = decodeURIComponent(key.replace(/\+/g, ' '));
                    value = decodeURIComponent(value.replace(/\+/g, ' '));
                } catch (error) {
                    // 非法转义时保留原文
                }
                query[key] = value;
            });
        }
        if (!raw) {
            return { path: '', query: query };
        }
        if (raw.charAt(0) !== '/') {
            raw = '/' + raw;
        }
        return { path: raw.replace(/\/+$/, '') || '/', query: query };
    }

    function normalize(hash) {
        return parse(hash).path;
    }

    function dispatch() {
        var path = normalize(window.location.hash);
        if (!routes[path]) {
            path = '/lobby';
            if (normalize(window.location.hash) !== path) {
                window.location.replace('#' + path);
                return;
            }
        }
        if (path === currentPath) {
            return;
        }
        var previous = currentPath ? routes[currentPath] : null;
        if (previous && previous.hide) {
            previous.hide();
        }
        currentPath = path;
        var route = routes[path];
        if (route && route.show) {
            var result;
            try {
                result = route.show();
            } catch (error) {
                console.error(error);
            }
            if (result && result.catch) {
                result.catch(function (error) { console.error(error); });
            }
        }
        handlers.forEach(function (fn) {
            fn(path);
        });
    }

    window.QP.router = {
        register: function (path, handler) {
            routes[path] = handler;
        },
        start: function (fallback) {
            var existing = normalize(window.location.hash);
            if (!existing) {
                window.location.replace('#' + (fallback || '/lobby'));
                return;
            }
            dispatch();
        },
        go: function (path) {
            var target = path.charAt(0) === '/' ? path : '/' + path;
            if (normalize(window.location.hash) === target) {
                dispatch();
            } else {
                window.location.hash = '#' + target;
            }
        },
        current: function () {
            return currentPath;
        },
        parse: parse,
        query: function () {
            return parse(window.location.hash).query;
        },
        onChange: function (fn) {
            handlers.push(fn);
        }
    };

    window.addEventListener('hashchange', dispatch);
})();
