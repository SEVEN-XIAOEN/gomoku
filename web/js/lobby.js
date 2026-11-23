/* ------------------------------------------------------------------
   大厅：点游戏卡片进入对应棋种。
   ------------------------------------------------------------------ */
(function () {
    'use strict';

    window.QP = window.QP || {};

    window.QP.lobby = {
        init: function () {
            document.querySelectorAll('[data-enter-game]').forEach(function (button) {
                button.addEventListener('click', function () {
                    var game = button.dataset.enterGame;
                    if (game === 'gomoku' || game === 'xiangqi') {
                        window.QP.router.go('/' + game);
                    }
                });
            });
        }
    };
})();
