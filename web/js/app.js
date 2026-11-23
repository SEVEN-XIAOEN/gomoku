/* ------------------------------------------------------------------
   应用入口：路由、全局计时器、启动时的页面恢复。
   启动后先进入大厅；用户选择棋种后才进入对应主界面。
   ------------------------------------------------------------------ */
(function () {
    'use strict';

    window.QP = window.QP || {};
    var QP = window.QP;

    var lobbyView = null;
    var gameView = null;
    var gomokuEl = null;
    var xiangqiEl = null;
    var homeBtn = null;
    var activeGame = null;
    var cameFromGame = false;
    var lobbyAlreadySynced = false;

    function syncLobby() {
        // 离开棋局时通知服务端离开房间，并清空“当前棋种”。
        return QP.api.post('/api/session/game', { game: 'lobby' })
            .catch(function () {
                // 网络失败不阻止用户返回大厅，下次进入棋种时还会重新同步。
            });
    }

    function showLobby() {
        lobbyView.hidden = false;
        gameView.hidden = true;
        homeBtn.hidden = true;
        activeGame = null;
        document.title = '棋类对战平台 · 五子棋 / 中国象棋';

        if (cameFromGame) {
            cameFromGame = false;
            if (lobbyAlreadySynced) {
                lobbyAlreadySynced = false;
            } else {
                syncLobby();
            }
        }
    }

    function hideLobby() {
        lobbyView.hidden = true;
    }

    function showGame(game) {
        lobbyView.hidden = true;
        gameView.hidden = false;
        homeBtn.hidden = false;
        gomokuEl.hidden = game !== 'gomoku';
        xiangqiEl.hidden = game !== 'xiangqi';

        var view = game === 'gomoku' ? QP.gomokuView : QP.xiangqiView;
        activeGame = view;
        document.title = (game === 'gomoku' ? '五子棋' : '中国象棋') + ' · 棋类对战平台';
        view.activate();
    }

    function hideGame() {
        QP.gomokuView.deactivate();
        QP.xiangqiView.deactivate();
        gameView.hidden = true;
        activeGame = null;
        cameFromGame = true;
    }

    function goLobby() {
        if (!activeGame) {
            QP.router.go('/lobby');
            return;
        }
        homeBtn.disabled = true;
        lobbyAlreadySynced = true;
        syncLobby().then(function () {
            homeBtn.disabled = false;
            QP.router.go('/lobby');
        });
    }

    function boot() {
        lobbyView = document.getElementById('lobby-view');
        gameView = document.getElementById('game-view');
        gomokuEl = document.getElementById('gomoku-view');
        xiangqiEl = document.getElementById('xiangqi-view');
        homeBtn = document.getElementById('home-btn');

        QP.lobby.init();
        QP.gomokuView.init();
        QP.xiangqiView.init();

        homeBtn.addEventListener('click', goLobby);

        QP.router.register('/lobby', {
            show: showLobby,
            hide: hideLobby
        });
        QP.router.register('/gomoku', {
            show: function () { showGame('gomoku'); },
            hide: hideGame
        });
        QP.router.register('/xiangqi', {
            show: function () { showGame('xiangqi'); },
            hide: hideGame
        });

        setInterval(function () {
            if (document.hidden || !activeGame) {
                return;
            }
            activeGame.tick();
        }, 100);

        setInterval(function () {
            if (document.hidden || !activeGame) {
                return;
            }
            activeGame.poll();
        }, 1200);

        document.addEventListener('visibilitychange', function () {
            if (!document.hidden && activeGame) {
                activeGame.tick();
                activeGame.poll();
            }
        });

        QP.api.get('/api/session/state').then(function (state) {
            var hash = (window.location.hash || '').replace(/^#/, '');
            // 只有显式 hash 才直达棋种；否则始终先回到大厅选择游戏。
            var target = (hash === '/gomoku' || hash === '/xiangqi' || hash === '/lobby')
                ? hash
                : '/lobby';
            if (target === '/lobby' && state && state.game && state.game !== 'lobby') {
                // 重新打开页面时先让服务端离开上次的棋局/房间，避免后台残留旧对局。
                syncLobby().then(function () {
                    QP.router.start(target);
                });
                return;
            }
            QP.router.start(target);
        }).catch(function () {
            QP.router.start('/lobby');
        });
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', boot);
    } else {
        boot();
    }
})();
