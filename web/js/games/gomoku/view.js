/* ------------------------------------------------------------------
   五子棋页面控制器
   规则、胜负、AI 全部由后端决定；这里只负责发请求和渲染状态。
   ------------------------------------------------------------------ */
(function () {
    'use strict';

    window.QP = window.QP || {};
    var QP = window.QP;

    var els = {};
    var boardView = null;
    var clockView = null;
    var state = null;
    var hover = null;
    var pending = null;
    var busy = false;
    var active = false;
    var polling = false;
    var enterToken = 0;

    function el(id) {
        return document.getElementById(id);
    }

    function init() {
        els.game = el('gomoku-view');
        els.canvas = el('gomoku-board');
        els.overlay = el('gomoku-overlay');
        els.overlayText = el('gomoku-overlay-text');
        els.spinner = els.overlay ? els.overlay.querySelector('.spinner') : null;
        els.message = el('gomoku-message');
        els.moves = el('gomoku-moves');
        els.aiMillis = el('gomoku-ai-millis');
        els.size = el('gomoku-size');
        els.difficulty = el('gomoku-difficulty');
        els.newBtn = el('gomoku-new');
        els.undoBtn = el('gomoku-undo');
        els.createBtn = el('gomoku-create');
        els.joinBtn = el('gomoku-join');
        els.leaveBtn = el('gomoku-leave');
        els.roomCode = el('gomoku-room-code');
        els.roomDisplay = el('gomoku-room-display');
        els.colorDisplay = el('gomoku-color-display');
        els.youStone = el('gomoku-you-stone');
        els.otherStone = el('gomoku-other-stone');
        els.youLabel = el('gomoku-you-label');
        els.otherLabel = el('gomoku-other-label');
        els.clockPanel = el('gomoku-clock-panel');
        els.clock1 = el('gomoku-clock-1');
        els.clock2 = el('gomoku-clock-2');
        els.roomActions = el('gomoku-room-actions');
        els.resignBtn = el('gomoku-resign');
        els.drawBtn = el('gomoku-draw');
        els.drawAnswer = el('gomoku-draw-answer');
        els.drawAccept = el('gomoku-draw-accept');
        els.drawDecline = el('gomoku-draw-decline');
        els.rematchBtn = el('gomoku-rematch');

        boardView = QP.gomokuRender.create(els.canvas);
        clockView = QP.ui.createClock({
            firstEl: els.clock1,
            secondEl: els.clock2,
            rows: {
                1: els.clockPanel.querySelector('[data-clock-seat="1"]'),
                2: els.clockPanel.querySelector('[data-clock-seat="2"]')
            },
            onExpire: function () {
                if (active) {
                    setTimeout(function () {
                        if (active) {
                            refreshRoom();
                        }
                    }, 400);
                }
            }
        });

        bindEvents();
        if (window.ResizeObserver) {
            new ResizeObserver(function () {
                if (state) {
                    draw();
                }
            }).observe(els.canvas);
        } else {
            window.addEventListener('resize', draw);
        }
    }

    function bindEvents() {
        els.canvas.addEventListener('mousemove', function (event) {
            var next = canPlay() ? boardView.locate(event.clientX, event.clientY) : null;
            var changed = (!!next !== !!hover)
                || (next && hover && (next.r !== hover.r || next.c !== hover.c));
            if (changed) {
                hover = next;
                draw();
            }
        });
        els.canvas.addEventListener('mouseleave', function () {
            if (hover) {
                hover = null;
                draw();
            }
        });
        els.canvas.addEventListener('click', onBoardClick);

        els.newBtn.addEventListener('click', function () {
            var size = parseInt(els.size.value, 10) || 15;
            run('/api/gomoku/new', { size: size }, '正在开局…').then(function (data) {
                if (data) {
                    pending = null;
                    apply(data);
                }
            });
        });

        els.undoBtn.addEventListener('click', function () {
            var url = state && state.mode === 'pvp' ? '/api/rooms/undo' : '/api/gomoku/undo';
            run(url, {}, '正在悔棋…').then(function (data) {
                if (data) {
                    pending = null;
                    apply(data);
                }
            });
        });

        els.difficulty.addEventListener('change', function () {
            var level = parseInt(els.difficulty.value, 10) || 2;
            run('/api/gomoku/difficulty', { level: level }, '正在切换难度…').then(function (data) {
                if (data) {
                    apply(data);
                }
            });
        });

        document.querySelectorAll('[data-gomoku-mode]').forEach(function (button) {
            button.addEventListener('click', function () {
                var mode = button.dataset.gomokuMode;
                if (!state || mode === state.mode) {
                    return;
                }
                if (state.mode === 'pvp' && state.room
                        && !window.confirm('切换模式会离开当前房间，确定吗？')) {
                    return;
                }
                run('/api/gomoku/mode', { mode: mode }, '正在切换模式…').then(function (data) {
                    if (data) {
                        pending = null;
                        apply(data);
                    }
                });
            });
        });

        els.createBtn.addEventListener('click', createRoom);
        els.joinBtn.addEventListener('click', joinRoom);
        els.leaveBtn.addEventListener('click', function () {
            run('/api/rooms/leave', {}, '正在离开房间…').then(function (data) {
                if (data) {
                    pending = null;
                    apply(data);
                }
            });
        });

        els.roomCode.addEventListener('keydown', function (event) {
            if (event.key === 'Enter') {
                event.preventDefault();
                joinRoom();
            }
        });

        els.resignBtn.addEventListener('click', function () {
            if (!window.confirm('确定认输吗？')) {
                return;
            }
            run('/api/rooms/resign', {}, '正在认输…').then(function (data) {
                if (data) {
                    apply(data);
                }
            });
        });

        els.drawBtn.addEventListener('click', function () {
            run('/api/rooms/draw', { action: 'offer' }, '正在提和…').then(function (data) {
                if (data) {
                    apply(data);
                }
            });
        });
        els.drawAccept.addEventListener('click', function () {
            run('/api/rooms/draw', { action: 'accept' }, '正在结束对局…').then(function (data) {
                if (data) {
                    apply(data);
                }
            });
        });
        els.drawDecline.addEventListener('click', function () {
            run('/api/rooms/draw', { action: 'decline' }, '正在回应…').then(function (data) {
                if (data) {
                    apply(data);
                }
            });
        });
        els.rematchBtn.addEventListener('click', function () {
            run('/api/rooms/rematch', {}, '正在准备下一盘…').then(function (data) {
                if (data) {
                    pending = null;
                    apply(data);
                }
            });
        });
    }

    function activate() {
        active = true;
        var token = ++enterToken;
        setBusy(true, '正在准备棋局…');
        QP.api.post('/api/session/game', { game: 'gomoku' })
            .then(function (data) {
                if (token !== enterToken) {
                    return;
                }
                setBusy(false);
                apply(data);
            })
            .catch(function (error) {
                if (token !== enterToken) {
                    return;
                }
                setBusy(false);
                fail(error);
            });
    }

    function deactivate() {
        active = false;
        enterToken++;
        hover = null;
    }

    function run(url, payload, label) {
        setBusy(true, label || '处理中…');
        return QP.api.post(url, payload || {}).then(function (data) {
            setBusy(false);
            return data;
        }).catch(function (error) {
            setBusy(false);
            fail(error);
            return null;
        });
    }

    function fail(error) {
        var text = (error && error.message) || '操作失败';
        QP.ui.toast(text, 'error');
        if (state) {
            QP.ui.setText(els.message, text);
            QP.ui.setTone(els.message, 'error');
        }
    }

    function apply(data, silent) {
        if (!data || data.game !== 'gomoku') {
            return;
        }
        var previousLast = state ? state.lastMove : null;
        state = data;

        if (!silent) {
            hover = null;
        }
        if (silent && data.lastMove && (!previousLast
                || previousLast.r !== data.lastMove.r || previousLast.c !== data.lastMove.c)) {
            pending = { r: data.lastMove.r, c: data.lastMove.c, start: Date.now() };
        }

        var pvp = state.mode === 'pvp';
        var inRoom = pvp && !!state.room;
        els.game.classList.toggle('mode-pvp', pvp);
        els.game.classList.toggle('mode-solo', !pvp);
        els.game.classList.toggle('has-room', inRoom);

        document.querySelectorAll('[data-gomoku-mode]').forEach(function (button) {
            button.classList.toggle('is-active', button.dataset.gomokuMode === state.mode);
        });

        if (state.size) {
            els.size.value = String(state.size);
        }
        if (state.difficulty) {
            els.difficulty.value = String(state.difficulty);
        }
        QP.ui.setText(els.moves, state.moves || 0);
        QP.ui.setText(els.message, state.message || '');
        QP.ui.setTone(els.message, messageTone());

        if (pvp) {
            QP.ui.setText(els.roomDisplay, state.room || '—');
            QP.ui.setText(els.colorDisplay, colorName(state.mySeat));
        } else {
            QP.ui.setText(els.aiMillis, (state.aiMillis || 0) + ' ms');
        }
        updateLegend(pvp);

        els.clockPanel.hidden = !(pvp && state.clock);
        if (pvp && state.clock) {
            clockView.set(state.clock);
        }

        syncControls();
        syncOverlay();
        draw();
        if (pending) {
            animate();
        }
    }

    function messageTone() {
        if (!state) {
            return 'info';
        }
        if (state.status === 'draw') {
            return 'info';
        }
        var winner = state.status === 'black_win' ? 1 : (state.status === 'white_win' ? 2 : 0);
        if (!winner) {
            return 'info';
        }
        var mine = state.mode === 'pvp' ? state.mySeat : 1;
        return mine === winner ? 'win' : 'lose';
    }

    function colorName(seat) {
        if (seat === 1) {
            return '黑棋';
        }
        if (seat === 2) {
            return '白棋';
        }
        return '—';
    }

    function stoneClass(seat) {
        if (seat === 1) {
            return 'stone stone--black';
        }
        if (seat === 2) {
            return 'stone stone--white';
        }
        return 'stone stone--none';
    }

    function updateLegend(pvp) {
        if (!pvp) {
            els.youStone.className = stoneClass(1);
            els.otherStone.className = stoneClass(2);
            QP.ui.setText(els.youLabel, '你（黑棋）');
            QP.ui.setText(els.otherLabel, '电脑（白棋）');
            return;
        }
        var mine = state.mySeat;
        var other = mine ? 3 - mine : 0;
        els.youStone.className = stoneClass(mine);
        els.otherStone.className = stoneClass(other);
        if (mine === 1 || mine === 2) {
            QP.ui.setText(els.youLabel, '你（' + colorName(mine) + '）');
            QP.ui.setText(els.otherLabel, '对手（' + colorName(other) + '）');
        } else {
            QP.ui.setText(els.youLabel, '你');
            QP.ui.setText(els.otherLabel, '对手');
        }
    }

    function syncControls() {
        if (!state) {
            return;
        }
        var pvp = state.mode === 'pvp';
        var inRoom = pvp && !!state.room;
        var playing = state.status === 'playing';
        var mySeat = state.mySeat;

        var canUndo = (state.moves || 0) > 0 && playing;
        if (pvp) {
            canUndo = canUndo && inRoom && state.lastMove
                && state.board[state.lastMove.r]
                && state.board[state.lastMove.r][state.lastMove.c] === mySeat;
        }

        QP.ui.setDisabled(els.undoBtn, busy || !canUndo);
        QP.ui.setDisabled(els.newBtn, busy);
        QP.ui.setDisabled(els.size, busy || inRoom);
        QP.ui.setDisabled(els.difficulty, busy);
        QP.ui.setDisabled(els.createBtn, busy || inRoom);
        QP.ui.setDisabled(els.joinBtn, busy || inRoom);
        QP.ui.setDisabled(els.leaveBtn, busy || !inRoom);
        QP.ui.setDisabled(els.roomCode, busy || inRoom);

        els.roomActions.hidden = !inRoom;
        QP.ui.setDisabled(els.resignBtn, !playing);
        QP.ui.setDisabled(els.drawBtn, !playing || state.drawOffer === mySeat);
        els.drawAnswer.hidden = !(playing && state.drawOffer && state.drawOffer !== mySeat);
        els.rematchBtn.hidden = !inRoom || state.phase !== 'finished';
        els.rematchBtn.textContent = (state.rematchVotes && state.rematchVotes[mySeat - 1])
            ? '等待对手…'
            : '再来一盘';
    }

    function syncOverlay() {
        if (!els.overlay) {
            return;
        }
        var show = false;
        var text = '';
        if (busy) {
            show = true;
            text = els.overlayText.textContent;
            if (els.spinner) {
                els.spinner.hidden = false;
            }
        } else if (state && state.mode === 'pvp' && state.phase === 'waiting') {
            show = true;
            text = state.room ? ('等待对手加入… 房间码 ' + state.room) : '等待对手加入…';
            if (els.spinner) {
                els.spinner.hidden = false;
            }
        } else if (state && state.mode === 'pvp' && state.phase === 'lobby') {
            show = true;
            text = '创建或加入一个房间后开始联机对局';
            if (els.spinner) {
                els.spinner.hidden = true;
            }
        }
        els.overlay.hidden = !show;
        if (show) {
            QP.ui.setText(els.overlayText, text);
        }
    }

    function setBusy(value, text) {
        busy = value;
        if (value && text) {
            QP.ui.setText(els.overlayText, text);
        }
        syncControls();
        syncOverlay();
        draw();
    }

    function canPlay() {
        if (!state || busy || state.status !== 'playing') {
            return false;
        }
        if (state.mode === 'pvp') {
            return state.phase === 'playing'
                && state.mySeat !== 0
                && state.turn === state.mySeat;
        }
        return true;
    }

    function draw() {
        if (!state || !state.board) {
            return;
        }
        boardView.draw(state, {
            hover: hover,
            canPlay: canPlay(),
            previewColor: state.mode === 'pvp' ? state.mySeat : state.playerColor,
            pending: pending
        });
    }

    function onBoardClick(event) {
        if (!state || busy) {
            return;
        }
        var hit = boardView.locate(event.clientX, event.clientY);
        if (!hit) {
            return;
        }
        if (state.mode === 'pvp' && state.phase === 'lobby') {
            QP.ui.toast('先创建或加入一个房间');
            return;
        }
        if (state.mode === 'pvp' && state.phase === 'waiting') {
            QP.ui.toast('等待对手加入后再落子');
            return;
        }
        if (state.status !== 'playing') {
            QP.ui.toast('本局已经结束，点「再来一盘」继续');
            return;
        }
        if (state.mode === 'pvp' && state.turn !== state.mySeat) {
            QP.ui.toast('还没轮到你落子');
            return;
        }
        if (state.board[hit.r][hit.c] !== 0) {
            QP.ui.toast('这个位置已经有棋子了');
            return;
        }

        var url = state.mode === 'pvp' ? '/api/rooms/move' : '/api/gomoku/move';
        setBusy(true, state.mode === 'pvp' ? '正在落子…' : '电脑思考中…');
        QP.api.post(url, { r: hit.r, c: hit.c }).then(function (data) {
            setBusy(false);
            pending = { r: hit.r, c: hit.c, start: Date.now() };
            apply(data);
        }).catch(function (error) {
            setBusy(false);
            fail(error);
        });
    }

    function createRoom() {
        var size = parseInt(els.size.value, 10) || 15;
        QP.ui.roomModal.open('gomoku').then(function (choice) {
            if (!choice) {
                return;
            }
            run('/api/rooms/create', {
                game: 'gomoku',
                size: size,
                seat: choice.seat,
                minutes: choice.minutes,
                increment: choice.increment
            }, '正在创建房间…').then(function (data) {
                if (data) {
                    pending = null;
                    apply(data);
                }
            });
        });
    }

    function joinRoom() {
        var code = (els.roomCode.value || '').trim().toUpperCase();
        if (!code) {
            QP.ui.toast('请输入 6 位房间码', 'error');
            els.roomCode.focus();
            return;
        }
        run('/api/rooms/join', { room: code }, '正在加入房间…').then(function (data) {
            if (data) {
                els.roomCode.value = '';
                pending = null;
                apply(data);
            }
        });
    }

    function refreshRoom() {
        if (!active || busy) {
            return;
        }
        QP.api.get('/api/rooms/state').then(function (data) {
            if (active && data && data.game === 'gomoku') {
                apply(data, true);
            }
        }).catch(function () {
            // 轮询失败不打断操作
        });
    }

    function poll() {
        if (!active || busy || polling || !state) {
            return;
        }
        if (state.mode !== 'pvp' || !state.room) {
            return;
        }
        polling = true;
        QP.api.get('/api/rooms/state').then(function (data) {
            if (!active || !data || data.game !== 'gomoku') {
                return;
            }
            apply(data, true);
        }).catch(function () {
            // 下一次自动重试
        }).then(function () {
            polling = false;
        });
    }

    function animate() {
        if (!pending) {
            return;
        }
        draw();
        if (Date.now() - pending.start < 200) {
            window.requestAnimationFrame(animate);
        } else {
            pending = null;
            draw();
        }
    }

    function tick() {
        if (active && state) {
            clockView.tick();
        }
    }

    window.QP.gomokuView = {
        init: init,
        activate: activate,
        deactivate: deactivate,
        poll: poll,
        tick: tick
    };
})();
