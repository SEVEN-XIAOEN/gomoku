/* ------------------------------------------------------------------
   中国象棋页面控制器
   两段式走子：先点己方棋子取合法落点，再点落点走棋。
   前端不判断任何规则，合法落点、将军、胜负全部来自服务端。
   ------------------------------------------------------------------ */
(function () {
    'use strict';

    window.QP = window.QP || {};
    var QP = window.QP;

    var els = {};
    var boardView = null;
    var clockView = null;
    var state = null;
    var selected = null;
    var legal = [];
    var hover = null;
    var legalToken = 0;
    var busy = false;
    var active = false;
    var polling = false;
    var enterToken = 0;
    var timeoutAttempts = 0;

    function el(id) {
        return document.getElementById(id);
    }

    function init() {
        els.game = el('xiangqi-view');
        els.canvas = el('xiangqi-board');
        els.overlay = el('xiangqi-overlay');
        els.overlayText = el('xiangqi-overlay-text');
        els.spinner = els.overlay ? els.overlay.querySelector('.spinner') : null;
        els.message = el('xiangqi-message');
        els.moves = el('xiangqi-moves');
        els.aiMillis = el('xiangqi-ai-millis');
        els.side = el('xiangqi-side');
        els.time = el('xiangqi-time');
        els.difficulty = el('xiangqi-difficulty');
        els.newBtn = el('xiangqi-new');
        els.undoBtn = el('xiangqi-undo');
        els.resignSoloBtn = el('xiangqi-resign-solo');
        els.createBtn = el('xiangqi-create');
        els.joinBtn = el('xiangqi-join');
        els.leaveBtn = el('xiangqi-leave');
        els.roomCode = el('xiangqi-room-code');
        els.roomDisplay = el('xiangqi-room-display');
        els.colorDisplay = el('xiangqi-color-display');
        els.youLabel = el('xiangqi-you-label');
        els.otherLabel = el('xiangqi-other-label');
        els.clockPanel = el('xiangqi-clock-panel');
        els.clock1 = el('xiangqi-clock-1');
        els.clock2 = el('xiangqi-clock-2');
        els.roomActions = el('xiangqi-room-actions');
        els.resignBtn = el('xiangqi-resign');
        els.drawBtn = el('xiangqi-draw');
        els.drawAnswer = el('xiangqi-draw-answer');
        els.drawAccept = el('xiangqi-draw-accept');
        els.drawDecline = el('xiangqi-draw-decline');
        els.rematchBtn = el('xiangqi-rematch');

        boardView = QP.xiangqiRender.create(els.canvas);
        clockView = QP.ui.createClock({
            firstEl: els.clock1,
            secondEl: els.clock2,
            rows: {
                1: els.clockPanel.querySelector('[data-clock-seat="1"]'),
                2: els.clockPanel.querySelector('[data-clock-seat="2"]')
            },
            onExpire: onClockExpire
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
            var time = parseTime(els.time.value);
            run('/api/xiangqi/new', {
                side: parseInt(els.side.value, 10) || 0,
                level: parseInt(els.difficulty.value, 10) || 2,
                minutes: time.minutes,
                increment: time.increment
            }, '正在开局…').then(function (data) {
                if (data) {
                    clearSelection();
                    apply(data);
                }
            });
        });

        els.undoBtn.addEventListener('click', function () {
            var url = state && state.mode === 'pvp' ? '/api/rooms/undo' : '/api/xiangqi/undo';
            run(url, {}, '正在悔棋…').then(function (data) {
                if (data) {
                    clearSelection();
                    apply(data);
                }
            });
        });

        els.resignSoloBtn.addEventListener('click', function () {
            if (!window.confirm('确定认输吗？')) {
                return;
            }
            run('/api/xiangqi/resign', {}, '正在认输…').then(function (data) {
                if (data) {
                    clearSelection();
                    apply(data);
                }
            });
        });

        els.difficulty.addEventListener('change', function () {
            var level = parseInt(els.difficulty.value, 10) || 2;
            run('/api/xiangqi/difficulty', { level: level }, '正在切换难度…').then(function (data) {
                if (data) {
                    apply(data);
                }
            });
        });

        document.querySelectorAll('[data-xiangqi-mode]').forEach(function (button) {
            button.addEventListener('click', function () {
                var mode = button.dataset.xiangqiMode;
                if (!state || mode === state.mode) {
                    return;
                }
                if (state.mode === 'pvp' && state.room
                        && !window.confirm('切换模式会离开当前房间，确定吗？')) {
                    return;
                }
                run('/api/xiangqi/mode', { mode: mode }, '正在切换模式…').then(function (data) {
                    if (data) {
                        clearSelection();
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
                    clearSelection();
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
                    clearSelection();
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
                    clearSelection();
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
                    clearSelection();
                    apply(data);
                }
            });
        });
    }

    function activate() {
        active = true;
        var token = ++enterToken;
        setBusy(true, '正在准备棋局…');
        QP.api.post('/api/session/game', { game: 'xiangqi' })
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
        clearSelection();
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

    function signature(value) {
        if (!value) {
            return '';
        }
        return (value.moves || 0) + '|' + (value.turn || 0) + '|' + value.status
            + '|' + (value.board ? JSON.stringify(value.board) : '');
    }

    function apply(data, silent) {
        if (!data || data.game !== 'xiangqi') {
            return;
        }
        var previousSignature = signature(state);
        state = data;
        if (!silent || signature(data) !== previousSignature) {
            clearSelection();
        }

        var pvp = state.mode === 'pvp';
        var inRoom = pvp && !!state.room;
        els.game.classList.toggle('mode-pvp', pvp);
        els.game.classList.toggle('mode-solo', !pvp);
        els.game.classList.toggle('has-room', inRoom);

        document.querySelectorAll('[data-xiangqi-mode]').forEach(function (button) {
            button.classList.toggle('is-active', button.dataset.xiangqiMode === state.mode);
        });

        if (state.difficulty) {
            els.difficulty.value = String(state.difficulty);
        }
        QP.ui.setText(els.moves, state.moves || 0);
        QP.ui.setText(els.message, state.message || '');
        QP.ui.setTone(els.message, messageTone());

        if (pvp) {
            QP.ui.setText(els.roomDisplay, state.room || '—');
            QP.ui.setText(els.colorDisplay, sideName(state.mySeat));
        } else {
            QP.ui.setText(els.aiMillis, (state.aiMillis || 0) + ' ms');
        }
        updateLegend(pvp);

        els.clockPanel.hidden = false;
        clockView.set(state.clock);

        syncControls();
        syncOverlay();
        draw();
    }

    function messageTone() {
        if (!state) {
            return 'info';
        }
        if (state.status === 'draw') {
            return 'info';
        }
        var winner = state.status === 'red_win' ? 1 : (state.status === 'black_win' ? 2 : 0);
        if (!winner) {
            return 'info';
        }
        var mine = state.mode === 'pvp' ? state.mySeat : state.playerColor;
        return mine === winner ? 'win' : 'lose';
    }

    function sideName(seat) {
        if (seat === 1) {
            return '红方';
        }
        if (seat === 2) {
            return '黑方';
        }
        return '—';
    }

    function updateLegend(pvp) {
        if (!pvp) {
            var mine = state.playerColor;
            if (mine === 1 || mine === 2) {
                QP.ui.setText(els.youLabel, '你（' + sideName(mine) + '）');
                QP.ui.setText(els.otherLabel, '电脑（' + sideName(3 - mine) + '）');
            } else {
                QP.ui.setText(els.youLabel, '你');
                QP.ui.setText(els.otherLabel, '电脑');
            }
            return;
        }
        var seat = state.mySeat;
        if (seat === 1 || seat === 2) {
            QP.ui.setText(els.youLabel, '你（' + sideName(seat) + '）');
            QP.ui.setText(els.otherLabel, '对手（' + sideName(3 - seat) + '）');
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
        var started = state.phase !== 'ready';
        var mySeat = state.mySeat;

        var canUndo = (state.moves || 0) > 0 && playing;
        if (pvp) {
            canUndo = canUndo && inRoom && state.lastMove
                && state.lastMove.color === mySeat;
        }

        QP.ui.setDisabled(els.undoBtn, busy || !canUndo);
        QP.ui.setDisabled(els.newBtn, busy);
        QP.ui.setDisabled(els.resignSoloBtn, busy || !playing || !started);
        QP.ui.setDisabled(els.side, busy);
        QP.ui.setDisabled(els.time, busy);
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
        var spin = true;
        if (busy) {
            show = true;
            text = els.overlayText.textContent;
        } else if (state && state.mode === 'pvp' && state.phase === 'waiting') {
            show = true;
            text = state.room ? ('等待对手加入… 房间码 ' + state.room) : '等待对手加入…';
        } else if (state && state.mode === 'pvp' && state.phase === 'lobby') {
            show = true;
            text = '创建或加入一个房间后开始联机对局';
            spin = false;
        } else if (state && state.mode === 'solo' && state.phase === 'ready') {
            show = true;
            text = '选择执红 / 执黑，然后点「新对局」开始';
            spin = false;
        }
        els.overlay.hidden = !show;
        if (show) {
            QP.ui.setText(els.overlayText, text);
        }
        if (els.spinner) {
            els.spinner.hidden = !spin;
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

    function myColor() {
        if (!state) {
            return 0;
        }
        return state.mode === 'pvp' ? state.mySeat : state.playerColor;
    }

    function canPlay() {
        if (!state || busy || state.status !== 'playing') {
            return false;
        }
        var mine = myColor();
        if (!mine) {
            return false;
        }
        if (state.mode === 'pvp' && state.phase !== 'playing') {
            return false;
        }
        return state.turn === mine;
    }

    function draw() {
        if (!state || !state.board) {
            return;
        }
        boardView.draw(state, {
            selected: selected,
            legal: legal,
            hover: hover
        });
    }

    function clearSelection() {
        selected = null;
        legal = [];
        legalToken++;
    }

    function isLegalTarget(hit) {
        return legal.some(function (move) {
            return move.r === hit.r && move.c === hit.c;
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
            QP.ui.toast('等待对手加入后再走棋');
            return;
        }
        if (state.mode === 'solo' && state.phase === 'ready') {
            QP.ui.toast('先选择执红 / 执黑，再点「新对局」');
            return;
        }
        if (state.status !== 'playing') {
            QP.ui.toast('本局已经结束，点「再来一盘」或「新对局」');
            return;
        }
        if (state.turn !== myColor()) {
            QP.ui.toast('还没轮到你走棋');
            return;
        }

        if (selected && isLegalTarget(hit)) {
            sendMove(selected, hit);
            return;
        }

        var piece = state.board[hit.r][hit.c];
        if (piece && Math.floor(piece / 10) === myColor()) {
            selectPiece(hit);
            return;
        }
        clearSelection();
        draw();
    }

    function selectPiece(hit) {
        selected = hit;
        legal = [];
        draw();

        var token = ++legalToken;
        var url = state.mode === 'pvp' ? '/api/rooms/legal' : '/api/xiangqi/legal';
        QP.api.post(url, { r: hit.r, c: hit.c }).then(function (data) {
            if (token !== legalToken || !active) {
                return;
            }
            legal = (data.moves || []).map(function (move) {
                return { r: move[0], c: move[1] };
            });
            draw();
        }).catch(function (error) {
            if (token !== legalToken) {
                return;
            }
            clearSelection();
            draw();
            QP.ui.toast(error.message || '无法获取合法落点', 'error');
        });
    }

    function sendMove(from, to) {
        var url = state.mode === 'pvp' ? '/api/rooms/move' : '/api/xiangqi/move';
        var label = state.mode === 'pvp' ? '正在走棋…' : '电脑思考中…';
        setBusy(true, label);
        QP.api.post(url, {
            fr: from.r,
            fc: from.c,
            tr: to.r,
            tc: to.c
        }).then(function (data) {
            setBusy(false);
            clearSelection();
            apply(data);
        }).catch(function (error) {
            setBusy(false);
            clearSelection();
            draw();
            fail(error);
        });
    }

    function createRoom() {
        QP.ui.roomModal.open('xiangqi').then(function (choice) {
            if (!choice) {
                return;
            }
            run('/api/rooms/create', {
                game: 'xiangqi',
                seat: choice.seat,
                minutes: choice.minutes,
                increment: choice.increment
            }, '正在创建房间…').then(function (data) {
                if (data) {
                    clearSelection();
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
                clearSelection();
                apply(data);
            }
        });
    }

    function parseTime(value) {
        var parts = (value || '5:3').split(':');
        return {
            minutes: parseInt(parts[0], 10) || 0,
            increment: parseInt(parts[1], 10) || 0
        };
    }

    function onClockExpire() {
        if (!active) {
            return;
        }
        timeoutAttempts = 0;
        retryTimeout();
    }

    function retryTimeout() {
        if (!active || !state || timeoutAttempts >= 8) {
            return;
        }
        timeoutAttempts++;
        var url = state.mode === 'pvp' ? '/api/rooms/state' : '/api/xiangqi/state';
        QP.api.get(url).then(function (data) {
            if (!active || !data || data.game !== 'xiangqi') {
                return;
            }
            apply(data, true);
            var clock = data.clock;
            if (data.status === 'playing' && clock
                    && (clock.firstMs <= 0 || clock.secondMs <= 0)) {
                setTimeout(retryTimeout, 500);
            }
        }).catch(function () {
            setTimeout(retryTimeout, 600);
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
            if (!active || !data || data.game !== 'xiangqi') {
                return;
            }
            apply(data, true);
        }).catch(function () {
            // 下一次自动重试
        }).then(function () {
            polling = false;
        });
    }

    function tick() {
        if (active && state) {
            clockView.tick();
        }
    }

    window.QP.xiangqiView = {
        init: init,
        activate: activate,
        deactivate: deactivate,
        poll: poll,
        tick: tick
    };
})();
