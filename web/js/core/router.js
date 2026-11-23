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

    function normalize(hash) {
        var path = (hash || '').replace(/^#/, '');
        if (!path) {
            return '';
        }
        if (path.charAt(0) !== '/') {
            path = '/' + path;
        }
        return path.replace(/\/+$/, '') || '/';
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
        onChange: function (fn) {
            handlers.push(fn);
        }
    };

    window.addEventListener('hashchange', dispatch);
})();
